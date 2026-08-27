package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class PreparationWaiverService {

    public static final String PERMISSION_APPROVE = "pms:preparation-survey:waiver-approve";
    private static final Set<String> ACTIONS = Set.of("CREATE", "SUBMIT", "APPROVE", "REJECT", "WITHDRAW");
    private static final Set<String> WAIVABLE_BLOCKERS = Set.of("EVIDENCE_REQUIRED", "FILE_FACT_CHANGED",
            "FILE_FACT_UNAVAILABLE", "SOURCE_PROVIDER_UNAVAILABLE", "SOURCE_NOT_SYNCED",
            "SOURCE_FACT_CHANGED", "SOURCE_RESULT_UNSATISFIED");

    private final PreparationMapper preparationMapper;
    private final PreparationItemMapper itemMapper;
    private final PreparationItemWaiverMapper waiverMapper;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final PermissionApi permissionApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final TransactionTemplate transactionTemplate;

    public WaiverPage page(Long preparationId, Long itemId, String cursor, Integer requestedPageSize,
            PreparationItemApplicationService.Actor actor) {
        if (preparationId == null || itemId == null || actor == null) throw exception(PREPARATION_COMMAND_INVALID);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), "pms:preparation-survey:query",
                PreparationInitializationService.PERMISSION_MANAGE, PERMISSION_APPROVE)) throw exception(FORBIDDEN);
        PreparationDO preparation = preparationMapper.selectById(new PreparationRowQuery(actor.tenantId(), preparationId));
        PreparationItemDO item = itemMapper.selectList(new PreparationChildrenQuery(actor.tenantId(), preparationId))
                .stream().filter(row -> Objects.equals(row.getId(), itemId)).findFirst().orElse(null);
        if (preparation == null || item == null) throw exception(PREPARATION_NOT_EXISTS);
        ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(),
                actor.actorId(), preparation.getProjectId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(preparation.getProjectId())) throw exception(FORBIDDEN);
        WaiverCursor parsed = parseCursor(cursor);
        int pageSize = requestedPageSize == null ? 20 : requestedPageSize;
        if (pageSize < 1 || pageSize > 100) throw exception(PREPARATION_COMMAND_INVALID);
        List<PreparationItemWaiverDO> fetched = waiverMapper.selectPage(new PreparationWaiverPageQuery(
                actor.tenantId(), preparation.getProjectId(), item.getItemCode(), parsed.waiverNo(),
                parsed.id(), pageSize + 1));
        boolean hasMore = fetched.size() > pageSize;
        boolean manager = canActAs(preparation.getProjectId(), actor,
                ProjectParticipantFactApi.ROLE_PROJECT_MANAGER,
                PreparationInitializationService.PERMISSION_MANAGE, ProjectScopeApi.ACTION_MANAGE);
        Map<String, Boolean> approvalRoles = new HashMap<>();
        List<WaiverView> rows = fetched.stream().limit(pageSize)
                .map(row -> view(row, preparation, actor, manager, approvalRoles)).toList();
        WaiverView last = rows.isEmpty() ? null : rows.getLast();
        return new WaiverPage(rows, hasMore && last != null ? last.waiverNo() + ":" + last.waiverId() : null, hasMore);
    }

    public WaiverResult execute(WaiverCommand command, PreparationItemApplicationService.Actor actor) {
        require(command, actor);
        try {
            return transactionTemplate.execute(status -> executeInTransaction(command, actor));
        } catch (RuntimeException failure) {
            auditRejected(command, actor, failure);
            throw failure;
        }
    }

    private WaiverResult executeInTransaction(WaiverCommand command, PreparationItemApplicationService.Actor actor) {
        PreparationDO located = preparationMapper.selectById(new PreparationRowQuery(actor.tenantId(), command.preparationId()));
        if (located == null) throw exception(PREPARATION_NOT_EXISTS);
        boolean decision = Set.of("APPROVE", "REJECT").contains(command.action());
        authorizeScope(located.getProjectId(), actor, decision);
        AtomicReference<AuditFacts> audit = new AtomicReference<>();
        String scope = "PREPARATION_WAIVER_" + command.action();
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), scope, actor.actorId(), command.idempotencyKey()),
                JsonUtils.toJsonString(command), WaiverResult.class,
                () -> executeOnce(command, actor, located.getProjectId(), decision, audit),
                result -> successFacts(scope, actor, result, audit.get()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PREPARATION_WAIVER_INVALID);
        }
        return execution.response();
    }

    private WaiverResult executeOnce(WaiverCommand command, PreparationItemApplicationService.Actor actor,
            Long projectId, boolean decision, AtomicReference<AuditFacts> audit) {
        PreparationDO preparation = preparationMapper.selectForUpdate(new PreparationRowQuery(actor.tenantId(), command.preparationId()));
        PreparationItemDO item = itemMapper.selectForUpdate(new PreparationItemRowQuery(
                actor.tenantId(), command.preparationId(), command.itemId()));
        if (preparation == null || item == null || !Integer.valueOf(1).equals(preparation.getCurrentMarker())
                || !Objects.equals(preparation.getProjectId(), projectId)
                || !Objects.equals(preparation.getVersion(), command.expectedPreparationVersion())
                || !Objects.equals(preparation.getInputVersion(), command.expectedInputVersion())
                || !Objects.equals(preparation.getReadinessVersion(), command.expectedReadinessVersion())
                || !Objects.equals(item.getVersion(), command.expectedItemVersion())) throw exception(PREPARATION_VERSION_NOT_MATCH);
        List<PreparationItemWaiverDO> businessRows = waiverMapper.selectBusinessListForUpdate(
                new PreparationWaiverBusinessQuery(actor.tenantId(), preparation.getProjectId(), Set.of(item.getItemCode())));
        PreparationItemWaiverDO existing = null;
        if (!"CREATE".equals(command.action())) {
            existing = businessRows.stream().filter(row -> Objects.equals(row.getId(), command.waiverId()))
                    .findFirst().orElseThrow(() -> exception(PREPARATION_WAIVER_INVALID));
            if (!Objects.equals(existing.getVersion(), command.expectedWaiverVersion())) {
                throw exception(PREPARATION_VERSION_NOT_MATCH);
            }
        }
        authorizeParticipant(preparation.getProjectId(), command.expectedProjectVersion(), actor,
                decision ? existing.getApprovalRoleCode() : ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
        return apply(command, actor, preparation, item, businessRows, audit);
    }

    private WaiverResult apply(WaiverCommand command, PreparationItemApplicationService.Actor actor,
            PreparationDO preparation, PreparationItemDO item, List<PreparationItemWaiverDO> rows,
            AtomicReference<AuditFacts> audit) {
        LocalDateTime now = LocalDateTime.now();
        if ("CREATE".equals(command.action())) return create(command, actor, preparation, item, rows, now, audit);
        PreparationItemWaiverDO waiver = rows.stream().filter(row -> Objects.equals(row.getId(), command.waiverId()))
                .findFirst().orElseThrow(() -> exception(PREPARATION_WAIVER_INVALID));
        String before = waiver.getStatusCode();
        String after;
        LocalDateTime submittedAt = waiver.getSubmittedAt(), decidedAt = waiver.getDecidedAt(), withdrawnAt = waiver.getWithdrawnAt();
        Long decidedBy = waiver.getDecidedBy();
        switch (command.action()) {
            case "SUBMIT" -> { requireApplicant(waiver, actor); requireStatus(before, "DRAFT"); after = "PENDING_APPROVAL"; submittedAt = now; }
            case "WITHDRAW" -> { requireApplicant(waiver, actor); if (!Set.of("DRAFT", "PENDING_APPROVAL").contains(before)) throw exception(PREPARATION_WAIVER_INVALID); after = "WITHDRAWN"; withdrawnAt = now; }
            case "APPROVE" -> { requireDecision(waiver, actor); requireStatus(before, "PENDING_APPROVAL"); requireNoOverlappingApproved(rows, waiver); after = "APPROVED"; decidedAt = now; decidedBy = actor.actorId(); }
            case "REJECT" -> { requireDecision(waiver, actor); requireStatus(before, "PENDING_APPROVAL"); after = "REJECTED"; decidedAt = now; decidedBy = actor.actorId(); }
            default -> throw exception(PREPARATION_COMMAND_INVALID);
        }
        if (waiverMapper.updateStatusIfMatch(new PreparationWaiverStatusUpdate(actor.tenantId(),
                waiver.getPreparationId(), waiver.getItemId(), waiver.getId(), waiver.getVersion(), before, after,
                submittedAt, decidedBy, decidedAt, command.opinion(), withdrawnAt,
                String.valueOf(actor.actorId()))) != 1) throw exception(PREPARATION_VERSION_NOT_MATCH);
        invalidate(preparation, actor);
        WaiverResult result = result(waiver, after, waiver.getVersion() + 1, preparation);
        audit.set(new AuditFacts(preparation.getProjectId(), item.getId(), waiver.getId(), before, after,
                waiver.getVersion(), waiver.getVersion() + 1, waiver.getBlockerCodesSnapshot(),
                waiver.getApprovalRoleCode(), waiver.getReason(), waiver.getRisk(), waiver.getCompensation(),
                waiver.getValidFrom(), waiver.getValidUntil(), command.opinion()));
        return result;
    }

    private WaiverResult create(WaiverCommand command, PreparationItemApplicationService.Actor actor,
            PreparationDO preparation, PreparationItemDO item, List<PreparationItemWaiverDO> rows,
            LocalDateTime now, AtomicReference<AuditFacts> audit) {
        WaiverPolicy policy = policy(item);
        if (!policy.allowed()) throw exception(PREPARATION_WAIVER_INVALID);
        if (command.blockerCodes() == null || command.blockerCodes().isEmpty()
                || !WAIVABLE_BLOCKERS.containsAll(command.blockerCodes()) || blank(command.reason())
                || blank(command.risk()) || blank(command.compensation()) || command.validFrom() == null
                || command.validUntil() == null || command.validUntil().isBefore(command.validFrom())) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
        if (rows.stream().anyMatch(row -> "PENDING_APPROVAL".equals(row.getStatusCode()))) throw exception(PREPARATION_WAIVER_INVALID);
        int waiverNo = rows.stream().map(PreparationItemWaiverDO::getWaiverNo).max(Integer::compareTo).orElse(0) + 1;
        PreparationItemWaiverDO row = new PreparationItemWaiverDO();
        row.setTenantId(actor.tenantId()); row.setProjectId(preparation.getProjectId()); row.setPreparationId(preparation.getId());
        row.setItemId(item.getId()); row.setItemCode(item.getItemCode()); row.setWaiverNo(waiverNo); row.setStatusCode("DRAFT");
        row.setBlockerCodesSnapshot(JsonUtils.toJsonString(command.blockerCodes().stream().distinct().sorted().toList()));
        row.setReason(command.reason().trim()); row.setRisk(command.risk().trim()); row.setCompensation(command.compensation().trim());
        row.setValidFrom(command.validFrom()); row.setValidUntil(command.validUntil()); row.setApprovalRoleCode(policy.approvalRoleCode());
        row.setApplicantUserId(actor.actorId()); row.setVersion(0); row.setCreator(String.valueOf(actor.actorId()));
        row.setUpdater(String.valueOf(actor.actorId())); row.setCreateTime(now); row.setUpdateTime(now);
        if (waiverMapper.insert(row) != 1 || row.getId() == null) throw new IllegalStateException("PREPARATION_WAIVER_INSERT_FAILED");
        invalidate(preparation, actor);
        WaiverResult result = result(row, "DRAFT", 0, preparation);
        audit.set(new AuditFacts(preparation.getProjectId(), item.getId(), row.getId(), "NONE", "DRAFT", -1, 0,
                row.getBlockerCodesSnapshot(), row.getApprovalRoleCode(), row.getReason(), row.getRisk(),
                row.getCompensation(), row.getValidFrom(), row.getValidUntil(), null));
        return result;
    }

    private void authorizeScope(Long projectId, PreparationItemApplicationService.Actor actor, boolean decision) {
        String permission = decision ? PERMISSION_APPROVE : PreparationInitializationService.PERMISSION_MANAGE;
        String action = decision ? ProjectScopeApi.ACTION_VIEW : ProjectScopeApi.ACTION_MANAGE;
        if (!permissionApi.hasAnyPermissions(actor.actorId(), permission)) throw exception(FORBIDDEN);
        ProjectScopeResult current = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, action));
        if (current == null || current.fullProjectIds() == null || !current.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
        projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(), actor.actorId(),
                projectId, action, current.treeVersion()));
    }

    private void authorizeParticipant(Long projectId, Integer projectVersion,
            PreparationItemApplicationService.Actor actor, String roleCode) {
        if (blank(roleCode)) throw exception(PREPARATION_WAIVER_INVALID);
        participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(projectId, actor.actorId(),
                projectVersion, "ACTIVE", null, Set.of(roleCode)));
    }

    private void invalidate(PreparationDO preparation, PreparationItemApplicationService.Actor actor) {
        if (preparationMapper.invalidateReadinessIfMatch(new PreparationInputInvalidationUpdate(actor.tenantId(),
                preparation.getId(), preparation.getVersion(), preparation.getInputVersion(),
                preparation.getReadinessVersion(), String.valueOf(actor.actorId()))) != 1) throw exception(PREPARATION_VERSION_NOT_MATCH);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(String scope,
            PreparationItemApplicationService.Actor actor, WaiverResult result, AuditFacts audit) {
        if (audit == null) throw new IllegalStateException("PREPARATION_WAIVER_AUDIT_MISSING");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", audit.projectId()); detail.put("itemId", audit.itemId()); detail.put("waiverId", audit.waiverId());
        detail.put("statusBefore", audit.statusBefore()); detail.put("statusAfter", audit.statusAfter());
        detail.put("versionBefore", audit.versionBefore()); detail.put("versionAfter", audit.versionAfter());
        detail.put("blockerCodes", audit.blockerCodes()); detail.put("approvalRoleCode", audit.approvalRoleCode());
        detail.put("reason", audit.reason()); detail.put("risk", audit.risk());
        detail.put("compensation", audit.compensation()); detail.put("opinion", audit.opinion());
        detail.put("validFrom", audit.validFrom()); detail.put("validUntil", audit.validUntil());
        return new PlatformCommandExecutionApi.SuccessFacts(scope, "PreparationWaiver", String.valueOf(result.waiverId()),
                actor.correlationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private void auditRejected(WaiverCommand command, PreparationItemApplicationService.Actor actor, RuntimeException failure) {
        if (actor == null || command == null) return;
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "PREPARATION_WAIVER_" + command.action(), "PreparationWaiver",
                command.waiverId() == null ? "NEW" : String.valueOf(command.waiverId()), "REJECTED", Map.of(
                        "preparationId", command.preparationId(), "itemId", command.itemId(),
                        "failureCode", failure instanceof ServiceException service ? String.valueOf(service.getCode()) : "PREPARATION_WAIVER_FAILED"));
    }

    private WaiverPolicy policy(PreparationItemDO item) {
        Map<?, ?> value = JsonUtils.parseObject(item.getWaiverPolicySnapshot(), Map.class);
        return new WaiverPolicy(Boolean.TRUE.equals(value == null ? null : value.get("allowed")),
                value == null || value.get("approvalRoleCode") == null ? null : String.valueOf(value.get("approvalRoleCode")));
    }

    private void requireDecision(PreparationItemWaiverDO waiver, PreparationItemApplicationService.Actor actor) {
        if (Objects.equals(waiver.getApplicantUserId(), actor.actorId())) throw exception(PREPARATION_WAIVER_INVALID);
    }
    private void requireApplicant(PreparationItemWaiverDO waiver, PreparationItemApplicationService.Actor actor) {
        if (!Objects.equals(waiver.getApplicantUserId(), actor.actorId())) throw exception(PREPARATION_WAIVER_INVALID);
    }
    private void requireStatus(String actual, String expected) { if (!expected.equals(actual)) throw exception(PREPARATION_WAIVER_INVALID); }
    private void requireNoOverlappingApproved(List<PreparationItemWaiverDO> rows, PreparationItemWaiverDO candidate) {
        if (rows.stream().anyMatch(row -> !Objects.equals(row.getId(), candidate.getId()) && "APPROVED".equals(row.getStatusCode())
                && !row.getValidUntil().isBefore(candidate.getValidFrom()) && !candidate.getValidUntil().isBefore(row.getValidFrom()))) {
            throw exception(PREPARATION_WAIVER_INVALID);
        }
    }

    private WaiverCursor parseCursor(String cursor) {
        if (blank(cursor)) return new WaiverCursor(null, null);
        String[] parts = cursor.split(":", -1);
        try {
            if (parts.length != 2) throw new NumberFormatException();
            int waiverNo = Integer.parseInt(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (waiverNo <= 0 || id <= 0) throw new NumberFormatException();
            return new WaiverCursor(waiverNo, id);
        } catch (NumberFormatException failure) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
    }

    private WaiverView view(PreparationItemWaiverDO row, PreparationDO preparation,
            PreparationItemApplicationService.Actor actor, boolean manager, Map<String, Boolean> approvalRoles) {
        List<String> actions = new ArrayList<>();
        boolean current = Integer.valueOf(1).equals(preparation.getCurrentMarker())
                && Objects.equals(row.getPreparationId(), preparation.getId());
        if (current && manager && Objects.equals(row.getApplicantUserId(), actor.actorId())) {
            if ("DRAFT".equals(row.getStatusCode())) actions.add("SUBMIT");
            if (Set.of("DRAFT", "PENDING_APPROVAL").contains(row.getStatusCode())) actions.add("WITHDRAW");
        }
        boolean approver = current && !Objects.equals(row.getApplicantUserId(), actor.actorId())
                && !blank(row.getApprovalRoleCode())
                && approvalRoles.computeIfAbsent(row.getApprovalRoleCode(), role -> canActAs(
                        preparation.getProjectId(), actor, role, PERMISSION_APPROVE, ProjectScopeApi.ACTION_VIEW));
        if (approver && "PENDING_APPROVAL".equals(row.getStatusCode())) {
            actions.add("APPROVE");
            actions.add("REJECT");
        }
        return new WaiverView(row.getId(), row.getPreparationId(), row.getItemId(), row.getItemCode(),
                row.getWaiverNo(), row.getStatusCode(), row.getBlockerCodesSnapshot(), row.getReason(), row.getRisk(),
                row.getCompensation(), row.getValidFrom(), row.getValidUntil(), row.getApprovalRoleCode(),
                row.getApplicantUserId(), row.getSubmittedAt(), row.getDecidedBy(), row.getDecidedAt(),
                row.getDecisionOpinion(), row.getWithdrawnAt(), row.getVersion(), actions);
    }

    private boolean canActAs(Long projectId, PreparationItemApplicationService.Actor actor, String roleCode,
            String permission, String scopeAction) {
        try {
            if (!permissionApi.hasAnyPermissions(actor.actorId(), permission)) return false;
            ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                    actor.tenantId(), actor.actorId(), projectId, scopeAction));
            if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) return false;
            ProjectParticipantFact fact = participantFactApi.inspect(new ProjectParticipantFactQuery(
                    projectId, actor.actorId(), Set.of(roleCode), LocalDateTime.now()));
            return fact != null && Objects.equals(fact.projectId(), projectId)
                    && Objects.equals(fact.userId(), actor.actorId()) && "ACTIVE".equals(fact.lifecycleStatus())
                    && fact.effectiveRoleCodes().contains(roleCode);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private WaiverResult result(PreparationItemWaiverDO row, String status, int version, PreparationDO preparation) {
        return new WaiverResult(row.getId(), row.getWaiverNo(), status, version,
                preparation.getVersion() + 1, preparation.getInputVersion() + 1);
    }

    private void require(WaiverCommand command, PreparationItemApplicationService.Actor actor) {
        if (command == null || actor == null || actor.tenantId() == null || actor.actorId() == null
                || !ACTIONS.contains(command.action()) || command.preparationId() == null || command.itemId() == null
                || command.expectedPreparationVersion() == null || command.expectedInputVersion() == null
                || command.expectedReadinessVersion() == null || command.expectedItemVersion() == null
                || command.expectedProjectVersion() == null || blank(command.idempotencyKey())
                || (!"CREATE".equals(command.action())
                    && (command.waiverId() == null || command.expectedWaiverVersion() == null))) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record WaiverCommand(String action, Long preparationId, Long itemId, Long waiverId,
            Integer expectedPreparationVersion, Integer expectedInputVersion, Integer expectedReadinessVersion,
            Integer expectedItemVersion, Integer expectedWaiverVersion, Integer expectedProjectVersion,
            List<String> blockerCodes, String reason, String risk, String compensation,
            LocalDateTime validFrom, LocalDateTime validUntil, String opinion, String idempotencyKey) {
        public WaiverCommand { blockerCodes = blockerCodes == null ? List.of() : List.copyOf(blockerCodes); }
    }
    public record WaiverResult(Long waiverId, Integer waiverNo, String status, Integer waiverVersion,
            Integer preparationVersion, Integer inputVersion) {}
    public record WaiverPage(List<WaiverView> items, String nextCursor, boolean hasMore) {}
    public record WaiverView(Long waiverId, Long preparationId, Long itemId, String itemCode, Integer waiverNo,
            String statusCode, String blockerCodesSnapshot, String reason, String risk, String compensation,
            LocalDateTime validFrom, LocalDateTime validUntil, String approvalRoleCode, Long applicantUserId,
            LocalDateTime submittedAt, Long decidedBy, LocalDateTime decidedAt, String decisionOpinion,
            LocalDateTime withdrawnAt, Integer version, List<String> allowedActions) {
        public WaiverView { allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions); }
    }
    private record WaiverCursor(Integer waiverNo, Long id) {}
    private record WaiverPolicy(boolean allowed, String approvalRoleCode) {}
    private record AuditFacts(Long projectId, Long itemId, Long waiverId, String statusBefore, String statusAfter,
            Integer versionBefore, Integer versionAfter, String blockerCodes, String approvalRoleCode,
            String reason, String risk, String compensation, LocalDateTime validFrom, LocalDateTime validUntil,
            String opinion) {}
}
