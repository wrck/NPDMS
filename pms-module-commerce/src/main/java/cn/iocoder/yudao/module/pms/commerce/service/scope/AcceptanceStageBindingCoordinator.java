package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeBindingApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingFact;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingResult;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.EffectiveScopeBindingCommand;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectAcceptanceStageFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFact;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFactQuery;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AcceptanceStageBindingCoordinator {

    private static final String TRIGGER_SCOPE_EFFECTIVE = "SCOPE_VERSION_EFFECTIVE";

    private final ProjectAcceptanceStageFactApi stageFactApi;
    private final AcceptanceScopeBindingApi bindingApi;

    public StageContext lockAndRead(Long tenantId, Long projectId, Integer expectedProjectVersion,
                                    String operationId) {
        ProjectAcceptanceStageFact fact = stageFactApi.lockAndRead(new ProjectAcceptanceStageFactQuery(
                tenantId, projectId, expectedProjectVersion, operationId));
        if (fact == null || fact.outcome() != ProjectFactOutcome.FOUND
                || !Objects.equals(fact.projectId(), projectId)
                || !Objects.equals(fact.projectVersion(), expectedProjectVersion)
                || blank(fact.currentStageCode()) || blank(fact.acceptanceStageCode())) {
            throw new IllegalStateException("PROJECT_ACCEPTANCE_STAGE_FACT_INVALID");
        }
        boolean acceptanceStage = Objects.equals(fact.currentStageCode(), fact.acceptanceStageCode());
        if (acceptanceStage != positive(fact.projectStageSnapshotId())) {
            throw new IllegalStateException("PROJECT_ACCEPTANCE_STAGE_SNAPSHOT_INVALID");
        }
        return new StageContext(tenantId, projectId, expectedProjectVersion,
                fact.projectStageSnapshotId(), acceptanceStage);
    }

    public void bindIfRequired(StageContext context, Long deliveryScopeId, Long allocationVersion,
                               String operationId) {
        if (!context.acceptanceStage()) {
            return;
        }
        AcceptanceScopeBindingResult result = bindingApi.bindEffectiveScope(new EffectiveScopeBindingCommand(
                context.tenantId(), context.projectId(), context.projectStageSnapshotId(), deliveryScopeId,
                allocationVersion, operationId));
        AcceptanceScopeBindingFact binding = result == null || result.bindings() == null
                || result.bindings().size() != 1 ? null : result.bindings().getFirst();
        if (binding == null || !positive(binding.bindingId())
                || !Objects.equals(binding.projectStageSnapshotId(), context.projectStageSnapshotId())
                || !Objects.equals(binding.deliveryScopeId(), deliveryScopeId)
                || !Objects.equals(binding.scopeAllocationVersion(), allocationVersion)
                || !TRIGGER_SCOPE_EFFECTIVE.equals(binding.bindingTrigger())
                || binding.acceptanceFactVersion() == null || binding.acceptanceFactVersion() <= 0
                || result.acceptanceFactVersion() == null
                || !Objects.equals(result.acceptanceFactVersion(), binding.acceptanceFactVersion())) {
            throw new IllegalStateException("ACCEPTANCE_SCOPE_BINDING_INVALID");
        }
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record StageContext(Long tenantId, Long projectId, Integer projectVersion,
                               Long projectStageSnapshotId, boolean acceptanceStage) {
    }
}
