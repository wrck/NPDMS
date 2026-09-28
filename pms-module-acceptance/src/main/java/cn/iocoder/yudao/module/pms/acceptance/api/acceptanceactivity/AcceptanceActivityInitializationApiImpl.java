package cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto.AcceptanceActivityInitializationCommand;
import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto.AcceptanceActivityInitializationResult;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceActivityIdentityLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
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
    private final PlatformDeliveryRequirementApi platform;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public AcceptanceActivityInitializationResult initialize(AcceptanceActivityInitializationCommand command) {
        if (!valid(command)) return result("IDENTITY_MISMATCH", null, null);
        AcceptanceActivityDO existing = activityMapper.selectByIdentityForUpdate(
                new AcceptanceActivityIdentityLockQuery(command.tenantId(), command.projectId(),
                        command.acceptanceType()));
        // 交付件身份在平台（tenant+project+type_code 唯一），锁级定位沿用初始化事务的行锁语义。
        var deliverable = platform.lockByIdentity(command.projectId(), command.deliverableCode()).orElse(null);
        if (deliverable == null || !Objects.equals(deliverable.projectId(), command.projectId())
                || !Objects.equals(deliverable.deliverableCode(), command.deliverableCode())
                || (deliverable.taskCode() != null && !Objects.equals(deliverable.taskCode(), command.taskDefinitionKey())))
            return result("IDENTITY_MISMATCH", null, null);
        if (existing != null) {
            return Objects.equals(existing.getProjectTaskId(), command.projectTaskId())
                    && Objects.equals(existing.getExecutionContractId(), command.executionContractId())
                    && Objects.equals(existing.getDeliverableId(), deliverable.id())
                    ? result("INITIALIZED", existing.getId(), existing.getVersion())
                    : result("DUPLICATE_OR_PARTIAL", null, null);
        }
        AcceptanceActivityDO row = new AcceptanceActivityDO();
        row.setId(IdWorker.getId());
        row.setTenantId(command.tenantId());
        row.setProjectId(command.projectId());
        row.setProjectTaskId(command.projectTaskId());
        row.setExecutionContractId(command.executionContractId());
        row.setDeliverableId(deliverable.id());
        row.setAcceptanceType(command.acceptanceType());
        row.setActivityStatus("PENDING");
        row.setVersion(0L);
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

    private AcceptanceActivityInitializationResult result(String outcome, Long id, Long version) {
        return new AcceptanceActivityInitializationResult(outcome, id, version);
    }

}
