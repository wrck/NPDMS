package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper;
import cn.iocoder.yudao.module.pms.integration.sync.SyncConnectionService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public final class BaseSyncService {
    public record Run(long taskId,Integer batchSize,List<String> stages) {
        public Run { stages=stages==null?List.of():List.copyOf(stages); }
        public Run(long taskId){this(taskId,null,List.of());}
        public Run(long taskId,Integer batchSize){this(taskId,batchSize,List.of());}
    }
    public record PreviewRecovery(long taskId,long runId) {}
    public record Task(String code, String name) {}
    private final LocalDateTime runtimeStartedAt=LocalDateTime.now();
    private final List<AbstractSyncTask<?,?>> tasks;
    private final ConfiguredSyncSource sources;
    private final SyncReferences references;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    private final PlatformTransactionManager transactions;
    private final BaseSyncMetadataMapper metadata;
    private final SyncConnectionService connections;
    private final org.mybatis.spring.SqlSessionTemplate target;

    public BaseSyncService(List<AbstractSyncTask<?,?>> tasks, ConfiguredSyncSource sources,
                           PermissionApi permissions, AdminUserApi users, PlatformTransactionManager transactions,
                           BaseSyncMetadataMapper metadata, SyncConnectionService connections, SyncReferences references, org.mybatis.spring.SqlSessionTemplate target) {
        this.tasks = List.copyOf(tasks);
        this.sources = sources;
        this.references = references;
        this.target = target;
        this.permissions = permissions;
        this.users = users;
        this.transactions = transactions;
        this.metadata = metadata;
        this.connections = connections;
        if (tasks.stream().map(AbstractSyncTask::taskCode).distinct().count() != tasks.size())
            throw new IllegalStateException("基类同步任务编码重复");
    }
    public List<Task> tasks() {
        return tasks.stream().map(task -> new Task(task.taskCode(), task.taskName()))
                .sorted(Comparator.comparing(Task::code)).toList();
    }
    public List<BaseSyncMetadataMapper.TaskRow> registrations() {
        return metadata.selectRegisteredTasks(new BaseSyncMetadataMapper.TenantTasksQuery(TenantContextHolder.getRequiredTenantId()));
    }
    public SyncCounts preview(Run request) { return run(request, true); }
    public SyncCounts execute(Run request) { return run(request, false); }
    /** Ends only a previous runtime's abandoned preview, under the same lock held by live work. */
    public void recoverPreview(PreviewRecovery request) {
        long tenant=TenantContextHolder.getRequiredTenantId();Long actor=SecurityFrameworkUtils.getLoginUserId();
        if(actor==null || !permissions.hasAnyPermissions(actor,"pms:integration:execute"))
            throw new IllegalArgumentException("缺少同步执行权限");
        users.validateUser(actor);
        transaction().executeWithoutResult(status->{
            var task=metadata.selectTask(new BaseSyncMetadataMapper.TaskQuery(tenant,request.taskId(),true));
            if(task==null || !Objects.equals(task.activeRunId(),request.runId()))throw new IllegalStateException("独立预览占用已变化");
            for(var permission:requiredTask(task.taskCode()).requiredPermissions())
                if(!permissions.hasAnyPermissions(actor,permission))throw new IllegalArgumentException("缺少任务业务权限："+permission);
            var run=metadata.selectRun(new BaseSyncMetadataMapper.RunQuery(tenant,request.taskId(),request.runId()));
            if(run==null || !run.preview() || !"READING".equals(run.status()) || run.startedAt()==null
                    || !run.startedAt().isBefore(runtimeStartedAt))throw new IllegalStateException("只能核查前一运行进程遗留的独立预览");
            var finish=new BaseSyncMetadataMapper.FinishRun(tenant,task.id(),request.runId(),SyncCounts.empty(),"FAILED","PREVIEW_INTERRUPTED_BEFORE_CURRENT_RUNTIME");
            if(metadata.finishRun(finish)!=1 || metadata.releaseTask(finish)!=1)throw new IllegalStateException("独立预览中断回执保存冲突");
        });
    }
    public long register(BaseSyncConfiguration config) {
        long tenantId=TenantContextHolder.getRequiredTenantId();
        Long actorId=SecurityFrameworkUtils.getLoginUserId();
        if(actorId==null || !permissions.hasAnyPermissions(actorId,"pms:integration:configure"))
            throw new IllegalArgumentException("缺少同步配置权限");
        users.validateUser(actorId);
        connections.required(config.connectionId());
        var task=requiredTask(config.taskCode());
        long id=IdWorker.getId();
        var row=new BaseSyncMetadataMapper.Registration(id,tenantId,task.taskName(),task.sourceSystem(),config,actorId.toString());
        transaction().executeWithoutResult(status -> {
            if(metadata.createTask(row)!=1) throw new IllegalStateException("新任务注册失败");
        });
        return id;
    }
    private SyncCounts run(Run request, boolean preview) {
        Objects.requireNonNull(request);
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null) throw new IllegalArgumentException("必须使用正式登录上下文");
        users.validateUser(actorId);
        if (!permissions.hasAnyPermissions(actorId, "pms:integration:execute"))
            throw new IllegalArgumentException("缺少同步执行权限");
        var registration=metadata.selectTask(new BaseSyncMetadataMapper.TaskQuery(tenantId,request.taskId(),false));
        if(registration==null)
            throw new IllegalArgumentException("只能执行新注册的基类同步任务");
        var registeredConfig=registration.configuration();
        var config=request.batchSize()==null?registeredConfig:new BaseSyncConfiguration(registeredConfig.taskCode(),registeredConfig.connectionId(),request.batchSize(),registeredConfig.maxRows());
        var task=requiredTask(config.taskCode());
        task.validateStages(request.stages());
        for (var permission : task.requiredPermissions())
            if (!permissions.hasAnyPermissions(actorId, permission)) throw new IllegalArgumentException("缺少任务业务权限：" + permission);
        connections.required(config.connectionId());
        var run=new BaseSyncMetadataMapper.RunRow(IdWorker.getId(),tenantId,registration.id(),UUID.randomUUID().toString(),
                config,actorId.toString(),preview);
        var claim=new BaseSyncMetadataMapper.RunRow(run.id(),run.tenantId(),run.taskId(),run.requestKey(),registeredConfig,run.actor(),run.preview());
        transaction().executeWithoutResult(status -> {
            var locked=metadata.selectTask(new BaseSyncMetadataMapper.TaskQuery(tenantId,registration.id(),true));
            if(locked==null || locked.activeRunId()!=null || !Objects.equals(locked.configuration(),registeredConfig))
                throw new IllegalStateException("新任务已有运行或配置已变化");
            if(metadata.createRun(run)!=1 || metadata.claimTask(claim)!=1) throw new IllegalStateException("无法认领新任务运行");
        });
        var context = new SyncContext<Void>(tenantId, actorId, config.connectionId(), preview, config.batchSize(),
                config.maxRows(), LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS), sources,new RunSyncIdentityStore(metadata,run),references,config.taskCode(),target,false,task.defaultUseRowHandler()).forStages(request.stages());
        try {
            var settings=task.retrySettings();
            var retry=new RetryTemplate(RetryPolicy.builder().maxRetries(settings.maxRetries())
                    .delay(Duration.ofMillis(settings.intervalMillis())).jitter(Duration.ZERO)
                    .multiplier(settings.exponentialBackoff()?2:1).predicate(task::retryAllowed).build());
            // Retry the complete work only after Spring has rolled back the failed attempt.
            return retry.invoke(() -> transaction().execute(status -> {
                var locked=metadata.selectTask(new BaseSyncMetadataMapper.TaskQuery(tenantId,registration.id(),true));
                if(locked==null || !Objects.equals(locked.activeRunId(),run.id())) throw new IllegalStateException("新任务运行上下文已变化");
                var result=task.execute(context);
                var finish=new BaseSyncMetadataMapper.FinishRun(tenantId,registration.id(),run.id(),result,
                        preview?"PREVIEW_READY":"COMPLETED",context.runState().warnings().isEmpty()?null:String.join(";",context.runState().warnings()));
                if(metadata.finishRun(finish)!=1 || metadata.releaseTask(finish)!=1) throw new IllegalStateException("新任务回执保存冲突");
                return result;
            }));
        } catch(RuntimeException | Error failure) {
            String reason=failure.getClass()==IllegalArgumentException.class || failure.getClass()==IllegalStateException.class
                    ?failure.getMessage():"同步读取或写入失败："+failure.getClass().getSimpleName();
            if(!request.stages().isEmpty())reason="STAGES:"+String.join(",",request.stages())+";"+reason;
            var finish=new BaseSyncMetadataMapper.FinishRun(tenantId,registration.id(),run.id(),SyncCounts.empty(),"FAILED",reason);
            try {
                transaction().executeWithoutResult(status -> {
                    if(metadata.finishRun(finish)!=1 || metadata.releaseTask(finish)!=1)
                        throw new IllegalStateException("新任务失败回执保存冲突");
                });
            } catch(RuntimeException receiptFailure) {
                reason+="；失败回执未能保存，运行占用需核查";
            }
            throw new IllegalStateException("运行 "+run.id()+"："+reason);
        }
    }
    private AbstractSyncTask<?,?> requiredTask(String code) {
        return tasks.stream().filter(candidate -> candidate.taskCode().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("新的基类同步任务不存在"));
    }
    private TransactionTemplate transaction() {
        var transaction=new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }
}
