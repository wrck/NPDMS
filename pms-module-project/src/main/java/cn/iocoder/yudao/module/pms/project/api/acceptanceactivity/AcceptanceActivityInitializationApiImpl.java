package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityInitializationResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverableIdentityLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityIdentityLockQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AcceptanceActivityInitializationApiImpl implements AcceptanceActivityInitializationApi {

    private final AcceptanceActivityMapper activityMapper;
    private final AccProjectDeliverableMapper deliverableMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public AcceptanceActivityInitializationResult initialize(AcceptanceActivityInitializationCommand command) {
        if (!valid(command)) return result("IDENTITY_MISMATCH", null, null);
        AcceptanceActivityDO existing = activityMapper.selectByIdentityForUpdate(
                new AcceptanceActivityIdentityLockQuery(command.tenantId(), command.projectId(),
                        command.acceptanceType()));
        var deliverable = deliverableMapper.selectByProjectAndCodeForUpdate(
                new ProjectDeliverableIdentityLockQuery(command.tenantId(), command.projectId(),
                        command.deliverableCode()));
        if (deliverable == null || !Objects.equals(deliverable.getTenantId(), command.tenantId())
                || !Objects.equals(deliverable.getProjectId(), command.projectId())
                || !Objects.equals(deliverable.getDeliverableCode(), command.deliverableCode())
                || (deliverable.getTaskCode() != null && !Objects.equals(deliverable.getTaskCode(), command.taskDefinitionKey())))
            return result("IDENTITY_MISMATCH", null, null);
        if (existing != null) {
            return Objects.equals(existing.getProjectTaskId(), command.projectTaskId())
                    && Objects.equals(existing.getExecutionContractId(), command.executionContractId())
                    && Objects.equals(existing.getDeliverableId(), deliverable.getId())
                    ? result("INITIALIZED", existing.getId(), existing.getVersion())
                    : result("DUPLICATE_OR_PARTIAL", null, null);
        }
        AcceptanceActivityDO row = new AcceptanceActivityDO();
        row.setId(IdWorker.getId());
        row.setTenantId(command.tenantId());
        row.setProjectId(command.projectId());
        row.setProjectTaskId(command.projectTaskId());
        row.setExecutionContractId(command.executionContractId());
        row.setDeliverableId(deliverable.getId());
        row.setAcceptanceType(command.acceptanceType());
        row.setActivityStatus("PENDING");
        row.setVersion(0);
        row.setCreator("acceptance-activity-initializer");
        row.setUpdater("acceptance-activity-initializer");
        if (activityMapper.insert(row) != 1) return result("DEPENDENCY_UNAVAILABLE", null, null);
        return result("INITIALIZED", row.getId(), row.getVersion());
    }

    private boolean valid(AcceptanceActivityInitializationCommand command) {
        Long tenantId = TenantContextHolder.getTenantId();
        return command != null && tenantId != null && tenantId.equals(command.tenantId())
                && command.projectId() != null && command.projectId() > 0
                && command.projectTaskId() != null && command.projectTaskId() > 0
                && command.executionContractId() != null && command.executionContractId() > 0
                && command.taskDefinitionKey() != null && !command.taskDefinitionKey().isBlank()
                && command.acceptanceType() != null && Set.of("PRELIMINARY", "FINAL").contains(command.acceptanceType())
                && command.deliverableCode() != null && !command.deliverableCode().isBlank()
                && command.templateRevision() != null && command.templateRevision() > 0;
    }

    private AcceptanceActivityInitializationResult result(String outcome, Long id, Integer version) {
        return new AcceptanceActivityInitializationResult(outcome, id, version);
    }

}
