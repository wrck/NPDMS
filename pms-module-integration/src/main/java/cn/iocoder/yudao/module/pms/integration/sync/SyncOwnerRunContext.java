package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Shared active-run authorization for the newly exposed historical Owner adapters. */
@Component @RequiredArgsConstructor
final class SyncOwnerRunContext {
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    record Request(DataSyncAdapter.Batch batch,String adapter,boolean apply) {}
    record Context(Long tenantId,Long actorId,SyncTaskDO task,SyncRunDO run,SyncDefinition definition) {}
    Context require(Request request) {
        long tenant=TenantContextHolder.getRequiredTenantId();var batch=request.batch();
        if(batch==null || batch.clearBeforeLoad() || batch.adoptExisting() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy()) || batch.owner()==null
                || !batch.owner().matches("integration:"+tenant+":[1-9][0-9]*"))fail();
        var task=tasks.required(SyncFieldMapper.primaryKey(batch.owner().substring(batch.owner().lastIndexOf(':')+1)));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!Objects.equals(task.getTenantId(),tenant) || !request.adapter().equals(task.getAdapter())
                || !"DPPMS".equals(task.getSourceSystem()) || definition==null || !"ONCE".equals(definition.mode())
                || definition.resetMappingsBeforeLoad() || run==null || !Objects.equals(run.getTenantId(),tenant)
                || !Objects.equals(run.getTaskId(),task.getId()) || !Objects.equals(run.getConfigVersion(),task.getVersion())
                || !Objects.equals(run.getPreview(),!request.apply())
                || !(request.apply()?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))fail();
        return new Context(tenant,SyncFieldMapper.primaryKey(run.getCreator()),task,run,definition);
    }
    private static void fail(){throw new IllegalArgumentException("DPPMS_OWNER_RUN_SCOPE_INVALID");}
}
