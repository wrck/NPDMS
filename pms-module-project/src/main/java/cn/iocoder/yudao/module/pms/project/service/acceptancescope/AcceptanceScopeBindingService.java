package cn.iocoder.yudao.module.pms.project.service.acceptancescope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeAcceptanceLockApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeAcceptanceLockCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeVersionFact;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeBindingApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeGuardApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingFact;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingResult;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardOutcome;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardResult;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceStageEntryBindingCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.EffectiveScopeBindingCommand;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancescope.AcceptanceScopeBindingDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancescope.query.AcceptanceScopeBindingIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancescope.query.AcceptanceScopeCurrentQuery;
import cn.iocoder.yudao.module.pms.project.dal.repository.acceptancescope.AcceptanceScopeBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_ACCEPTANCE_SCOPE_BINDING_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_ACCEPTANCE_SCOPE_REQUEST_INVALID;

@Service
@RequiredArgsConstructor
public class AcceptanceScopeBindingService implements AcceptanceScopeBindingApi, AcceptanceScopeGuardApi {

    private static final String TRIGGER_STAGE_ENTRY = "PROJECT_STAGE_ENTRY";
    private static final String TRIGGER_SCOPE_EFFECTIVE = "SCOPE_VERSION_EFFECTIVE";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final int INITIAL_FACT_VERSION = 1;

    private final DeliveryScopeAcceptanceLockApi deliveryScopeLockApi;
    private final AcceptanceScopeBindingRepository bindingRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public AcceptanceScopeBindingResult bindForStageEntry(AcceptanceStageEntryBindingCommand command) {
        validateStageCommand(command);
        List<DeliveryScopeVersionFact> scopes = deliveryScopeLockApi.lockCurrentByProject(
                new DeliveryScopeAcceptanceLockCommand(command.tenantId(), command.projectId(),
                        command.projectStageSnapshotId(), command.operationId()));
        validateLockedScopes(scopes);
        List<AcceptanceScopeBindingFact> bindings = new ArrayList<>(scopes.size());
        boolean replayed = !scopes.isEmpty();
        for (DeliveryScopeVersionFact scope : scopes) {
            BindingOutcome outcome = bindOne(command.tenantId(), command.projectId(),
                    command.projectStageSnapshotId(), scope.deliveryScopeId(), scope.allocationVersion(),
                    TRIGGER_STAGE_ENTRY);
            replayed &= outcome.replayed();
            bindings.add(outcome.fact());
        }
        return new AcceptanceScopeBindingResult(replayed, INITIAL_FACT_VERSION, List.copyOf(bindings));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public AcceptanceScopeBindingResult bindEffectiveScope(EffectiveScopeBindingCommand command) {
        validateEffectiveScopeCommand(command);
        BindingOutcome outcome = bindOne(command.tenantId(), command.projectId(),
                command.projectStageSnapshotId(), command.deliveryScopeId(), command.scopeAllocationVersion(),
                TRIGGER_SCOPE_EFFECTIVE);
        return new AcceptanceScopeBindingResult(outcome.replayed(), outcome.fact().acceptanceFactVersion(),
                List.of(outcome.fact()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public AcceptanceScopeGuardResult checkReduction(AcceptanceScopeGuardQuery query) {
        validateGuardQuery(query);
        List<AcceptanceScopeBindingDO> current = bindingRepository.selectCurrentByScopeForUpdate(
                new AcceptanceScopeCurrentQuery(query.tenantId(), query.deliveryScopeId()));
        if (current == null) {
            return new AcceptanceScopeGuardResult(AcceptanceScopeGuardOutcome.UNKNOWN,
                    null, null, query.deliveryScopeId(), query.currentAllocationVersion());
        }
        if (current.isEmpty()) {
            return new AcceptanceScopeGuardResult(AcceptanceScopeGuardOutcome.UNLOCKED,
                    null, null, query.deliveryScopeId(), query.currentAllocationVersion());
        }
        AcceptanceScopeBindingDO exact = current.stream()
                .filter(row -> validLockedRow(row)
                        && Objects.equals(row.getTenantId(), query.tenantId())
                        && Objects.equals(row.getProjectId(), query.projectId())
                        && Objects.equals(row.getDeliveryScopeId(), query.deliveryScopeId())
                        && Objects.equals(row.getScopeAllocationVersion(), query.currentAllocationVersion()))
                .findFirst().orElse(null);
        if (exact == null) {
            AcceptanceScopeBindingDO first = current.getFirst();
            return new AcceptanceScopeGuardResult(AcceptanceScopeGuardOutcome.UNKNOWN,
                    first.getAcceptanceFactVersion(), first.getProjectStageSnapshotId(),
                    query.deliveryScopeId(), query.currentAllocationVersion());
        }
        return new AcceptanceScopeGuardResult(AcceptanceScopeGuardOutcome.LOCKED,
                exact.getAcceptanceFactVersion(), exact.getProjectStageSnapshotId(),
                exact.getDeliveryScopeId(), exact.getScopeAllocationVersion());
    }

    private BindingOutcome bindOne(Long tenantId, Long projectId, Long stageSnapshotId,
                                   Long scopeId, Long allocationVersion, String trigger) {
        AcceptanceScopeBindingIdentityQuery identity = new AcceptanceScopeBindingIdentityQuery(
                tenantId, projectId, stageSnapshotId, scopeId, allocationVersion);
        AcceptanceScopeBindingDO existing = bindingRepository.selectByIdentityForUpdate(identity);
        if (existing != null) {
            if (!validLockedRow(existing) || !trigger.equals(existing.getBindingTrigger())) {
                throw exception(ACC_ACCEPTANCE_SCOPE_BINDING_CONFLICT);
            }
            return new BindingOutcome(true, toFact(existing));
        }
        AcceptanceScopeBindingDO created = new AcceptanceScopeBindingDO();
        created.setTenantId(tenantId);
        created.setProjectId(projectId);
        created.setProjectStageSnapshotId(stageSnapshotId);
        created.setDeliveryScopeId(scopeId);
        created.setScopeAllocationVersion(allocationVersion);
        created.setBindingTrigger(trigger);
        created.setBindingStatus(STATUS_LOCKED);
        created.setEffectiveFrom(LocalDateTime.now());
        created.setEffectiveTo(null);
        created.setAcceptanceFactVersion(INITIAL_FACT_VERSION);
        created.setVersion(0);
        if (bindingRepository.append(created) != 1) {
            throw exception(ACC_ACCEPTANCE_SCOPE_BINDING_CONFLICT);
        }
        return new BindingOutcome(false, toFact(created));
    }

    private AcceptanceScopeBindingFact toFact(AcceptanceScopeBindingDO row) {
        return new AcceptanceScopeBindingFact(row.getId(), row.getProjectStageSnapshotId(),
                row.getDeliveryScopeId(), row.getScopeAllocationVersion(), row.getBindingTrigger(),
                row.getAcceptanceFactVersion());
    }

    private boolean validLockedRow(AcceptanceScopeBindingDO row) {
        return row != null && STATUS_LOCKED.equals(row.getBindingStatus()) && row.getEffectiveTo() == null
                && row.getAcceptanceFactVersion() != null && row.getAcceptanceFactVersion() > 0;
    }

    private void validateStageCommand(AcceptanceStageEntryBindingCommand command) {
        if (command == null || !validTenant(command.tenantId()) || !positive(command.projectId())
                || command.projectVersion() == null || command.projectVersion() < 0
                || !positive(command.projectStageSnapshotId()) || blank(command.fromStageCode())
                || blank(command.acceptanceStageCode()) || blank(command.operationId())
                || command.fromStageCode().equals(command.acceptanceStageCode())) {
            throw exception(ACC_ACCEPTANCE_SCOPE_REQUEST_INVALID);
        }
    }

    private void validateEffectiveScopeCommand(EffectiveScopeBindingCommand command) {
        if (command == null || !validTenant(command.tenantId()) || !positive(command.projectId())
                || !positive(command.projectStageSnapshotId()) || !positive(command.deliveryScopeId())
                || !positive(command.scopeAllocationVersion()) || blank(command.operationId())) {
            throw exception(ACC_ACCEPTANCE_SCOPE_REQUEST_INVALID);
        }
    }

    private void validateGuardQuery(AcceptanceScopeGuardQuery query) {
        if (query == null || !validTenant(query.tenantId()) || !positive(query.projectId())
                || !positive(query.deliveryScopeId()) || !positive(query.currentAllocationVersion())
                || query.proposedAllocatedQty() == null || query.proposedAllocatedQty().compareTo(BigDecimal.ZERO) < 0
                || blank(query.operationId())) {
            throw exception(ACC_ACCEPTANCE_SCOPE_REQUEST_INVALID);
        }
    }

    private void validateLockedScopes(List<DeliveryScopeVersionFact> scopes) {
        if (scopes == null) {
            throw exception(ACC_ACCEPTANCE_SCOPE_REQUEST_INVALID);
        }
        Long previousId = null;
        for (DeliveryScopeVersionFact scope : scopes) {
            if (scope == null || !positive(scope.deliveryScopeId()) || !positive(scope.allocationVersion())
                    || previousId != null && scope.deliveryScopeId() <= previousId) {
                throw exception(ACC_ACCEPTANCE_SCOPE_REQUEST_INVALID);
            }
            previousId = scope.deliveryScopeId();
        }
    }

    private boolean validTenant(Long tenantId) {
        return tenantId != null && tenantId >= 0
                && Objects.equals(tenantId, TenantContextHolder.getTenantId());
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record BindingOutcome(boolean replayed, AcceptanceScopeBindingFact fact) {
    }
}
