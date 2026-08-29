package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.AssetDeviceScopeApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SerialScopeValidationResult;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineIdsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeIdQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderLineQuery;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeGuardApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardOutcome;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardResult;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectOfficeFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFactQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceDeliveryScopeCommandService {

    private final ProjectScopeApi projectScopeApi;
    private final ProjectOfficeFactApi projectOfficeFactApi;
    private final AcceptanceStageBindingCoordinator acceptanceBindingCoordinator;
    private final AssetDeviceScopeApi assetDeviceScopeApi;
    private final AcceptanceScopeGuardApi acceptanceScopeGuardApi;
    private final SalesOrderLineMapper orderLineMapper;
    private final DeliveryScopeMapper scopeMapper;
    private final DeliveryScopeDetailMapper detailMapper;
    private final CommerceOutboxEventMapper outboxMapper;
    private final OperationAuditApi operationAuditApi;

    @Transactional(rollbackFor = Exception.class)
    public DeliveryScopePreviewResult preview(DeliveryScopePreviewCommand command) {
        validatePreview(command);
        ProjectOfficeFact project = lockProject(command.tenantId(), command.subjectUserId(), command.projectId(),
                command.expectedProjectVersion(), command.expectedProjectScopeVersion());
        SalesOrderLineDO line = lockLine(command.tenantId(), command.orderLineId(),
                command.expectedOrderLineSourceVersion());
        List<DeliveryScopeDO> current = lockCurrentByLine(command.tenantId(), line.getId());
        BigDecimal allocated = current.stream().map(DeliveryScopeDO::getAllocatedQty)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = line.getOrderQty().subtract(allocated);
        List<String> errors = new ArrayList<>();
        if (current.stream().anyMatch(scope -> "CONFLICT_FROZEN".equals(scope.getScopeStatus()))) {
            errors.add("DELIVERY_SCOPE_CONFLICT_FROZEN");
        }
        if (current.stream().anyMatch(scope -> Objects.equals(scope.getProjectId(), command.projectId()))) {
            errors.add("DELIVERY_SCOPE_CURRENT_CONFLICT");
        }
        if (command.proposedQuantity().scale() > 6
                || command.proposedQuantity().stripTrailingZeros().scale() > line.getUnitScale()) {
            errors.add("UNIT_PRECISION_INVALID");
        }
        if (command.proposedQuantity().compareTo(available) > 0) {
            errors.add("OVER_ALLOCATION");
        }
        validatePreviewSubject(command, line, errors);
        List<DeliveryScopePreviewResult.OccupiedScope> occupied = current.stream()
                .map(scope -> new DeliveryScopePreviewResult.OccupiedScope(scope.getId(), scope.getProjectId(),
                        scope.getAllocatedQty(), scope.getAllocationVersion(), scope.getScopeStatus()))
                .toList();
        return new DeliveryScopePreviewResult(project.projectId(), project.projectVersion(), project.projectCode(),
                project.officeDepartmentId(), project.officeDepartmentCode(), project.officeDepartmentName(),
                project.officeDepartmentVersion(), line.getId(), line.getSourceVersion(), line.getOrderQty(),
                allocated, available, command.proposedQuantity(), errors.isEmpty(), List.copyOf(errors), occupied);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeliveryScopeCommandResult assign(DeliveryScopeAssignCommand command) {
        validateAssign(command);
        String requestKey = assignRequestKey(command);
        DeliveryScopeCommandResult replay = replay(command.tenantId(), command.operationId(), "ASSIGN", requestKey);
        if (replay != null) {
            return replay;
        }
        ProjectOfficeFact project = lockProject(command.tenantId(), command.subjectUserId(), command.projectId(),
                command.expectedProjectVersion(), command.expectedProjectScopeVersion());
        AcceptanceStageBindingCoordinator.StageContext acceptanceStage = acceptanceBindingCoordinator.lockAndRead(
                command.tenantId(), command.projectId(), command.expectedProjectVersion(), command.operationId());
        SalesOrderLineDO line = lockLine(command.tenantId(), command.orderLineId(),
                command.expectedOrderLineSourceVersion());
        validateSubject(command.tenantId(), command.projectId(), command.allocatedQuantity(),
                command.serialNumbers(), line);
        List<DeliveryScopeDO> current = lockCurrentByLine(command.tenantId(), line.getId());
        if (current.stream().anyMatch(scope -> "CONFLICT_FROZEN".equals(scope.getScopeStatus()))
                || current.stream().anyMatch(scope -> Objects.equals(scope.getProjectId(), command.projectId()))) {
            throw conflict("DELIVERY_SCOPE_CURRENT_CONFLICT");
        }
        validateTotal(line, current, null, command.allocatedQuantity());
        long version = current.stream().map(DeliveryScopeDO::getAllocationVersion).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L) + 1;
        LocalDateTime now = LocalDateTime.now();
        DeliveryScopeDO created = insertScope(command.tenantId(), line, project, command.allocatedQuantity(),
                version, "DIRECT_ASSIGN", command.reason(), command.operationId(), now);
        insertDetails(command.tenantId(), created.getId(), command.allocatedQuantity(),
                command.serialNumbers(), line.getProductCode(), command.operationId());
        insertEvent(command.tenantId(), command.operationId(), "ASSIGN", "DeliveryScopeAssigned",
                created, requestKey, now);
        acceptanceBindingCoordinator.bindIfRequired(acceptanceStage, created.getId(),
                created.getAllocationVersion(), command.operationId());
        audit(command.tenantId(), command.subjectUserId(), command.operationId(), "COM_SCOPE_ASSIGN",
                created, BigDecimal.ZERO, command.allocatedQuantity(), command.reason());
        return new DeliveryScopeCommandResult(created.getId(), created.getAllocationVersion(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeliveryScopeCommandResult adjust(DeliveryScopeChangeCommand command) {
        validateChange(command, false);
        return change(command, "ADJUST");
    }

    @Transactional(rollbackFor = Exception.class)
    public DeliveryScopeCommandResult release(DeliveryScopeChangeCommand command) {
        validateChange(command, true);
        return change(command, "RELEASE");
    }

    private DeliveryScopeCommandResult change(DeliveryScopeChangeCommand command, String operation) {
        String requestKey = changeRequestKey(command, operation);
        DeliveryScopeCommandResult replay = replay(
                command.tenantId(), command.operationId(), operation, requestKey);
        if (replay != null) {
            return replay;
        }
        DeliveryScopeDO observed = scopeMapper.selectCurrentById(
                new DeliveryScopeIdQuery(command.tenantId(), command.deliveryScopeId()));
        if (observed == null || !Objects.equals(observed.getProjectId(), command.projectId())) {
            throw conflict("DELIVERY_SCOPE_NOT_FOUND");
        }
        ProjectOfficeFact project = lockProject(command.tenantId(), command.subjectUserId(), command.projectId(),
                command.expectedProjectVersion(), command.expectedProjectScopeVersion());
        AcceptanceStageBindingCoordinator.StageContext acceptanceStage = acceptanceBindingCoordinator.lockAndRead(
                command.tenantId(), command.projectId(), command.expectedProjectVersion(), command.operationId());
        SalesOrderLineDO line = lockLine(command.tenantId(), observed.getOrderLineId(),
                command.expectedOrderLineSourceVersion());
        List<DeliveryScopeDO> currentByLine = lockCurrentByLine(command.tenantId(), line.getId());
        DeliveryScopeDO current = scopeMapper.selectCurrentByIdForUpdate(
                new DeliveryScopeIdQuery(command.tenantId(), command.deliveryScopeId()));
        if (!validCurrent(current, observed, command)) {
            throw conflict("DELIVERY_SCOPE_VERSION_CONFLICT");
        }
        BigDecimal proposed = "RELEASE".equals(operation) ? BigDecimal.ZERO : command.proposedAllocatedQuantity();
        if (proposed.compareTo(current.getAllocatedQty()) > 0
                && currentByLine.stream().anyMatch(scope -> "CONFLICT_FROZEN".equals(scope.getScopeStatus()))) {
            throw conflict("DELIVERY_SCOPE_CONFLICT_FROZEN");
        }
        if (proposed.compareTo(current.getAllocatedQty()) < 0) {
            requireReductionUnlocked(command, current, proposed);
        }
        if (!"RELEASE".equals(operation)) {
            validateSubject(command.tenantId(), command.projectId(), proposed, command.serialNumbers(), line);
            validateTotal(line, currentByLine, current, proposed);
        }
        LocalDateTime now = LocalDateTime.now();
        current.setScopeStatus("RELEASED");
        current.setEffectiveTo(now);
        scopeMapper.updateById(current);
        if ("RELEASE".equals(operation)) {
            insertEvent(command.tenantId(), command.operationId(), operation, "DeliveryScopeReleased",
                    current, requestKey, now);
            audit(command.tenantId(), command.subjectUserId(), command.operationId(), "COM_SCOPE_RELEASE",
                    current, current.getAllocatedQty(), BigDecimal.ZERO, command.reason());
            return new DeliveryScopeCommandResult(current.getId(), current.getAllocationVersion(), false);
        }
        DeliveryScopeDO replacement = insertScope(command.tenantId(), line, project, proposed,
                current.getAllocationVersion() + 1, "DIRECT_ADJUST", command.reason(), command.operationId(), now);
        insertDetails(command.tenantId(), replacement.getId(), proposed, command.serialNumbers(),
                line.getProductCode(), command.operationId());
        insertEvent(command.tenantId(), command.operationId(), "ADJUST_RELEASE", "DeliveryScopeReleased",
                current, requestKey, now);
        insertEvent(command.tenantId(), command.operationId(), operation, "DeliveryScopeAssigned",
                replacement, requestKey, now);
        acceptanceBindingCoordinator.bindIfRequired(acceptanceStage, replacement.getId(),
                replacement.getAllocationVersion(), command.operationId());
        audit(command.tenantId(), command.subjectUserId(), command.operationId(), "COM_SCOPE_ADJUST",
                replacement, current.getAllocatedQty(), proposed, command.reason());
        return new DeliveryScopeCommandResult(replacement.getId(), replacement.getAllocationVersion(), false);
    }

    private ProjectOfficeFact lockProject(Long tenantId, Long subjectUserId, Long projectId,
                                          Integer projectVersion, Long projectScopeVersion) {
        ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                tenantId, subjectUserId, projectId, ProjectScopeApi.ACTION_MANAGE, projectScopeVersion));
        if (scope == null || !Objects.equals(scope.treeVersion(), projectScopeVersion)
                || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
            throw conflict("PROJECT_SCOPE_FORBIDDEN");
        }
        ProjectOfficeFact fact = projectOfficeFactApi.lockAndRevalidate(
                new ProjectOfficeFactQuery(tenantId, projectId, projectVersion));
        if (fact == null || fact.outcome() != ProjectFactOutcome.FOUND
                || !Objects.equals(fact.projectId(), projectId)
                || !Objects.equals(fact.projectVersion(), projectVersion)
                || blank(fact.projectCode()) || fact.officeDepartmentId() == null
                || blank(fact.officeDepartmentCode()) || blank(fact.officeDepartmentName())
                || fact.officeDepartmentVersion() == null || fact.officeDepartmentVersion() < 0) {
            throw conflict("PROJECT_OFFICE_FACT_INVALID");
        }
        return fact;
    }

    private SalesOrderLineDO lockLine(Long tenantId, Long orderLineId, String sourceVersion) {
        List<SalesOrderLineDO> lines = orderLineMapper.selectByIdsForUpdate(
                new SalesOrderLineIdsQuery(tenantId, List.of(orderLineId)));
        SalesOrderLineDO line = lines == null || lines.size() != 1 ? null : lines.getFirst();
        if (line == null || !Objects.equals(line.getId(), orderLineId)
                || !Objects.equals(line.getSourceVersion(), sourceVersion)
                || !"CONFIRMED".equals(line.getQuantityStatus()) || !"ENABLED".equals(line.getStatus())
                || line.getOrderQty() == null || line.getUnitScale() == null) {
            throw conflict("ORDER_LINE_AUTHORITY_INVALID");
        }
        return line;
    }

    private List<DeliveryScopeDO> lockCurrentByLine(Long tenantId, Long orderLineId) {
        List<DeliveryScopeDO> scopes = scopeMapper.selectCurrentByOrderLineIdsForUpdate(
                new DeliveryScopeOrderLineQuery(tenantId, List.of(orderLineId)));
        return scopes == null ? List.of() : scopes;
    }

    private void validateSubject(Long tenantId, Long projectId, BigDecimal quantity,
                                 List<String> requestedSerials, SalesOrderLineDO line) {
        List<String> serials = normalizeSerials(requestedSerials);
        if (serials.isEmpty()) {
            if (blank(line.getProductCode())) {
                throw conflict("ERP_PRODUCT_CODE_REQUIRED");
            }
            return;
        }
        if (quantity.compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw conflict("SERIAL_QUANTITY_MISMATCH");
        }
        SerialScopeValidationResult result = assetDeviceScopeApi.validateAssignableSerials(
                tenantId, projectId, serials);
        if (result == null || !result.valid() || result.missingSerialNumbers() == null
                || !result.missingSerialNumbers().isEmpty() || result.unavailableSerialNumbers() == null
                || !result.unavailableSerialNumbers().isEmpty() || result.duplicateSerialNumbers() == null
                || !result.duplicateSerialNumbers().isEmpty()) {
            throw conflict("AST_SERIAL_NOT_ASSIGNABLE");
        }
    }

    private void validatePreviewSubject(DeliveryScopePreviewCommand command, SalesOrderLineDO line,
                                        List<String> errors) {
        List<String> serials;
        try {
            serials = normalizeSerials(command.serialNumbers());
        } catch (RuntimeException exception) {
            errors.add("SERIAL_LIST_INVALID");
            return;
        }
        if (serials.isEmpty()) {
            if (blank(line.getProductCode())) {
                errors.add("ERP_PRODUCT_CODE_REQUIRED");
            }
            return;
        }
        if (command.proposedQuantity().compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            errors.add("SERIAL_QUANTITY_MISMATCH");
        }
        SerialScopeValidationResult result;
        try {
            result = assetDeviceScopeApi.validateAssignableSerials(
                    command.tenantId(), command.projectId(), serials);
        } catch (RuntimeException exception) {
            errors.add("AST_PROVIDER_UNAVAILABLE");
            return;
        }
        if (result == null || !result.valid() || result.missingSerialNumbers() == null
                || !result.missingSerialNumbers().isEmpty() || result.unavailableSerialNumbers() == null
                || !result.unavailableSerialNumbers().isEmpty() || result.duplicateSerialNumbers() == null
                || !result.duplicateSerialNumbers().isEmpty()) {
            errors.add("AST_SERIAL_NOT_ASSIGNABLE");
        }
    }

    private void validateTotal(SalesOrderLineDO line, List<DeliveryScopeDO> current,
                               DeliveryScopeDO replaced, BigDecimal proposed) {
        if (proposed.scale() > 6 || proposed.stripTrailingZeros().scale() > line.getUnitScale()) {
            throw conflict("UNIT_PRECISION_INVALID");
        }
        BigDecimal allocated = current.stream().map(DeliveryScopeDO::getAllocatedQty)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (replaced != null) {
            allocated = allocated.subtract(replaced.getAllocatedQty());
        }
        if (allocated.add(proposed).compareTo(line.getOrderQty()) > 0) {
            throw conflict("OVER_ALLOCATION");
        }
    }

    private void requireReductionUnlocked(DeliveryScopeChangeCommand command, DeliveryScopeDO current,
                                          BigDecimal proposed) {
        AcceptanceScopeGuardResult guard;
        try {
            guard = acceptanceScopeGuardApi.checkReduction(new AcceptanceScopeGuardQuery(
                    command.tenantId(), command.projectId(), current.getId(), current.getAllocationVersion(),
                    proposed, command.operationId()));
        } catch (RuntimeException exception) {
            throw conflict("ACCEPTANCE_SCOPE_UNKNOWN");
        }
        if (guard == null || guard.outcome() != AcceptanceScopeGuardOutcome.UNLOCKED
                || !Objects.equals(guard.deliveryScopeId(), current.getId())
                || !Objects.equals(guard.scopeAllocationVersion(), current.getAllocationVersion())) {
            throw conflict("ACCEPTANCE_SCOPE_LOCKED_OR_UNKNOWN");
        }
    }

    private boolean validCurrent(DeliveryScopeDO current, DeliveryScopeDO observed,
                                 DeliveryScopeChangeCommand command) {
        return current != null && Objects.equals(current.getId(), observed.getId())
                && Objects.equals(current.getProjectId(), command.projectId())
                && Objects.equals(current.getOrderLineId(), observed.getOrderLineId())
                && Objects.equals(current.getAllocationVersion(), command.expectedAllocationVersion())
                && ("ACTIVE".equals(current.getScopeStatus()) || "CONFLICT_FROZEN".equals(current.getScopeStatus()))
                && current.getEffectiveTo() == null && current.getAllocatedQty() != null;
    }

    private DeliveryScopeDO insertScope(Long tenantId, SalesOrderLineDO line, ProjectOfficeFact project,
                                        BigDecimal quantity, long version, String source,
                                        String reason, String operationId, LocalDateTime now) {
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setTenantId(tenantId);
        scope.setProjectId(project.projectId());
        scope.setProjectCode(project.projectCode());
        scope.setOrderLineId(line.getId());
        scope.setOrderSourceSystem(line.getSourceSystem());
        scope.setOrderCompanyCode(line.getCompanyCode());
        scope.setOrderCompanyName(line.getCompanyName());
        scope.setOrderType(line.getOrderType());
        scope.setOrderNo(line.getOrderNo());
        scope.setLineNo(line.getLineNo());
        scope.setItemCode(line.getItemCode());
        scope.setItemDesc(line.getItemDesc());
        scope.setAllocatedQty(quantity);
        scope.setScopeStatus("ACTIVE");
        scope.setAllocationVersion(version);
        scope.setAllocationSource(source);
        scope.setChangeReason(reason);
        scope.setOfficeDepartmentId(project.officeDepartmentId());
        scope.setOfficeDepartmentCode(project.officeDepartmentCode());
        scope.setOfficeDepartmentName(project.officeDepartmentName());
        scope.setOfficeDepartmentVersion(project.officeDepartmentVersion());
        scope.setSourceEvidence(operationId);
        scope.setEffectiveFrom(now);
        scope.setStatus("ENABLED");
        scope.setVersion(0);
        scopeMapper.insert(scope);
        return scope;
    }

    private void insertDetails(Long tenantId, Long scopeId, BigDecimal quantity, List<String> requestedSerials,
                               String productCode, String operationId) {
        List<String> serials = normalizeSerials(requestedSerials);
        if (serials.isEmpty()) {
            DeliveryScopeDetailDO detail = detail(tenantId, scopeId, 1, quantity, operationId);
            detail.setProductCode(productCode);
            detailMapper.insert(detail);
            return;
        }
        int sequence = 1;
        for (String serial : serials) {
            DeliveryScopeDetailDO detail = detail(tenantId, scopeId, sequence++, BigDecimal.ONE, operationId);
            detail.setSerialNo(serial);
            detailMapper.insert(detail);
        }
    }

    private DeliveryScopeDetailDO detail(Long tenantId, Long scopeId, int sequence, BigDecimal quantity,
                                         String operationId) {
        DeliveryScopeDetailDO detail = new DeliveryScopeDetailDO();
        detail.setTenantId(tenantId);
        detail.setDeliveryScopeId(scopeId);
        detail.setDetailSequence(sequence);
        detail.setSourceRecordKey(operationId + ':' + sequence);
        detail.setAllocatedQty(quantity);
        detail.setDetailStatus("ACTIVE");
        detail.setVersion(0);
        return detail;
    }

    private void insertEvent(Long tenantId, String operationId, String operation, String eventType,
                             DeliveryScopeDO scope, String requestKey, LocalDateTime now) {
        CommerceOutboxEventDO event = new CommerceOutboxEventDO();
        event.setTenantId(tenantId);
        event.setEventId(eventId(tenantId, operationId, operation));
        event.setEventType(eventType);
        event.setAggregateType("DeliveryScope");
        event.setAggregateKey(String.valueOf(scope.getId()));
        event.setScopeVersion(scope.getAllocationVersion());
        event.setPayload(JsonUtils.toJsonString(Map.of("requestKey", requestKey,
                "deliveryScopeId", scope.getId(), "allocationVersion", scope.getAllocationVersion(),
                "projectId", scope.getProjectId(), "orderLineId", scope.getOrderLineId())));
        event.setStatus("PENDING");
        event.setOccurredAt(now);
        event.setRetryCount(0);
        outboxMapper.insert(event);
    }

    private DeliveryScopeCommandResult replay(Long tenantId, String operationId, String operation,
                                              String requestKey) {
        CommerceOutboxEventDO event = outboxMapper.selectByEventId(eventId(tenantId, operationId, operation));
        if (event == null) {
            return null;
        }
        String storedKey = JsonUtils.parseObject(event.getPayload(), "requestKey", String.class);
        if (!Objects.equals(requestKey, storedKey)) {
            throw conflict("IDEMPOTENCY_PAYLOAD_CONFLICT");
        }
        return new DeliveryScopeCommandResult(Long.valueOf(event.getAggregateKey()), event.getScopeVersion(), true);
    }

    private void audit(Long tenantId, Long actorId, String operationId, String operation,
                       DeliveryScopeDO scope, BigDecimal before, BigDecimal after, String reason) {
        operationAuditApi.record(tenantId, actorId, operationId, operation, "DeliveryScope",
                String.valueOf(scope.getId()), "SUCCESS", Map.of("projectId", scope.getProjectId(),
                        "orderLineId", scope.getOrderLineId(), "allocationVersion", scope.getAllocationVersion(),
                        "beforeQuantity", before, "afterQuantity", after, "reason", reason));
    }

    private void validateAssign(DeliveryScopeAssignCommand command) {
        if (command == null || invalidIdentity(command.tenantId(), command.subjectUserId(), command.projectId(),
                command.expectedProjectVersion(), command.expectedProjectScopeVersion())
                || command.orderLineId() == null || command.orderLineId() <= 0
                || blank(command.expectedOrderLineSourceVersion()) || command.allocatedQuantity() == null
                || command.allocatedQuantity().signum() <= 0 || blank(command.reason())
                || blank(command.operationId()) || command.operationId().length() > 128) {
            throw conflict("DELIVERY_SCOPE_COMMAND_INVALID");
        }
    }

    private void validatePreview(DeliveryScopePreviewCommand command) {
        if (command == null || invalidIdentity(command.tenantId(), command.subjectUserId(), command.projectId(),
                command.expectedProjectVersion(), command.expectedProjectScopeVersion())
                || command.orderLineId() == null || command.orderLineId() <= 0
                || blank(command.expectedOrderLineSourceVersion()) || command.proposedQuantity() == null
                || command.proposedQuantity().signum() <= 0) {
            throw conflict("DELIVERY_SCOPE_PREVIEW_INVALID");
        }
    }

    private void validateChange(DeliveryScopeChangeCommand command, boolean release) {
        if (command == null || invalidIdentity(command.tenantId(), command.subjectUserId(), command.projectId(),
                command.expectedProjectVersion(), command.expectedProjectScopeVersion())
                || command.deliveryScopeId() == null || command.deliveryScopeId() <= 0
                || command.expectedAllocationVersion() == null || command.expectedAllocationVersion() <= 0
                || blank(command.expectedOrderLineSourceVersion()) || blank(command.reason())
                || blank(command.operationId()) || command.operationId().length() > 128
                || !release && (command.proposedAllocatedQuantity() == null
                || command.proposedAllocatedQuantity().signum() <= 0)) {
            throw conflict("DELIVERY_SCOPE_COMMAND_INVALID");
        }
    }

    private boolean invalidIdentity(Long tenantId, Long subjectUserId, Long projectId,
                                    Integer projectVersion, Long projectScopeVersion) {
        return tenantId == null || tenantId < 0 || subjectUserId == null || subjectUserId <= 0
                || projectId == null || projectId <= 0 || projectVersion == null || projectVersion < 0
                || projectScopeVersion == null || projectScopeVersion < 0
                || !Objects.equals(tenantId, TenantContextHolder.getTenantId());
    }

    private String assignRequestKey(DeliveryScopeAssignCommand command) {
        return command.subjectUserId() + "|" + command.projectId() + "|" + command.expectedProjectVersion() + "|"
                + command.expectedProjectScopeVersion() + "|" + command.orderLineId() + "|"
                + command.expectedOrderLineSourceVersion() + "|" + command.allocatedQuantity().toPlainString()
                + "|" + normalizeSerials(command.serialNumbers()) + "|" + command.reason();
    }

    private String changeRequestKey(DeliveryScopeChangeCommand command, String operation) {
        return command.subjectUserId() + "|" + operation + "|" + command.deliveryScopeId() + "|"
                + command.projectId() + "|"
                + command.expectedProjectVersion() + "|" + command.expectedProjectScopeVersion() + "|"
                + command.expectedAllocationVersion() + "|" + command.expectedOrderLineSourceVersion() + "|"
                + command.proposedAllocatedQuantity() + "|" + normalizeSerials(command.serialNumbers())
                + "|" + command.reason();
    }

    private List<String> normalizeSerials(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> normalized = values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).sorted().toList();
        if (normalized.stream().anyMatch(value -> value.length() > 128)
                || normalized.stream().distinct().count() != normalized.size()) {
            throw conflict("SERIAL_LIST_INVALID");
        }
        return normalized;
    }

    private String eventId(Long tenantId, String operationId, String operation) {
        return UUID.nameUUIDFromBytes((tenantId + ":DIRECT_SCOPE:" + operation + ':' + operationId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private IllegalStateException conflict(String code) {
        return new IllegalStateException(code);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
