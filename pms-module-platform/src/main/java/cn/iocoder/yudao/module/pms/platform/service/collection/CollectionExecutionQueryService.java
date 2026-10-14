package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CollectionExecutionQueryService implements CollectionExecutionQueryApi {
    private final CollectionRequestMapper requests;
    private final CollectionTaskApi tasks;
    private final List<CollectionSourceAdapter> sources;

    @Override
    public CollectionApplicationApi.Execution get(Query q) {
        if (q == null || !Objects.equals(TenantContextHolder.getRequiredTenantId(), q.tenantId())
                || q.actorId() == null || q.actorId() <= 0 || q.executionId() == null) {
            throw new CollectionOperationException("采集查询身份无效");
        }
        var row = requests.selectById(q.executionId());
        if (row == null || !q.tenantId().equals(row.getTenantId()) || !Objects.equals(q.entry(), row.getEntry())
                || !Objects.equals(q.objectId(), row.getObjectId())) throw new CollectionOperationException("采集记录不存在");
        var task = tasks.getTask(q.tenantId(), row.getPlatformTaskId());
        var matches = sources.stream().filter(s -> s.entry().equals(q.entry())).toList();
        if (task == null || matches.size() != 1) throw new CollectionOperationException("采集业务入口不存在");
        var source = matches.getFirst().authorize(CollectionSourceAdapter.AuthorizationQuery.builder()
                .tenantId(q.tenantId())
                .actorId(q.actorId())
                .objectId(q.objectId())
                .deviceId(Long.valueOf(task.deviceId()))
                .access(CollectionSourceAdapter.Access.READ)
                .expectedVersion(null)
                .build());
        if (!Objects.equals(source.sourceContext(), task.sourceContext())
                || !Objects.equals(source.sourceObjectType(), task.sourceObjectType())
                || !Objects.equals(q.objectId().toString(), task.sourceObjectId())
                || !Objects.equals(source.projectId().toString(), task.projectId())
                || !Objects.equals(source.deviceId().toString(), task.deviceId())) {
            throw new CollectionOperationException("采集记录与业务归属不一致");
        }
        return new CollectionApplicationApi.Execution(row.getId(), row.getActorId(), row.getCreateTime(),
                row.getConsumedResultVersion(), task, row.getCommandText(), row.getTemplateName(), row.getRetryOfId());
    }
}
