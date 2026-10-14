package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectContractScopeApi;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.ContractProjectScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ContractProjectScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ContractScopeOrderLineRow;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeDetailIdsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderLineQuery;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeGuardApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardOutcome;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectOfficeFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFactQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.commerce.enums.ErrorCodeConstants.COMMERCE_SCOPE_STATE_CONFLICT;

/** 既有关联合同的订单行范围同步；只版本化根项目剩余范围，子项目范围由拆分服务管理。 */
@Service
@RequiredArgsConstructor
public class ProjectContractScopeService implements ProjectContractScopeApi {
    private final ProjectScopeApi projectScopeApi;
    private final ProjectOfficeFactApi projectOfficeFactApi;
    private final ContractAccessService contractAccessService;
    private final ContractProjectScopeMapper contractScopeMapper;
    private final DeliveryScopeMapper scopeMapper;
    private final DeliveryScopeDetailMapper detailMapper;
    private final DeliveryScopeProjectVersionService versionService;
    private final AcceptanceScopeGuardApi acceptanceGuardApi;
    private final AcceptanceStageBindingCoordinator acceptanceBindingCoordinator;
    private final CommerceOutboxEventMapper outboxMapper;
    private final OperationAuditApi auditApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final PermissionApi permissionApi;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void initialize(ScopeContext context) {
        validate(context);
        if (context.expectedScopeVersion() != 0L) throw conflict("INITIAL_SCOPE_NOT_EMPTY");
        apply(context, plan(context), "CONTRACT_INITIALIZED");
    }

    @Transactional(rollbackFor = Exception.class)
    public ScopePreview preview(ScopeContext context) {
        validate(context);
        return plan(context).preview();
    }

    @Transactional(rollbackFor = Exception.class)
    public ScopeSynchronizationResult synchronize(ScopeContext context, String sourceDigest) {
        validate(context);
        if (blank(sourceDigest)) throw conflict("CONTRACT_SCOPE_PREVIEW_REQUIRED");
        // 重放仍校验当前权限，但不要求已成功命令的旧版本继续等于当前版本。
        requireRootScope(context, projectScopeApi.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(context.tenantId())
                .subjectUserId(context.subjectUserId())
                .anchorProjectId(context.projectId())
                .actionCode(ProjectScopeApi.ACTION_MANAGE).build()));
        if (!permissionApi.hasAnyPermissions(context.subjectUserId(), "pms:commerce:scope:assign",
                "pms:commerce:scope:adjust", "pms:commerce:scope:release")) throw exception(FORBIDDEN);
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        context.tenantId(), "POST:project-contract-scopes:synchronize", context.subjectUserId(),
                        context.operationId()), digest(List.of(context, sourceDigest)), ScopeSynchronizationResult.class,
                () -> {
                    ScopePlan plan = plan(context);
                    if (!Objects.equals(sourceDigest, plan.preview().sourceDigest())) {
                        throw conflict("CONTRACT_SCOPE_SOURCE_CHANGED");
                    }
                    for (String type : plan.preview().lines().stream().map(ScopeLineChange::changeType).distinct().toList()) {
                        String permission = switch (type) {
                            case "ADD" -> "pms:commerce:scope:assign";
                            case "UPDATE" -> "pms:commerce:scope:adjust";
                            case "RELEASE" -> "pms:commerce:scope:release";
                            default -> null;
                        };
                        if (permission != null && !permissionApi.hasAnyPermissions(context.subjectUserId(), permission)) {
                            throw exception(FORBIDDEN);
                        }
                    }
                    return apply(context, plan, "CONTRACT_SYNCHRONIZED");
                }, result -> new PlatformCommandExecutionApi.SuccessFacts("COM_CONTRACT_SCOPE_SYNCHRONIZE",
                        "ProjectDeliveryScope", String.valueOf(context.projectId()), context.operationId(),
                        JsonUtils.toJsonString(result), null, null));
        return switch (execution.decision()) {
            case NEW, REPLAY_COMPLETED -> execution.response();
            case CONFLICT -> throw conflict("IDEMPOTENCY_PAYLOAD_CONFLICT");
            case IN_PROGRESS -> throw conflict("IDEMPOTENCY_IN_PROGRESS");
        };
    }

    private ScopePlan plan(ScopeContext context) {
        ProjectScopeResult tree = projectScopeApi.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(context.tenantId())
                .subjectUserId(context.subjectUserId())
                .anchorProjectId(context.projectId())
                .actionCode(ProjectScopeApi.ACTION_MANAGE)
                .expectedScopeVersion(context.expectedTreeVersion()).build());
        requireRootScope(context, tree);
        if (!Objects.equals(tree.treeVersion(), context.expectedTreeVersion())) {
            throw conflict("PROJECT_TREE_VERSION_CONFLICT");
        }
        ProjectOfficeFact project = projectOfficeFactApi.lockAndRevalidate(new ProjectOfficeFactQuery(
                context.tenantId(), context.projectId(), context.expectedProjectVersion()));
        if (project == null || project.outcome() != ProjectFactOutcome.FOUND
                || !Objects.equals(project.projectId(), context.projectId())
                || !Objects.equals(project.projectVersion(), context.expectedProjectVersion())
                || blank(project.projectCode()) || project.departmentId() == null
                || blank(project.departmentCode()) || blank(project.departmentName())
                || project.departmentVersion() == null || project.departmentVersion() < 0) {
            throw conflict("PROJECT_OFFICE_FACT_INVALID");
        }
        long scopeVersion = versionService.current(context.tenantId(), context.projectId());
        if (scopeVersion != context.expectedScopeVersion()) throw conflict("DELIVERY_SCOPE_PROJECT_VERSION_CONFLICT");
        var query = new ContractProjectScopeQuery(context.tenantId(), context.projectId());
        var contracts = contractScopeMapper.selectContractsForUpdate(query);
        List<String> errors = new ArrayList<>();
        if (contracts.isEmpty()) errors.add("PROJECT_CONTRACT_REQUIRED");
        for (var contract : contracts) {
            contractAccessService.getContractDetail(context.tenantId(), context.subjectUserId(),
                    context.operationId(), contract.getId());
        }
        var lines = contractScopeMapper.selectOrderLinesForUpdate(query);
        var current = lines.isEmpty() ? List.<DeliveryScopeDO>of()
                : scopeMapper.selectCurrentByOrderLineIdsForUpdate(new DeliveryScopeOrderLineQuery(
                        context.tenantId(), lines.stream().map(ContractScopeOrderLineRow::getId).toList()));
        var own = current.stream().filter(scope -> Objects.equals(scope.getProjectId(), context.projectId())).toList();
        var details = own.isEmpty() ? List.<DeliveryScopeDetailDO>of()
                : detailMapper.selectByScopeIds(new DeliveryScopeDetailIdsQuery(
                        context.tenantId(), own.stream().map(DeliveryScopeDO::getId).toList()));
        Map<Long, List<DeliveryScopeDetailDO>> detailsByScope = details.stream()
                .collect(Collectors.groupingBy(DeliveryScopeDetailDO::getDeliveryScopeId));
        List<PlannedLine> planned = new ArrayList<>();
        for (var line : lines) {
            var allocated = current.stream().filter(scope -> Objects.equals(scope.getOrderLineId(), line.getId())).toList();
            var rootScopes = allocated.stream().filter(scope -> Objects.equals(scope.getProjectId(), context.projectId())).toList();
            if (rootScopes.size() > 1) throw conflict("DELIVERY_SCOPE_CURRENT_CONFLICT");
            DeliveryScopeDO old = rootScopes.isEmpty() ? null : rootScopes.getFirst();
            var oldDetails = old == null ? List.<DeliveryScopeDetailDO>of()
                    : detailsByScope.getOrDefault(old.getId(), List.of());
            planned.add(planLine(context, line, allocated, old, oldDetails));
        }
        var changes = planned.stream().map(PlannedLine::change).toList();
        int changed = (int) changes.stream().filter(change -> !"NO_CHANGE".equals(change.changeType())).count();
        String sourceDigest = digest(List.of(project, tree.treeVersion(), scopeVersion, contracts, lines, current, details));
        var preview = new ScopePreview(context.projectId(), scopeVersion, sourceDigest, changes, List.copyOf(errors),
                changed, errors.isEmpty() && changes.stream().allMatch(change -> change.errors().isEmpty()));
        return new ScopePlan(project, List.copyOf(planned), preview);
    }

    private PlannedLine planLine(ScopeContext context, ContractScopeOrderLineRow line,
                                 List<DeliveryScopeDO> allocated, DeliveryScopeDO old,
                                 List<DeliveryScopeDetailDO> details) {
        List<String> errors = new ArrayList<>();
        if (!Boolean.TRUE.equals(line.getContractAssociated())) errors.add("CONTRACT_ORDER_RELATION_CHANGED");
        boolean retired = retired(line.getSourceLifecycleStatus()) || retired(line.getOrderLifecycleStatus());
        if (!"CONFIRMED".equals(line.getOrderAuthorityStatus()) || !"CONFIRMED".equals(line.getQuantityStatus())
                || blank(line.getSourceVersion()) || line.getOrderQty() == null || line.getOrderQty().signum() < 0
                || (!retired && (!"ACTIVE".equals(line.getSourceLifecycleStatus())
                    || !"ACTIVE".equals(line.getOrderLifecycleStatus()) || !"ENABLED".equals(line.getStatus())
                    || !"ENABLED".equals(line.getOrderStatus()) || !"0".equals(line.getOrderType())))) {
            errors.add("ORDER_LINE_AUTHORITY_INVALID");
        }
        BigDecimal elsewhere = BigDecimal.ZERO;
        for (var scope : allocated) {
            if (scope.getAllocatedQty() == null || scope.getAllocatedQty().signum() < 0) {
                throw conflict("DELIVERY_SCOPE_QUANTITY_INVALID");
            }
            if (!Objects.equals(scope.getProjectId(), context.projectId())) {
                elsewhere = elsewhere.add(scope.getAllocatedQty());
                if ("CONFLICT_FROZEN".equals(scope.getScopeStatus())) errors.add("CHILD_SCOPE_CONFLICT_FROZEN");
            }
        }
        BigDecimal effective = retired ? BigDecimal.ZERO : line.getOrderQty();
        BigDecimal proposed = effective == null ? null : effective.subtract(elsewhere);
        if (proposed != null && proposed.signum() < 0) errors.add("ALLOCATED_SCOPE_EXCEEDS_ORDER");
        if (proposed != null && proposed.signum() > 0
                && (line.getUnitScale() == null || proposed.stripTrailingZeros().scale() > line.getUnitScale()
                    || proposed.stripTrailingZeros().scale() > 6)) errors.add("UNIT_PRECISION_INVALID");
        if (proposed != null && proposed.signum() > 0 && blank(line.getProductCode())) errors.add("ORDER_PRODUCT_REQUIRED");
        BigDecimal previous = old == null ? BigDecimal.ZERO : old.getAllocatedQty();
        boolean productChanged = old != null && (!Objects.equals(old.getProductCode(), line.getProductCode())
                || !Objects.equals(old.getProductDesc(), line.getProductDesc()));
        boolean reduced = proposed != null && proposed.signum() >= 0 && proposed.compareTo(previous) < 0;
        if (old != null && (reduced || productChanged) && details.stream().anyMatch(detail -> !blank(detail.getSerialNo()))) {
            errors.add("SERIAL_SCOPE_REQUIRES_REVIEW");
        }
        if (old != null && reduced) {
            String guardError = reductionError(context, old, proposed);
            if (guardError != null) errors.add(guardError);
        }
        String changeType = proposed == null || proposed.signum() < 0 ? "CONFLICT"
                : proposed.compareTo(previous) == 0 && !productChanged
                    && (old == null || "ACTIVE".equals(old.getScopeStatus())) ? "NO_CHANGE"
                : proposed.signum() == 0 ? "RELEASE" : old == null ? "ADD" : "UPDATE";
        return new PlannedLine(line, old, details, new ScopeLineChange(line.getId(), line.getOrderNo(),
                line.getLineNo(), line.getProductCode(), line.getProductDesc(), line.getSourceVersion(),
                effective, elsewhere, previous, proposed, changeType, List.copyOf(errors)));
    }

    private ScopeSynchronizationResult apply(ScopeContext context, ScopePlan plan, String changeType) {
        if (!plan.preview().valid()) {
            List<String> errors = new ArrayList<>(plan.preview().errors());
            plan.preview().lines().forEach(line -> errors.addAll(line.errors()));
            throw conflict(String.join(",", errors.stream().distinct().toList()));
        }
        if (plan.preview().changedLineCount() == 0) {
            return new ScopeSynchronizationResult(context.projectId(), context.expectedScopeVersion(), 0);
        }
        var stage = acceptanceBindingCoordinator.lockAndRead(context.tenantId(), context.projectId(),
                context.expectedProjectVersion(), context.operationId());
        LocalDateTime now = LocalDateTime.now();
        var version = versionService.lock(context.tenantId(), context.projectId(), String.valueOf(context.subjectUserId()), now);
        if (!Objects.equals(version.getScopeVersion(), context.expectedScopeVersion())) {
            throw conflict("DELIVERY_SCOPE_PROJECT_VERSION_CONFLICT");
        }
        long next = version.getScopeVersion() + 1;
        for (PlannedLine item : plan.lines()) {
            if ("NO_CHANGE".equals(item.change().changeType())) continue;
            if (item.old() != null) {
                item.old().setScopeStatus("RELEASED");
                item.old().setEffectiveTo(now);
                if (scopeMapper.updateById(item.old()) != 1) throw conflict("DELIVERY_SCOPE_VERSION_CONFLICT");
                event(context, item.old(), "DeliveryScopeReleased", plan.preview().sourceDigest(), now);
            }
            DeliveryScopeDO effective = item.old();
            if (item.change().proposedQuantity().signum() > 0) {
                effective = insertScope(context, plan.project(), item, next, changeType, now);
                insertDetails(context, item, effective);
                acceptanceBindingCoordinator.bindIfRequired(stage, effective.getId(), next, context.operationId());
                event(context, effective, "DeliveryScopeAssigned", plan.preview().sourceDigest(), now);
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("projectId", context.projectId());
            detail.put("orderLineId", item.line().getId());
            detail.put("beforeQuantity", item.change().currentQuantity());
            detail.put("afterQuantity", item.change().proposedQuantity());
            detail.put("scopeVersion", next);
            detail.put("sourceVersion", item.line().getSourceVersion());
            detail.put("sourceDigest", plan.preview().sourceDigest());
            detail.put("officeFact", plan.project());
            auditApi.record(context.tenantId(), context.subjectUserId(), context.operationId(),
                    "COM_" + changeType, "DeliveryScope", String.valueOf(effective.getId()), "SUCCESS", detail);
        }
        versionService.advance(version, changeType, String.valueOf(context.subjectUserId()), now);
        return new ScopeSynchronizationResult(context.projectId(), next, plan.preview().changedLineCount());
    }

    private DeliveryScopeDO insertScope(ScopeContext context, ProjectOfficeFact project, PlannedLine item,
                                        long version, String source, LocalDateTime now) {
        var line = item.line();
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setTenantId(context.tenantId());
        scope.setProjectId(project.projectId());
        scope.setProjectCode(project.projectCode());
        scope.setOrderLineId(line.getId());
        scope.setOrderSourceSystem(line.getSourceSystem());
        scope.setOrderCompanyCode(line.getCompanyCode());
        scope.setOrderCompanyName(line.getCompanyName());
        scope.setOrderType(line.getOrderType());
        scope.setOrderNo(line.getOrderNo());
        scope.setLineNo(line.getLineNo());
        scope.setProductCode(line.getProductCode());
        scope.setProductDesc(line.getProductDesc());
        scope.setAllocatedQty(item.change().proposedQuantity());
        scope.setScopeStatus("ACTIVE");
        scope.setAllocationVersion(version);
        scope.setAllocationSource(source);
        scope.setChangeReason("既有关联合同订单同步");
        scope.setDepartmentId(project.departmentId());
        scope.setDepartmentCode(project.departmentCode());
        scope.setDepartmentName(project.departmentName());
        scope.setDepartmentVersion(project.departmentVersion());
        scope.setSourceEvidence(context.operationId());
        scope.setEffectiveFrom(now);
        scope.setStatus("ENABLED");
        scope.setVersion(0L);
        scope.setCreator(String.valueOf(context.subjectUserId()));
        scopeMapper.insert(scope);
        return scope;
    }

    private void insertDetails(ScopeContext context, PlannedLine item, DeliveryScopeDO scope) {
        BigDecimal remaining = scope.getAllocatedQty();
        int sequence = 1;
        for (var old : item.details()) {
            if (blank(old.getSerialNo())) continue;
            DeliveryScopeDetailDO detail = new DeliveryScopeDetailDO();
            BeanUtils.copyProperties(old, detail, "id", "creator", "updater", "createTime", "updateTime");
            detail.setDeliveryScopeId(scope.getId());
            detail.setDetailSequence(sequence++);
            detail.setSourceRecordKey(context.operationId() + ':' + item.line().getId() + ':' + detail.getDetailSequence());
            detail.setVersion(0);
            detail.setCreator(String.valueOf(context.subjectUserId()));
            detailMapper.insert(detail);
            remaining = remaining.subtract(old.getAllocatedQty());
        }
        if (remaining.signum() < 0) throw conflict("SERIAL_SCOPE_REQUIRES_REVIEW");
        if (remaining.signum() == 0) return;
        DeliveryScopeDetailDO detail = new DeliveryScopeDetailDO();
        detail.setTenantId(context.tenantId());
        detail.setDeliveryScopeId(scope.getId());
        detail.setDetailSequence(sequence);
        detail.setProductCode(scope.getProductCode());
        detail.setProductName(scope.getProductDesc());
        detail.setAllocatedQty(remaining);
        detail.setDetailStatus("ACTIVE");
        detail.setSourceRecordKey(context.operationId() + ':' + item.line().getId() + ':' + sequence);
        detail.setVersion(0);
        detail.setCreator(String.valueOf(context.subjectUserId()));
        detailMapper.insert(detail);
    }

    private void event(ScopeContext context, DeliveryScopeDO scope, String type, String sourceDigest, LocalDateTime now) {
        CommerceOutboxEventDO event = new CommerceOutboxEventDO();
        event.setTenantId(context.tenantId());
        event.setEventId(UUID.nameUUIDFromBytes((context.tenantId() + ":CONTRACT_SCOPE:" + context.operationId()
                + ':' + scope.getOrderLineId() + ':' + type).getBytes(StandardCharsets.UTF_8)).toString());
        event.setEventType(type);
        event.setAggregateType("DeliveryScope");
        event.setAggregateKey(String.valueOf(scope.getId()));
        event.setScopeVersion(scope.getAllocationVersion());
        event.setPayload(JsonUtils.toJsonString(Map.of("projectId", context.projectId(), "orderLineId", scope.getOrderLineId(),
                "deliveryScopeId", scope.getId(), "allocationVersion", scope.getAllocationVersion(), "sourceDigest", sourceDigest)));
        event.setStatus("PENDING");
        event.setOccurredAt(now);
        event.setRetryCount(0);
        outboxMapper.insert(event);
    }

    private String reductionError(ScopeContext context, DeliveryScopeDO old, BigDecimal proposed) {
        try {
            var guard = acceptanceGuardApi.checkReduction(new AcceptanceScopeGuardQuery(context.tenantId(),
                    context.projectId(), old.getId(), old.getAllocationVersion(), proposed, context.operationId()));
            if (guard == null || !Objects.equals(guard.deliveryScopeId(), old.getId())
                    || !Objects.equals(guard.scopeAllocationVersion(), old.getAllocationVersion())) return "ACCEPTANCE_SCOPE_UNKNOWN";
            if (guard.outcome() == AcceptanceScopeGuardOutcome.LOCKED) return "ACCEPTANCE_SCOPE_LOCKED";
            return guard.outcome() == AcceptanceScopeGuardOutcome.UNLOCKED ? null : "ACCEPTANCE_SCOPE_UNKNOWN";
        } catch (RuntimeException ignored) {
            return "ACCEPTANCE_SCOPE_UNKNOWN";
        }
    }

    private void requireRootScope(ScopeContext context, ProjectScopeResult scope) {
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(context.projectId())) {
            throw conflict("PROJECT_SCOPE_FORBIDDEN");
        }
        if (!Objects.equals(scope.rootProjectId(), context.projectId())) throw conflict("ROOT_PROJECT_REQUIRED");
    }

    private void validate(ScopeContext context) {
        if (context == null || context.tenantId() == null || context.tenantId() < 0
                || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                || context.projectId() == null || context.projectId() <= 0
                || context.subjectUserId() == null || context.subjectUserId() <= 0
                || context.expectedProjectVersion() == null || context.expectedProjectVersion() < 0
                || context.expectedTreeVersion() == null || context.expectedTreeVersion() < 0
                || context.expectedScopeVersion() == null || context.expectedScopeVersion() < 0
                || blank(context.operationId()) || context.operationId().length() > 128) {
            throw conflict("CONTRACT_SCOPE_COMMAND_INVALID");
        }
    }

    private boolean retired(String value) { return "CANCELLED".equals(value) || "RETURNED".equals(value); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ServiceException conflict(String reason) { return exception(COMMERCE_SCOPE_STATE_CONFLICT, reason); }

    private String digest(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record PlannedLine(ContractScopeOrderLineRow line, DeliveryScopeDO old,
                               List<DeliveryScopeDetailDO> details, ScopeLineChange change) { }
    private record ScopePlan(ProjectOfficeFact project, List<PlannedLine> lines, ScopePreview preview) { }
}
