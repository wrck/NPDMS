package cn.iocoder.yudao.module.pms.engineering.service.collection;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.collection.ImplementationCollectionLogDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.collection.ImplementationCollectionLogMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.collection.query.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationCollectionOwnerMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestCollectionOwnerMapper;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImplementationCollectionLogService implements CollectionBusinessResultReceiver {
    private final ImplementationCollectionLogMapper logs;
    private final ConfigurationCollectionOwnerMapper configurations;
    private final JointTestCollectionOwnerMapper jointTests;
    private final List<CollectionSourceAdapter> sources;

    @Override public Set<String> entries() { return Set.of("configuration", "joint-test"); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Result result) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (result == null || !tenant.equals(result.tenantId()) || !entries().contains(result.entry())
                || !"IMP".equals(result.sourceContext()) || result.fileVersionId() == null
                || result.resultVersion() == null || result.resultVersion() <= 0 || result.externalStatus() == null
                || result.executionId() == null || result.actorId() == null || result.projectId() == null || result.deviceId() == null
                || result.platformTaskId() == null || result.platformTaskId().isBlank()
                || !Set.of("SUCCEEDED", "SUCCESS", "PARTIAL_SUCCESS", "FAILED", "TIMED_OUT", "CANCELLED").contains(result.externalStatus())) {
            throw new CollectionOperationException("业务日志来源或结果无效");
        }
        Long project, device, ownerTenant;
        if ("configuration".equals(result.entry()) && "Configuration".equals(result.sourceObjectType())) {
            var owner = configurations.lockById(result.objectId());
            if (owner == null) throw new CollectionOperationException("配置记录不存在，日志未回传");
            project = owner.getProjectId(); device = owner.getEquipmentId(); ownerTenant = owner.getTenantId();
        } else if ("joint-test".equals(result.entry()) && "JointTest".equals(result.sourceObjectType())) {
            var owner = jointTests.lockById(result.objectId());
            if (owner == null) throw new CollectionOperationException("联调记录不存在，日志未回传");
            project = owner.getProjectId(); device = owner.getEquipmentId(); ownerTenant = owner.getTenantId();
        } else throw new CollectionOperationException("业务日志实体类型不匹配");
        if (!tenant.equals(ownerTenant) || !Objects.equals(project, result.projectId()) || !Objects.equals(device, result.deviceId())) {
            throw new CollectionOperationException("业务记录的项目或设备已变化，日志未回传");
        }
        var existing = logs.findResult(new ImplementationCollectionLogResultQuery(tenant, result.platformTaskId(), result.resultVersion()));
        if (existing != null) {
            if (!Objects.equals(existing.getEntry(), result.entry()) || !Objects.equals(existing.getObjectId(), result.objectId())
                    || !Objects.equals(existing.getProjectId(), result.projectId()) || !Objects.equals(existing.getDeviceId(), result.deviceId())
                    || !Objects.equals(existing.getFileVersionId(), result.fileVersionId()) || !Objects.equals(existing.getExecutionId(), result.executionId())
                    || !Objects.equals(existing.getCommandText(), result.commandText()) || !Objects.equals(existing.getExternalStatus(), result.externalStatus())
                    || !Objects.equals(existing.getActorId(), result.actorId()) || !Objects.equals(existing.getProtocol(), result.protocol())
                    || !Objects.equals(existing.getTemplateName(), result.templateName()) || !Objects.equals(existing.getFailureCategory(), result.failureCategory())) {
                throw new CollectionOperationException("业务日志重放与原记录不一致");
            }
            return;
        }
        var row = new ImplementationCollectionLogDO();
        row.setTenantId(tenant); row.setEntry(result.entry()); row.setObjectId(result.objectId());
        row.setProjectId(result.projectId()); row.setDeviceId(result.deviceId()); row.setExecutionId(result.executionId());
        row.setActorId(result.actorId()); row.setCreator(String.valueOf(result.actorId())); row.setPlatformTaskId(result.platformTaskId());
        row.setResultVersion(result.resultVersion()); row.setFileVersionId(result.fileVersionId()); row.setProtocol(result.protocol());
        row.setExternalStatus(result.externalStatus()); row.setFailureCategory(result.failureCategory());
        row.setCommandText(result.commandText()); row.setTemplateName(result.templateName()); row.setReceivedAt(LocalDateTime.now());
        if (logs.insert(row) != 1) throw new IllegalStateException("BUSINESS_COLLECTION_LOG_CREATE_FAILED");
    }

    public PageResult<View> page(String entry, Long objectId, Long actor, int pageNo, int pageSize) {
        if (!entries().contains(entry) || pageNo < 1 || pageSize < 1 || pageSize > 50) throw new CollectionOperationException("业务日志查询参数无效");
        var matches = sources.stream().filter(s -> entry.equals(s.entry())).toList();
        if (matches.size() != 1) throw new CollectionOperationException("业务日志入口尚未接入");
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var source = matches.getFirst().authorize(tenant, actor, objectId, null, CollectionSourceAdapter.Access.READ, null);
        var page = logs.page(new ImplementationCollectionLogPageQuery(tenant, entry, objectId, source.projectId(), source.deviceId(), pageNo, pageSize));
        return new PageResult<>(page.getList().stream().map(r -> new View(r.getId(), r.getEntry(), r.getObjectId(),
                r.getProjectId(), r.getDeviceId(), r.getExecutionId(), r.getPlatformTaskId(), r.getResultVersion(), r.getFileVersionId(),
                r.getProtocol(), r.getExternalStatus(), r.getFailureCategory(), r.getCommandText(), r.getTemplateName(), r.getReceivedAt())).toList(), page.getTotal());
    }

    public record View(Long id, String entry, Long objectId, Long projectId, Long deviceId, Long executionId,
                       String platformTaskId, Long resultVersion, Long fileVersionId, String protocol,
                       String externalStatus, String failureCategory, String commandText, String templateName, LocalDateTime receivedAt) { }
}
