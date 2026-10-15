package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncRunDO;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Reuses the original account/role reads; never reads PLT tables or reruns an upstream task. */
@Component @RequiredArgsConstructor
public class SyncSourceEvidenceReader {
    private final SyncTaskMapper tasks;
    private final SyncRunMapper runs;
    private final PlatformMigrationEvidenceApi evidence;
    record Input(SyncDefinition definition,SyncDefinition.Source source,Map<String,SyncRunDO> completed) {}
    static boolean reference(SyncDefinition.Source source){return "EVIDENCE".equals(source.readMode());}
    static void validate(SyncDefinition d,SyncDefinition.Source s) {
        if(!"ONCE".equals(d.mode()) || !"SNAPSHOT".equals(d.effectiveReadStrategy()) || !"DPPMS".equals(d.sourceSystem())
                || !Set.of("DPPMS_USER_ROLES","DPPMS_USER_SCOPES").contains(d.adapter())
                || !Set.of("DPPMS_USERS","DPPMS_USER_ROLES").contains(Objects.toString(s.evidenceAdapter(),""))
                || d.adapter().equals(s.evidenceAdapter()) || s.evidenceObject()==null || s.queryRef()!=null
                || s.groupKey()!=null || s.rowProjection()!=null || s.sql()!=null || s.table()!=null
                || s.parameters()!=null && !s.parameters().isEmpty() || s.filters()!=null && !s.filters().isEmpty())
            throw new IllegalArgumentException("共享来源证据只支持已批准的账号、角色与范围依赖，不能改写来源范围");
        MysqlSyncReader.identifier(s.evidenceObject());
    }
    MysqlSyncReader.SourceRows read(Input input) {
        var d=input.definition();var s=input.source();validate(d,s);
        Long connection=d.configuredConnectionId()==null?d.connectionId():d.configuredConnectionId();
        long tenant=TenantContextHolder.getRequiredTenantId();
        var run=input.completed().computeIfAbsent(s.evidenceAdapter(),adapter->{
            var task=tasks.selectIdentity(new SyncQueries.TaskIdentity(tenant,d.sourceSystem(),adapter));
            if(task==null || !Objects.equals(task.getTenantId(),tenant) || !Objects.equals(task.getConnectionId(),connection)
                    || task.getActiveRunId()!=null)fail("前置任务缺失、来源连接不同或仍在运行");
            var completed=runs.selectSuccessfulSnapshot(new SyncRunMapper.CompletedSnapshot(tenant,task.getId(),task.getVersion()));
            if(completed==null || !Objects.equals(completed.getTenantId(),tenant)
                    || !Objects.equals(completed.getTaskId(),task.getId()) || !Objects.equals(completed.getConfigVersion(),task.getVersion())
                    || !Boolean.FALSE.equals(completed.getPreview()) || !Boolean.TRUE.equals(completed.getFullSnapshot())
                    || !"SUCCESS".equals(completed.getStatus()) || completed.getEvidenceJson()==null)fail("前置任务当前版本没有成功的完整迁入证据");
            var frozen=JsonUtils.parseObject(completed.getConfigSnapshot(),SyncDefinition.class);
            if(frozen==null || !Objects.equals(frozen.connectionId(),connection) || !adapter.equals(frozen.adapter())
                    || !d.sourceSystem().equals(frozen.sourceSystem()))fail("前置回执的来源配置不匹配");
            return completed;
        });
        var frozen=JsonUtils.parseObject(run.getConfigSnapshot(),SyncDefinition.class);
        var original=frozen.sources().stream().filter(x->s.evidenceObject().equals(x.object())).findFirst().orElseThrow();
        if(!s.sourceObject().equals(original.sourceObject()) || !s.sourceKey().equals(original.sourceKey()))
            fail("复用来源对象或原主键不同");
        var batches=JsonUtils.parseArray(run.getEvidenceJson(),SyncEvidenceService.Evidence.class).stream()
                .filter(e->s.evidenceObject().equals(e.object())).toList();
        if(batches.isEmpty())fail("前置完整快照缺少来源对象证据");
        var rows=new ArrayList<Map<String,Object>>();var keys=new HashSet<String>();
        for(var batch:batches) {
            if(!s.sourceObject().equals(batch.sourceObject()))fail("来源证据表身份不同");
            Long after=null;var expected=new HashSet<>(batch.recordIds().values());
            do {
                var page=evidence.pageSourceRecords(new MigrationSourceRecordPageQuery(tenant,batch.batchId(),after,500));
                for(var row:page.records()) {
                    if(!Objects.equals(row.tenantId(),tenant) || !Objects.equals(row.batchId(),batch.batchId())
                            || !d.sourceSystem().equals(row.sourceSystem()) || !s.sourceObject().equals(row.sourceTable())
                            || !expected.remove(row.sourceRecordId()) || !keys.add(row.sourcePk()))fail("来源证据越界、缺失或重复");
                    var fields=new LinkedHashMap<>(JsonUtils.parseMap(row.sourcePayloadJson()));
                    if(!row.sourcePk().equals(Objects.toString(fields.get(s.sourceKey()),null)))fail("来源证据原主键不同");
                    fields.put("_sharedSourceRunId",run.getId());fields.put("_sharedSourceRecordId",row.sourceRecordId());
                    rows.add(Collections.unmodifiableMap(fields));
                    if(rows.size()>d.maxRows())fail("共享来源超过容量限制");
                }
                Long next=page.nextAfterSourceRecordId();
                if(next!=null && after!=null && next<=after)fail("来源证据游标未前进");
                after=next;
            }while(after!=null);
            if(!expected.isEmpty())fail("原始来源快照不完整");
        }
        return new MysqlSyncReader.SourceRows(s.object(),s.sourceObject(),List.copyOf(rows));
    }
    private static void fail(String message){throw new IllegalArgumentException("共享来源证据："+message);}
}
