package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class SyncRunService {
    private final SyncTaskService taskService;
    private final SyncTaskMapper tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    private final SyncConnectionService connections;
    private final MysqlSyncReader reader;
    private final SpringJdbcStreamingReader streamingReader;
    private final SyncFieldMapper fieldMapper;
    private final SyncDefinitionValidator validator;
    private final SyncEvidenceService evidenceService;
    private final RedissonClient redisson;
    private final PlatformTransactionManager transactionManager;
    private final org.springframework.beans.factory.ObjectProvider<SyncBatchLauncher> launcher;
    public record Start(Long taskId,Integer expectedVersion,String requestKey,boolean preview,
                        boolean full,boolean adoptExisting,Long retryOf,boolean confirmPreparation) {}

    public Long start(Start command) {
        if(command.requestKey()==null||!command.requestKey().matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("请求标识无效");
        long tenant=SyncTaskService.tenant();
        SyncRunDO run=tx().execute(status->{
            var t=taskService.locked(command.taskId());
            var d=taskService.definition(t);
            var previous=runs.selectRequest(new SyncQueries.Request(tenant,t.getId(),command.requestKey()));
            if(previous!=null)return previous;
            if(!Objects.equals(command.expectedVersion(),t.getVersion()))throw new IllegalArgumentException("配置版本冲突");
            if(t.getActiveRunId()!=null)throw new IllegalArgumentException("任务已有运行批次");
            if(command.retryOf()!=null) {
                var prior=required(command.retryOf());
                if(!prior.getTaskId().equals(t.getId())||!"FAILED".equals(prior.getStatus()))
                    throw new IllegalArgumentException("仅可重试当前任务的失败批次");
                if(prior.getPageNumber()!=null)throw new IllegalArgumentException("请从主运行创建关联重试");
                if("STREAMING_CURSOR".equals(d.effectiveReadStrategy())&&"NO_RESTART".equals(d.effectiveRestartPolicy()))
                    throw new IllegalArgumentException("当前流式任务配置为失败后不可断点重试");
                if(prior.getPagingJson()!=null && (!Objects.equals(prior.getConfigVersion(),t.getVersion())
                        || !Objects.equals(prior.getPreview(),command.preview())))
                    throw new IllegalArgumentException("断点重试须保持配置版本和预览方式不变；修改配置后请创建新运行");
            }
            if(command.adoptExisting()&&!command.preview()&&!Objects.equals(t.getValidatedVersion(),t.getVersion()))
                throw new IllegalArgumentException("接管前必须完成完整预检");
            if("STREAMING_CURSOR".equals(d.effectiveReadStrategy())&&command.adoptExisting())
                throw new IllegalArgumentException("流式分块同步不支持接管已有目标");
            if("KEYSET_PAGING".equals(d.effectiveReadStrategy())&&command.adoptExisting())
                throw new IllegalArgumentException("自动分页不支持接管已有目标");
            if((d.clearBeforeLoad()||d.resetMappingsBeforeLoad())&&!command.preview()) {
                if(!command.confirmPreparation()||!Objects.equals(t.getValidatedVersion(),t.getVersion()))
                    throw new IllegalArgumentException("加载前截断或重置映射必须先完成当前版本的完整预览，再明确确认执行");
                if(command.adoptExisting())throw new IllegalArgumentException("清空重建不能与接管已有组织同时执行");
            }
            boolean full=command.full()||command.preview()||t.getCheckpoint()==null||!"INCREMENTAL".equals(d.mode());
            var r=new SyncRunDO().setTaskId(t.getId()).setRequestKey(command.requestKey()).setParentRunId(command.retryOf())
                    .setStatus("QUEUED").setPreview(command.preview()).setFullSnapshot(full).setAdoptExisting(command.adoptExisting())
                    .setConfigSnapshot(t.getDefinition()).setConfigVersion(t.getVersion()).setStartedAt(LocalDateTime.now())
                    .setCachePending(false).setReadCount(0);
            r.setTenantId(tenant);
            if(command.retryOf()!=null) {
                var prior=required(command.retryOf());
                boolean keepCheckpoint="KEYSET_PAGING".equals(d.effectiveReadStrategy())
                        ||("STREAMING_CURSOR".equals(d.effectiveReadStrategy())&&"CHECKPOINT_KEY".equals(d.effectiveRestartPolicy()));
                if(keepCheckpoint)r.setPagingJson(prior.getPagingJson());
            }
            Long actor=cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
            r.setCreator(actor==null?"scheduled_sync":actor.toString());r.setUpdater(r.getCreator());
            runs.insert(r);
            t.setActiveRunId(r.getId());tasks.updateById(t);return r;
        });
        if("QUEUED".equals(run.getStatus())) launcher.getObject().launch(tenant,run.getId());
        return run.getId();
    }
    public SyncRunDO required(Long id) {
        // Spring Batch's NOT_SUPPORTED tasklet still enables synchronization. A mapper read
        // outside an actual transaction would retain a first-level cache across chunk commits.
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            return tx().execute(status->required(id));
        var r=runs.selectScoped(new SyncQueries.Id(SyncTaskService.tenant(),id));
        if(r==null)throw new IllegalArgumentException("运行记录不存在");return r;
    }
    public void execute(Long runId) throws Exception {
        var run=required(runId);
        var definition=JsonUtils.parseObject(run.getConfigSnapshot(),SyncDefinition.class);
        if("STREAMING_CURSOR".equals(definition.effectiveReadStrategy())&&run.getPageNumber()==null)
            executeStreaming(runId,definition);
        else if("KEYSET_PAGING".equals(definition.effectiveReadStrategy())&&run.getPageNumber()==null)
            executePaged(runId,definition);
        else executeSingle(runId);
    }

    private void executeSingle(Long runId) throws Exception {
        var r=required(runId);
        Long ownerRunId=r.getPageNumber()==null?runId:r.getParentRunId();
        if("SUCCESS".equals(r.getStatus())||"PREVIEW_READY".equals(r.getStatus()))return;
        if(!Objects.equals(taskService.required(r.getTaskId()).getActiveRunId(),ownerRunId))return;
        var d=JsonUtils.parseObject(r.getConfigSnapshot(),SyncDefinition.class);
        var lock=redisson.getLock("int:data-sync:"+SyncTaskService.tenant()+":"+d.adapter());
        boolean acquired=false;
        List<SyncEvidenceService.Evidence> evidence=List.of();
        try {
            acquired=lock.tryLock(1,TimeUnit.SECONDS);
            if(!acquired)throw new IllegalStateException("目标范围正在由另一任务同步");
            updateStatus(runId,"READING");
            var task=taskService.required(r.getTaskId());
            var resolved=connections.resolve(d);
            var snapshot=r.getPageNumber()==null?reader.read(resolved,task.getCheckpoint(),r.getFullSnapshot()):
                    reader.readPage(resolved,JsonUtils.parseObject(r.getPagingJson(),SyncPagingState.class));
            tx().executeWithoutResult(status->{
                var current=required(runId);current.setSourceUpper(snapshot.upper());
                current.setReadCount(snapshot.objects().stream().mapToInt(o->o.rows().size()).sum());
                runs.updateById(current);
            });
            evidence=evidenceService.stage(runId,d,snapshot);
            var es=evidence;
            tx().executeWithoutResult(status->{
                var current=required(runId);current.setSourceUpper(snapshot.upper()).setEvidenceJson(JsonUtils.toJsonString(es));
                current.setReadCount(snapshot.objects().stream().mapToInt(o->o.rows().size()).sum());
                current.setStatus("VALIDATING");runs.updateById(current);
            });
            if((d.clearBeforeLoad()||d.resetMappingsBeforeLoad())&&snapshot.objects().stream().allMatch(o->o.rows().isEmpty()))
                throw new IllegalArgumentException("来源为空，拒绝清空目标组织或重置映射");
            var adapter=validator.adapter(d.adapter());
            List<DataSyncAdapter.Binding> existing=(d.clearBeforeLoad()||d.resetMappingsBeforeLoad())?List.of():
                    adapter.requiresAllBindings()?loadBindings(task.getId()):loadPageBindings(task.getId(),d,snapshot);
            var rows=fieldMapper.transform(d,snapshot,existing);
            Set<String> stale=new HashSet<>();
            rows=protectNewer(rows,existing,stale);
            var batch=new DataSyncAdapter.Batch(owner(task),rows,existing,r.getFullSnapshot(),d.missingPolicy(),r.getAdoptExisting(),d.loadingMode(),d.clearBeforeLoad());
            if(r.getPreview()) {
                var preview=adapter.preview(batch);
                if(preview.stream().anyMatch(c->"CONFLICT".equals(c.action()))) {
                    saveResult(runId,preview);throw new IllegalArgumentException("存在目标归属或字段冲突，未写入任何业务记录");
                }
                var finalEvidence=evidence;
                tx().executeWithoutResult(status->{
                    evidenceService.complete(runId,d,finalEvidence,adapter.descriptor(),List.of(),false);
                    var current=required(runId);current.setStatus("PREVIEW_READY").setResultJson(JsonUtils.toJsonString(preview))
                            .setSummaryJson(summary(preview)).setFinishedAt(LocalDateTime.now());runs.updateById(current);
                    if(r.getPageNumber()!=null) advancePage(r,d,snapshot,preview);
                    else { var t=taskService.locked(task.getId());t.setValidatedVersion(t.getVersion()).setActiveRunId(null);tasks.updateById(t); }
                });
                return;
            }
            // Formal execution does not run preview a second time. apply() executes inside this transaction;
            // any returned conflict is converted to an exception so all target writes roll back atomically.
            updateStatus(runId,"APPLYING");
            var finalEvidence=evidence;
            tx().executeWithoutResult(status->{
                if(d.clearBeforeLoad()) {
                    var scope=new SyncQueries.AdapterScope(SyncTaskService.tenant(),d.adapter(),task.getId());
                    var affected=tasks.selectAdapterForUpdate(scope);
                    if(affected.stream().anyMatch(other->!other.getId().equals(task.getId())&&other.getActiveRunId()!=null))
                        throw new IllegalArgumentException("同目标适配器还有未结束批次，请等待其结束后再清空");
                    bindings.deleteAdapterBindings(scope);
                    for(var other:affected) if(!other.getId().equals(task.getId())) {
                        other.setEnabled(false).setCheckpoint(null).setValidatedVersion(null).setVersion(other.getVersion()+1);
                        tasks.updateById(other);
                    }
                }
                var t=taskService.locked(task.getId());
                if(!Objects.equals(t.getActiveRunId(),ownerRunId)||!Objects.equals(t.getVersion(),r.getConfigVersion()))
                    throw new IllegalStateException("运行所有权或配置版本变化");
                if(d.resetMappingsBeforeLoad())bindings.deleteTaskBindings(new SyncQueries.Task(SyncTaskService.tenant(),t.getId()));
                var changes=adapter.apply(batch);
                if(changes.stream().anyMatch(c->"CONFLICT".equals(c.action())))throw new IllegalArgumentException("写入时发生业务冲突");
                verifyPrimaryKeys(batch,changes);
                List<DataSyncAdapter.Change> finalChanges=markStale(changes,stale);
                persistBindings(t,d,finalChanges,runId);
                evidenceService.complete(runId,d,finalEvidence,adapter.descriptor(),finalChanges.stream().filter(c->!"CLEARED".equals(c.action())).toList(),false);
                var current=required(runId);current.setStatus("SUCCESS").setResultJson(JsonUtils.toJsonString(finalChanges))
                        .setSummaryJson(summary(finalChanges)).setFinishedAt(LocalDateTime.now()).setCachePending(true);runs.updateById(current);
                if(r.getPageNumber()!=null) { advancePage(r,d,snapshot,finalChanges);return; }
                t.setCheckpoint(snapshot.upper()).setActiveRunId(null).setRetryAttempt(0).setLastFailedRunId(null)
                        .setNextRunAt(SyncTaskService.next(d.cron()));
                if(r.getFullSnapshot())t.setNextFullAt(SyncTaskService.next(d.fullCron()));
                if("ONCE".equals(d.mode()))t.setEnabled(false);
                tasks.updateById(t);
            });
            refreshCache(runId);
        } catch(Exception exception) {
            var current=required(runId);
            if(!"SUCCESS".equals(current.getStatus())&&!"PREVIEW_READY".equals(current.getStatus())) {
                var captured=evidence;
                tx().executeWithoutResult(status->{
                    if(!captured.isEmpty())evidenceService.complete(runId,d,captured,validator.adapter(d.adapter()).descriptor(),List.of(),true);
                    var failure=required(runId);
                    failure.setStatus("FAILED").setFinishedAt(LocalDateTime.now())
                            .setErrorMessage(safeError(exception)).setSummaryJson(JsonUtils.toJsonString(Map.of("FAILED",failure.getReadCount())));runs.updateById(failure);
                    failTask(failure,d);
                });
            }
            throw exception;
        } finally {if(acquired&&lock.isHeldByCurrentThread())lock.unlock();}
    }

    private void executeStreaming(Long runId,SyncDefinition d)throws Exception {
        var root=required(runId);
        if(Set.of("SUCCESS","PREVIEW_READY").contains(root.getStatus())
                ||!Objects.equals(taskService.required(root.getTaskId()).getActiveRunId(),runId))return;
        var lock=redisson.getLock("int:data-sync:"+SyncTaskService.tenant()+":"+d.adapter());
        boolean acquired=false;
        try {
            acquired=lock.tryLock(1,TimeUnit.SECONDS);
            if(!acquired)throw new IllegalStateException("目标范围正在由另一任务同步");
            var task=taskService.required(root.getTaskId());
            var adapter=validator.adapter(d.adapter());
            if(!adapter.supportsStreaming())throw new IllegalArgumentException("当前业务适配器不支持流式分块执行");
            updateStatus(runId,"READING");
            SyncStreamingState resume=null;
            if("CHECKPOINT_KEY".equals(d.effectiveRestartPolicy())&&root.getPagingJson()!=null)
                resume=JsonUtils.parseObject(root.getPagingJson(),SyncStreamingState.class);
            var resolved=connections.resolve(d);
            streamingReader.stream(resolved,task.getCheckpoint(),root.getFullSnapshot(),resume,chunk->{
                if(chunk.sourceComplete())advanceStreamingSource(runId,d,chunk);
                else processStreamingChunk(runId,d,task,adapter,chunk);
            });
            tx().executeWithoutResult(status->{
                var current=required(runId);
                var state=current.getPagingJson()==null?SyncStreamingState.start(current.getSourceUpper()):
                        JsonUtils.parseObject(current.getPagingJson(),SyncStreamingState.class);
                if(!state.finished(d.sources().size()))throw new IllegalStateException("流式来源尚未全部完成");
                var t=taskService.locked(current.getTaskId());
                if(!Objects.equals(t.getActiveRunId(),runId)||!Objects.equals(t.getVersion(),current.getConfigVersion()))
                    throw new IllegalStateException("流式运行所有权或配置版本变化");
                current.setStatus(current.getPreview()?"PREVIEW_READY":"SUCCESS").setFinishedAt(LocalDateTime.now())
                        .setCachePending(!current.getPreview());runs.updateById(current);
                t.setActiveRunId(null);
                if(current.getPreview())t.setValidatedVersion(t.getVersion());
                else {
                    t.setCheckpoint(state.upper()).setRetryAttempt(0).setLastFailedRunId(null).setNextRunAt(SyncTaskService.next(d.cron()));
                    if(current.getFullSnapshot())t.setNextFullAt(SyncTaskService.next(d.fullCron()));
                    if("ONCE".equals(d.mode()))t.setEnabled(false);
                }
                tasks.updateById(t);
            });
            if(!root.getPreview())refreshCache(runId);
        } catch(Exception ex) {
            tx().executeWithoutResult(status->{
                var failure=required(runId);
                if(!Set.of("SUCCESS","PREVIEW_READY").contains(failure.getStatus())) {
                    failure.setStatus("FAILED").setFinishedAt(LocalDateTime.now()).setErrorMessage(safeError(ex));
                    runs.updateById(failure);failTask(failure,d);
                }
            });
            throw ex;
        } finally {if(acquired&&lock.isHeldByCurrentThread())lock.unlock();}
    }

    private void processStreamingChunk(Long runId,SyncDefinition d,SyncTaskDO task,DataSyncAdapter adapter,
                                       SpringJdbcStreamingReader.StreamChunk chunk) {
        var snapshot=new MysqlSyncReader.Snapshot(chunk.upper(),List.of(
                new MysqlSyncReader.SourceRows(chunk.object(),chunk.sourceObject(),chunk.rows())),chunk.bytes());
        String correlation=runId+":s"+chunk.sourceIndex()+":c"+chunk.sequence();
        var evidence=evidenceService.stage(runId,d,snapshot,correlation,"s"+chunk.sourceIndex()+"-c"+chunk.sequence());
        try {
            tx().executeWithoutResult(status->{
                var current=required(runId);
                var t=taskService.locked(task.getId());
                if(!Objects.equals(t.getActiveRunId(),runId)||!Objects.equals(t.getVersion(),current.getConfigVersion()))
                    throw new IllegalStateException("流式运行所有权或配置版本变化");
                var state=current.getPagingJson()==null?SyncStreamingState.start(chunk.upper()):
                        JsonUtils.parseObject(current.getPagingJson(),SyncStreamingState.class);
                if(state.sourceIndex()!=chunk.sourceIndex())throw new IllegalStateException("流式来源断点与当前游标不一致");
                var existing=loadPageBindings(task.getId(),d,snapshot);
                var rows=fieldMapper.transform(d,snapshot,existing);
                Set<String> stale=new HashSet<>();rows=protectNewer(rows,existing,stale);
                var batch=new DataSyncAdapter.Batch(owner(task),rows,existing,false,d.missingPolicy(),false,d.loadingMode(),false);
                var changes=current.getPreview()?adapter.preview(batch):adapter.apply(batch);
                if(changes.stream().anyMatch(c->"CONFLICT".equals(c.action())))
                    throw new IllegalArgumentException("流式分块存在目标归属或字段冲突，当前分块已回滚");
                verifyPrimaryKeys(batch,changes);
                var finalChanges=markStale(changes,stale);
                if(!current.getPreview())persistBindings(t,d,finalChanges,runId);
                evidenceService.complete(runId,d,evidence,adapter.descriptor(),
                        current.getPreview()?List.of():finalChanges,false);
                state=state.committed(chunk.lastKey(),chunk.rows().size());
                current.setPagingJson(JsonUtils.toJsonString(state)).setSourceUpper(chunk.upper())
                        .setReadCount(current.getReadCount()+chunk.rows().size())
                        .setStatus(current.getPreview()?"VALIDATING":"APPLYING");
                mergeSummary(current,finalChanges);appendResultSample(current,finalChanges,d.maxRows());runs.updateById(current);
            });
        } catch(RuntimeException ex) {
            tx().executeWithoutResult(status->evidenceService.complete(runId,d,evidence,adapter.descriptor(),List.of(),true));
            throw ex;
        }
    }

    private void advanceStreamingSource(Long runId,SyncDefinition d,SpringJdbcStreamingReader.StreamChunk chunk) {
        tx().executeWithoutResult(status->{
            var current=required(runId);
            var t=taskService.locked(current.getTaskId());
            if(!Objects.equals(t.getActiveRunId(),runId)||!Objects.equals(t.getVersion(),current.getConfigVersion()))
                throw new IllegalStateException("流式运行所有权或配置版本变化");
            var state=current.getPagingJson()==null?SyncStreamingState.start(chunk.upper()):
                    JsonUtils.parseObject(current.getPagingJson(),SyncStreamingState.class);
            if(state.sourceIndex()!=chunk.sourceIndex())throw new IllegalStateException("流式来源完成标记与断点不一致");
            current.setPagingJson(JsonUtils.toJsonString(state.nextSource())).setSourceUpper(chunk.upper())
                    .setStatus(current.getPreview()?"VALIDATING":"APPLYING");runs.updateById(current);
        });
    }

    private void executePaged(Long runId,SyncDefinition d) throws Exception {
        var root=required(runId);
        if(Set.of("SUCCESS","PREVIEW_READY").contains(root.getStatus())
                || !Objects.equals(taskService.required(root.getTaskId()).getActiveRunId(),runId))return;
        var lock=redisson.getLock("int:data-sync:"+SyncTaskService.tenant()+":"+d.adapter());
        boolean acquired=false;
        try {
            acquired=lock.tryLock(1,TimeUnit.SECONDS);
            if(!acquired)throw new IllegalStateException("目标范围正在由另一任务同步");
            if(root.getPagingJson()==null) {
                var initial=reader.pagingBounds(connections.resolve(d));
                var checkpoint=taskService.required(root.getTaskId()).getCheckpoint();
                if(checkpoint!=null&&initial.upper().isBefore(checkpoint))throw new IllegalArgumentException("来源时间早于已提交检查点，禁止游标倒退");
                tx().executeWithoutResult(s->{var r=required(runId);r.setPagingJson(JsonUtils.toJsonString(initial))
                        .setSourceUpper(initial.upper()).setStatus("READING");runs.updateById(r);});
            }
            while(true) {
                var progress=JsonUtils.parseObject(required(runId).getPagingJson(),SyncPagingState.class);
                if(progress.finished())break;
                Long pageId=tx().execute(s->{
                    var t=taskService.locked(root.getTaskId());
                    if(!Objects.equals(t.getActiveRunId(),runId)||!Objects.equals(t.getVersion(),root.getConfigVersion()))
                        throw new IllegalStateException("分页运行所有权或配置版本变化");
                    var page=new SyncRunDO().setTaskId(root.getTaskId()).setParentRunId(runId)
                            .setPageNumber(progress.completedPages()+1).setPagingJson(JsonUtils.toJsonString(progress))
                            .setRequestKey("page_"+runId+"_"+(progress.completedPages()+1)).setStatus("QUEUED")
                            .setPreview(root.getPreview()).setFullSnapshot(true).setAdoptExisting(false)
                            .setConfigSnapshot(root.getConfigSnapshot()).setConfigVersion(root.getConfigVersion())
                            .setStartedAt(LocalDateTime.now()).setCachePending(false).setReadCount(0);
                    page.setTenantId(root.getTenantId());page.setCreator(root.getCreator());page.setUpdater(root.getUpdater());
                    runs.insert(page);return page.getId();
                });
                executeSingle(pageId);
            }
            tx().executeWithoutResult(s->{
                var r=required(runId);var t=taskService.locked(r.getTaskId());
                if(!Objects.equals(t.getActiveRunId(),runId))throw new IllegalStateException("分页运行所有权变化");
                r.setStatus(r.getPreview()?"PREVIEW_READY":"SUCCESS").setFinishedAt(LocalDateTime.now());runs.updateById(r);
                t.setActiveRunId(null);
                if(r.getPreview())t.setValidatedVersion(t.getVersion());
                else {
                    t.setCheckpoint(JsonUtils.parseObject(r.getPagingJson(),SyncPagingState.class).upper())
                            .setRetryAttempt(0).setLastFailedRunId(null).setNextRunAt(SyncTaskService.next(d.cron()))
                            .setNextFullAt(SyncTaskService.next(d.fullCron()));
                    if("ONCE".equals(d.mode()))t.setEnabled(false);
                }
                tasks.updateById(t);
            });
        } catch(Exception ex) {
            tx().executeWithoutResult(s->{var r=required(runId);r.setStatus("FAILED").setErrorMessage(safeError(ex)).setFinishedAt(LocalDateTime.now());runs.updateById(r);failTask(r,d);});
            throw ex;
        } finally {if(acquired&&lock.isHeldByCurrentThread())lock.unlock();}
    }

    /** Called in the page's business/evidence transaction so retry cannot skip an uncommitted page. */
    private void advancePage(SyncRunDO page,SyncDefinition d,MysqlSyncReader.Snapshot snapshot,List<DataSyncAdapter.Change> changes) {
        var root=required(page.getParentRunId());
        var state=JsonUtils.parseObject(root.getPagingJson(),SyncPagingState.class);
        var source=d.sources().get(state.sourceIndex());
        var rows=snapshot.objects().getFirst().rows();
        long last=rows.isEmpty()?state.afterId():MysqlSyncReader.pagingKey(rows.getLast().get(source.sourceKey()));
        if(!rows.isEmpty()&&last<=state.afterId())throw new IllegalStateException("分页来源主键未推进");
        root.setPagingJson(JsonUtils.toJsonString(state.advance(last,rows.size(),d.maxRows())))
                .setReadCount(root.getReadCount()+rows.size()).setStatus(page.getPreview()?"VALIDATING":"APPLYING");
        mergeSummary(root,changes);runs.updateById(root);
    }
    public void refreshCache(Long runId) {
        var r=required(runId);if(!"SUCCESS".equals(r.getStatus())||!r.getCachePending())return;
        try {
            validator.adapter(JsonUtils.parseObject(r.getConfigSnapshot(),SyncDefinition.class).adapter()).refreshCaches();
            tx().executeWithoutResult(s->{var row=required(runId);row.setCachePending(false);runs.updateById(row);});
        }catch(RuntimeException ex){
            log.warn("Organization cache refresh pending for run {}: {}",runId,ex.getClass().getSimpleName());
        }
    }
    /** Framework recovery is only invoked while holding the same scope lock as all writers. */
    public void maintain() {
        for(var run:runs.selectMaintenance(new SyncQueries.Due(SyncTaskService.tenant(),LocalDateTime.now().minusMinutes(2)))) {
            if("SUCCESS".equals(run.getStatus())) { refreshCache(run.getId());continue; }
            var d=JsonUtils.parseObject(run.getConfigSnapshot(),SyncDefinition.class);
            var lock=redisson.getLock("int:data-sync:"+SyncTaskService.tenant()+":"+d.adapter());
            if(!lock.tryLock())continue;
            try {
                var current=required(run.getId());
                if(Set.of("SUCCESS","FAILED","PREVIEW_READY").contains(current.getStatus()))continue;
                launcher.getObject().recover(SyncTaskService.tenant(),run.getId());
                tx().executeWithoutResult(s->{
                    var r=required(run.getId());
                    if(r.getEvidenceJson()!=null)evidenceService.complete(r.getId(),d,
                            JsonUtils.parseArray(r.getEvidenceJson(),SyncEvidenceService.Evidence.class),
                            validator.adapter(d.adapter()).descriptor(),List.of(),true);
                    r.setStatus("FAILED").setErrorMessage("运行进程中断，已核对业务提交状态；可创建关联重试")
                            .setFinishedAt(LocalDateTime.now());runs.updateById(r);
                    var t=taskService.locked(r.getTaskId());
                    if(Objects.equals(t.getActiveRunId(),r.getId())){t.setActiveRunId(null);tasks.updateById(t);}
                });
            } finally {lock.unlock();}
        }
    }
    private void persistBindings(SyncTaskDO task,SyncDefinition d,List<DataSyncAdapter.Change> changes,Long runId) {
        boolean targetShared=validator.adapter(d.adapter()).sharesTargetAcrossSources();
        Map<String,SyncBindingDO> existing=new HashMap<>();
        Map<String,List<String>> keys=new LinkedHashMap<>();
        changes.stream().filter(c->c.targetId()!=null).forEach(c->keys.computeIfAbsent(c.object(),k->new ArrayList<>()).add(c.sourceKey()));
        keys.forEach((object,sourceKeys)->selectPageBindings(task.getId(),object,sourceKeys)
                .forEach(b->existing.put(b.getObjectKey()+":"+b.getSourceKey(),b)));
        List<SyncBindingDO> created=new ArrayList<>(),updated=new ArrayList<>();
        for(var c:changes) {
            if(c.targetId()==null||Set.of("SKIPPED","CLEARED").contains(c.action()))continue;
            var b=existing.get(c.object()+":"+c.sourceKey());boolean fresh=b==null;
            if(fresh)b=new SyncBindingDO().setTaskId(task.getId()).setObjectKey(c.object()).setSourceKey(c.sourceKey())
                    .setSourceObject(d.sources().stream().filter(s->s.object().equals(c.object())).findFirst().orElseThrow().sourceObject());
            b.setTenantId(task.getTenantId());b.setTargetId(c.targetId()).setFieldsJson(JsonUtils.toJsonString(c.after())).setLastRunId(runId);
            b.setTargetShared(targetShared);
            if(fresh) {b.setCreator("data_sync");b.setUpdater("data_sync");created.add(b);}
            else {b.setUpdater("data_sync");b.setUpdateTime(LocalDateTime.now());updated.add(b);}
        }
        if(!created.isEmpty())bindings.insertBatch(created,1000);
        if(!updated.isEmpty())bindings.updateBatch(updated,1000);
    }
    private List<DataSyncAdapter.Binding> loadBindings(Long taskId) {
        return bindings.selectTask(new SyncQueries.Task(SyncTaskService.tenant(),taskId)).stream()
                .map(b->new DataSyncAdapter.Binding(b.getObjectKey(),b.getSourceKey(),b.getTargetId(),JsonUtils.parseMap(b.getFieldsJson()))).toList();
    }
    private List<DataSyncAdapter.Binding> loadPageBindings(Long taskId,SyncDefinition definition,MysqlSyncReader.Snapshot snapshot) {
        List<DataSyncAdapter.Binding> result=new ArrayList<>();
        for(var object:snapshot.objects()) {
            var source=definition.sources().stream().filter(s->s.object().equals(object.object())).findFirst().orElseThrow();
            var keys=object.rows().stream().map(r->r.get(source.sourceKey()).toString()).toList();
            selectPageBindings(taskId,object.object(),keys).forEach(b->result.add(new DataSyncAdapter.Binding(
                    b.getObjectKey(),b.getSourceKey(),b.getTargetId(),JsonUtils.parseMap(b.getFieldsJson()))));
        }
        return result;
    }
    private List<SyncBindingDO> selectPageBindings(Long taskId,String object,List<String> keys) {
        List<SyncBindingDO> result=new ArrayList<>();
        for(int start=0;start<keys.size();start+=1000)
            result.addAll(bindings.selectSourceKeys(new SyncQueries.SourceKeys(SyncTaskService.tenant(),taskId,object,
                    keys.subList(start,Math.min(start+1000,keys.size())))));
        return result;
    }
    private static List<DataSyncAdapter.Row> protectNewer(List<DataSyncAdapter.Row> rows,List<DataSyncAdapter.Binding> bindings,Set<String> stale) {
        Map<String,DataSyncAdapter.Binding> old=new HashMap<>();bindings.forEach(b->old.put(b.object()+":"+b.sourceKey(),b));
        return rows.stream().map(r->{
            var prior=old.get(r.object()+":"+r.sourceKey());
            Object time=r.fields().get("_sourceUpdatedAt");Object last=prior==null?null:prior.lastFields().get("_sourceUpdatedAt");
            if(time!=null&&last!=null&&SyncFieldMapper.sourceTime(time).isBefore(SyncFieldMapper.sourceTime(last))) {
                stale.add(r.object()+":"+r.sourceKey());return new DataSyncAdapter.Row(r.object(),r.sourceKey(),prior.lastFields(),prior.targetId());
            }
            return r;
        }).toList();
    }
    private static List<DataSyncAdapter.Change> markStale(List<DataSyncAdapter.Change> changes,Set<String> stale) {
        return changes.stream().map(c->stale.contains(c.object()+":"+c.sourceKey())
                ?new DataSyncAdapter.Change(c.object(),c.sourceKey(),c.targetId(),"SKIPPED",c.before(),c.after(),"旧来源版本已跳过"):c).toList();
    }
    private static void verifyPrimaryKeys(DataSyncAdapter.Batch batch,List<DataSyncAdapter.Change> changes) {
        Map<String,DataSyncAdapter.Change> byKey=new HashMap<>();changes.forEach(c->byKey.put(c.object()+":"+c.sourceKey(),c));
        for(var row:batch.rows())if(row.fields().containsKey("_sourcePrimaryKey")) {
            long requested=SyncFieldMapper.primaryKey(row.sourceKey());var change=byKey.get(row.object()+":"+row.sourceKey());
            if(change==null)throw new IllegalStateException("适配器未返回主键同步结果");
            if(!Objects.equals(change.targetId(),requested))throw new IllegalStateException("适配器写入主键与来源主键不一致，整批回滚");
        }
    }
    private void failTask(SyncRunDO run,SyncDefinition d) {
        var t=taskService.locked(run.getTaskId());
        if(!Objects.equals(t.getActiveRunId(),run.getId()))return;
        t.setActiveRunId(null);
        if(!run.getPreview()) {
            int attempt=t.getRetryAttempt()+1;t.setRetryAttempt(attempt).setLastFailedRunId(run.getId());
            t.setNextRunAt(attempt<=d.retryCount()?LocalDateTime.now().plusSeconds(d.retryIntervalSeconds()):SyncTaskService.next(d.cron()));
            if(run.getFullSnapshot())t.setNextFullAt(attempt<=d.retryCount()?t.getNextRunAt():SyncTaskService.next(d.fullCron()));
        }
        tasks.updateById(t);
    }
    private static void mergeSummary(SyncRunDO run,List<DataSyncAdapter.Change> changes) {
        Map<String,Object> totals=run.getSummaryJson()==null?new LinkedHashMap<>():new LinkedHashMap<>(JsonUtils.parseMap(run.getSummaryJson()));
        for(var change:changes)totals.compute(change.action(),(k,v)->(v==null?0L:((Number)v).longValue())+1L);
        run.setSummaryJson(JsonUtils.toJsonString(totals));
    }
    private static void appendResultSample(SyncRunDO run,List<DataSyncAdapter.Change> changes,int limit) {
        if(limit<=0||changes.isEmpty())return;
        List<DataSyncAdapter.Change> sample=run.getResultJson()==null?new ArrayList<>():
                new ArrayList<>(JsonUtils.parseArray(run.getResultJson(),DataSyncAdapter.Change.class));
        int remaining=Math.max(0,limit-sample.size());
        if(remaining>0)sample.addAll(changes.subList(0,Math.min(remaining,changes.size())));
        run.setResultJson(JsonUtils.toJsonString(sample));
    }
    private void updateStatus(Long id,String state){tx().executeWithoutResult(s->{var r=required(id);r.setStatus(state);runs.updateById(r);});}
    private void saveResult(Long id,List<DataSyncAdapter.Change> changes){tx().executeWithoutResult(s->{var r=required(id);r.setResultJson(JsonUtils.toJsonString(changes)).setSummaryJson(summary(changes));runs.updateById(r);});}
    private static String summary(List<DataSyncAdapter.Change> changes) {
        Map<String,Long> counts=new LinkedHashMap<>();for(var change:changes)counts.merge(change.action(),1L,Long::sum);return JsonUtils.toJsonString(counts);
    }
    public static String owner(SyncTaskDO t){return "integration:"+t.getTenantId()+":"+t.getId();}
    private TransactionTemplate tx(){return new TransactionTemplate(transactionManager);}
    private static String safeError(Exception ex){
        if(ex instanceof IllegalArgumentException||ex instanceof IllegalStateException) {
            String m=ex.getMessage();return m==null?"数据校验失败":m.substring(0,Math.min(m.length(),1000));
        }
        return "执行失败（"+ex.getClass().getSimpleName()+"），请检查连接、容量和服务状态；凭据及原始SQL不写入日志";
    }
}
