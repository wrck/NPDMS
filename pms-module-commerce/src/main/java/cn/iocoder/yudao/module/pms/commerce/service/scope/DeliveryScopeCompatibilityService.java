package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.AssetDeviceScopeApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SerialScopeValidationResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeSliceDTO;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopePreviewCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineIdsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectQuery;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectOfficeFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFactQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryScopeCompatibilityService {

    private static final Set<String> INTEGER_UNITS = Set.of("EA", "PCS", "SET");

    private final SalesOrderLineMapper orderLineMapper;
    private final DeliveryScopeMapper deliveryScopeMapper;
    private final DeliveryScopeDetailMapper detailMapper;
    private final CommerceOutboxEventMapper outboxMapper;
    private final ProjectOfficeFactApi projectOfficeFactApi;
    private final AcceptanceStageBindingCoordinator acceptanceBindingCoordinator;
    private final AssetDeviceScopeApi assetDeviceScopeApi;

    public List<DeliveryScopeSliceDTO> getAvailableSlices(Long parentProjectId, Long expectedScopeVersion) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || parentProjectId == null) {
            return List.of();
        }
        List<DeliveryScopeDO> scopes = deliveryScopeMapper.selectActiveByProjectId(parentProjectId);
        long currentVersion = currentScopeVersion(scopes);
        if (expectedScopeVersion != null && !Objects.equals(expectedScopeVersion, currentVersion)) {
            throw new IllegalStateException("COM_SCOPE_VERSION_CONFLICT");
        }
        Map<Long, BigDecimal> available = allocatedByOrderLine(scopes);
        if (available.isEmpty()) {
            return List.of();
        }
        Map<Long, SalesOrderLineDO> lines = linesById(orderLineMapper.selectByIds(
                new SalesOrderLineIdsQuery(tenantId, sortedIds(available.keySet()))));
        return available.entrySet().stream()
                .map(entry -> lines.get(entry.getKey()))
                .filter(Objects::nonNull)
                .filter(line -> "CONFIRMED".equals(line.getQuantityStatus()))
                .map(line -> new DeliveryScopeSliceDTO(line.getId(), available.get(line.getId()),
                        line.getUnitCode(), currentVersion, line.getQuantityStatus()))
                .filter(slice -> slice.availableQuantity().signum() > 0)
                .toList();
    }

    public SplitScopeApplyResult previewSplit(SplitScopePreviewCommand command) {
        List<String> errors = validatePreviewShape(command);
        if (!errors.isEmpty()) {
            return invalid(errors);
        }
        List<Long> lineIds = command.allocations().stream()
                .map(SplitScopePreviewCommand.Allocation::orderLineId).distinct().sorted().toList();
        Map<Long, SalesOrderLineDO> lines = linesById(orderLineMapper.selectByIds(
                new SalesOrderLineIdsQuery(command.tenantId(), lineIds)));
        List<DeliveryScopeDO> scopes = deliveryScopeMapper.selectActiveByProjectId(command.parentProjectId());
        validateScopeVersion(command.expectedScopeVersion(), scopes, errors);
        validateQuantities(command.allocations(), lines, scopes, errors);
        validateNoSerialSubjects(command.allocations(), lines, errors);
        validateRemainderSubjects(command.allocations(), lines, scopes, errors);
        validatePreviewSerials(command, errors);
        if (!errors.isEmpty()) {
            return invalid(errors);
        }
        List<SplitScopeApplyResult.AppliedScope> preview = command.allocations().stream()
                .map(item -> new SplitScopeApplyResult.AppliedScope(item.clientItemKey(), null, null)).toList();
        return new SplitScopeApplyResult(true, false, currentScopeVersion(scopes), preview, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public SplitScopeApplyResult applySplit(SplitScopeApplyCommand command) {
        List<String> errors = validateApplyShape(command);
        if (!errors.isEmpty()) {
            return invalid(errors);
        }
        String replayEventId = eventId(command.tenantId(), command.idempotencyKey(), "RELEASE", 0);
        CommerceOutboxEventDO replay = outboxMapper.selectByEventId(replayEventId);
        if (replay != null) {
            return replayResult(command, replay);
        }

        Map<Long, ProjectOfficeFact> projectFacts = lockProjectFacts(command, errors);
        Map<Long, AcceptanceStageBindingCoordinator.StageContext> acceptanceStages =
                lockAcceptanceStageFacts(command, projectFacts, errors);
        validateWriteSerials(command, errors);
        if (!errors.isEmpty()) {
            return invalid(errors);
        }

        List<Long> lineIds = command.allocations().stream()
                .map(SplitScopeApplyCommand.Allocation::orderLineId).distinct().sorted().toList();
        Map<Long, SalesOrderLineDO> lockedLines = linesById(orderLineMapper.selectByIdsForUpdate(
                new SalesOrderLineIdsQuery(command.tenantId(), lineIds)));
        List<DeliveryScopeDO> currentScopes = deliveryScopeMapper.selectActiveByProjectIdForUpdate(
                new DeliveryScopeProjectQuery(command.tenantId(), command.parentProjectId()));
        CommerceOutboxEventDO concurrentReplay = outboxMapper.selectByEventId(replayEventId);
        if (concurrentReplay != null) {
            return replayResult(command, concurrentReplay);
        }

        List<SplitScopePreviewCommand.Allocation> previewAllocations = command.allocations().stream()
                .map(item -> new SplitScopePreviewCommand.Allocation(item.clientItemKey(), item.orderLineId(),
                        item.quantity(), item.officeDepartmentCode(), item.serialNumbers())).toList();
        validateScopeVersion(command.expectedScopeVersion(), currentScopes, errors);
        validateQuantities(previewAllocations, lockedLines, currentScopes, errors);
        validateNoSerialSubjects(previewAllocations, lockedLines, errors);
        validateRemainderSubjects(previewAllocations, lockedLines, currentScopes, errors);
        if (!errors.isEmpty()) {
            return invalid(errors);
        }

        long newScopeVersion = currentScopeVersion(currentScopes) + 1;
        LocalDateTime now = LocalDateTime.now();
        Map<Long, BigDecimal> requestedByLine = previewAllocations.stream().collect(Collectors.groupingBy(
                SplitScopePreviewCommand.Allocation::orderLineId,
                Collectors.reducing(BigDecimal.ZERO, SplitScopePreviewCommand.Allocation::quantity, BigDecimal::add)));
        Map<Long, BigDecimal> parentAvailable = allocatedByOrderLine(currentScopes);
        int eventIndex = 0;
        for (DeliveryScopeDO parentScope : currentScopes) {
            if (!requestedByLine.containsKey(parentScope.getOrderLineId())) {
                continue;
            }
            parentScope.setScopeStatus("RELEASED");
            parentScope.setEffectiveTo(now);
            deliveryScopeMapper.updateById(parentScope);
            insertReleaseOutbox(command, parentScope, newScopeVersion, eventIndex++, now);
        }
        for (Map.Entry<Long, BigDecimal> entry : requestedByLine.entrySet()) {
            BigDecimal remaining = parentAvailable.get(entry.getKey()).subtract(entry.getValue());
            if (remaining.signum() > 0) {
                SalesOrderLineDO line = lockedLines.get(entry.getKey());
                DeliveryScopeDO remainder = insertScope(command, line, projectFacts.get(command.parentProjectId()),
                        remaining, newScopeVersion, "REMAINDER", now);
                insertProductDetail(command.tenantId(), remainder.getId(), line.getProductCode(), remaining,
                        "REMAINDER");
                acceptanceBindingCoordinator.bindIfRequired(acceptanceStages.get(command.parentProjectId()),
                        remainder.getId(), remainder.getAllocationVersion(), command.idempotencyKey());
            }
        }
        List<SplitScopeApplyResult.AppliedScope> applied = new ArrayList<>();
        for (SplitScopeApplyCommand.Allocation item : command.allocations()) {
            Long projectId = command.projectIdsByClientItemKey().get(item.clientItemKey());
            SalesOrderLineDO line = lockedLines.get(item.orderLineId());
            DeliveryScopeDO scope = insertScope(command, line, projectFacts.get(projectId), item.quantity(),
                    newScopeVersion, item.clientItemKey(), now);
            insertDetails(command.tenantId(), scope.getId(), item, line.getProductCode());
            insertAssignedOutbox(command, item, scope, newScopeVersion, eventIndex++, now);
            acceptanceBindingCoordinator.bindIfRequired(acceptanceStages.get(projectId), scope.getId(),
                    scope.getAllocationVersion(), command.idempotencyKey());
            applied.add(new SplitScopeApplyResult.AppliedScope(item.clientItemKey(), projectId, scope.getId()));
        }
        return new SplitScopeApplyResult(true, false, newScopeVersion, List.copyOf(applied), List.of());
    }

    private Map<Long, ProjectOfficeFact> lockProjectFacts(SplitScopeApplyCommand command, List<String> errors) {
        Map<Long, Integer> versions = new HashMap<>();
        versions.put(command.parentProjectId(), command.expectedParentProjectVersion());
        command.projectIdsByClientItemKey().forEach((key, projectId) ->
                versions.put(projectId, command.projectVersionsByClientItemKey().get(key)));
        Map<Long, ProjectOfficeFact> facts = new LinkedHashMap<>();
        versions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ProjectOfficeFact fact = projectOfficeFactApi.lockAndRevalidate(
                    new ProjectOfficeFactQuery(command.tenantId(), entry.getKey(), entry.getValue()));
            if (!validProjectFact(fact, entry.getKey(), entry.getValue())) {
                errors.add("PROJECT_OFFICE_FACT_INVALID:" + entry.getKey());
            } else {
                facts.put(entry.getKey(), fact);
            }
        });
        return facts;
    }

    private boolean validProjectFact(ProjectOfficeFact fact, Long projectId, Integer projectVersion) {
        return fact != null && fact.outcome() == ProjectFactOutcome.FOUND
                && Objects.equals(fact.projectId(), projectId)
                && Objects.equals(fact.projectVersion(), projectVersion)
                && notBlank(fact.projectCode()) && fact.officeDepartmentId() != null
                && notBlank(fact.officeDepartmentCode()) && notBlank(fact.officeDepartmentName())
                && fact.officeDepartmentVersion() != null && fact.officeDepartmentVersion() >= 0;
    }

    private Map<Long, AcceptanceStageBindingCoordinator.StageContext> lockAcceptanceStageFacts(
            SplitScopeApplyCommand command, Map<Long, ProjectOfficeFact> projectFacts, List<String> errors) {
        Map<Long, AcceptanceStageBindingCoordinator.StageContext> facts = new LinkedHashMap<>();
        projectFacts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                facts.put(entry.getKey(), acceptanceBindingCoordinator.lockAndRead(command.tenantId(),
                        entry.getKey(), entry.getValue().projectVersion(), command.idempotencyKey()));
            } catch (RuntimeException exception) {
                errors.add("PROJECT_ACCEPTANCE_STAGE_FACT_INVALID:" + entry.getKey());
            }
        });
        return facts;
    }

    private void validatePreviewSerials(SplitScopePreviewCommand command, List<String> errors) {
        List<String> serials = serials(command.allocations());
        if (serials.isEmpty()) {
            return;
        }
        validateSerialResult(assetDeviceScopeApi.validateAssignableSerials(
                command.tenantId(), command.parentProjectId(), serials), errors);
    }

    private void validateWriteSerials(SplitScopeApplyCommand command, List<String> errors) {
        Map<Long, List<String>> byProject = command.allocations().stream()
                .filter(item -> !normalizedSerials(item.serialNumbers()).isEmpty())
                .collect(Collectors.groupingBy(
                        item -> command.projectIdsByClientItemKey().get(item.clientItemKey()),
                        Collectors.flatMapping(item -> normalizedSerials(item.serialNumbers()).stream(),
                                Collectors.toList())));
        byProject.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                validateSerialResult(assetDeviceScopeApi.validateAssignableSerials(
                        command.tenantId(), entry.getKey(), entry.getValue()), errors));
    }

    private void validateSerialResult(SerialScopeValidationResult result, List<String> errors) {
        if (result == null || !result.valid()
                || result.missingSerialNumbers() == null || !result.missingSerialNumbers().isEmpty()
                || result.unavailableSerialNumbers() == null || !result.unavailableSerialNumbers().isEmpty()
                || result.duplicateSerialNumbers() == null || !result.duplicateSerialNumbers().isEmpty()) {
            errors.add("AST_SERIAL_NOT_ASSIGNABLE");
        }
    }

    private List<String> validatePreviewShape(SplitScopePreviewCommand command) {
        List<String> errors = new ArrayList<>();
        if (command == null || command.tenantId() == null || command.parentProjectId() == null
                || command.allocations() == null || command.allocations().isEmpty()) {
            errors.add("INVALID_COMMAND");
            return errors;
        }
        validateTenant(command.tenantId(), errors);
        validateAllocationShape(command.allocations(), errors);
        return errors;
    }

    private List<String> validateApplyShape(SplitScopeApplyCommand command) {
        if (command == null) {
            return new ArrayList<>(List.of("INVALID_COMMAND"));
        }
        List<SplitScopePreviewCommand.Allocation> allocations = command.allocations() == null ? List.of()
                : command.allocations().stream().map(item -> new SplitScopePreviewCommand.Allocation(
                        item.clientItemKey(), item.orderLineId(), item.quantity(), item.officeDepartmentCode(),
                        item.serialNumbers())).toList();
        List<String> errors = validatePreviewShape(new SplitScopePreviewCommand(command.tenantId(),
                command.parentProjectId(), command.expectedScopeVersion(), allocations));
        if (command.expectedParentProjectVersion() == null || command.expectedParentProjectVersion() < 0
                || !notBlank(command.idempotencyKey()) || command.idempotencyKey().length() > 128
                || command.projectIdsByClientItemKey() == null
                || command.projectVersionsByClientItemKey() == null
                || command.projectVersionsByClientItemKey().isEmpty()) {
            errors.add("INVALID_APPLY_COMMAND");
            return errors;
        }
        Set<String> allocationKeys = allocations.stream()
                .map(SplitScopePreviewCommand.Allocation::clientItemKey).collect(Collectors.toSet());
        if (!command.projectIdsByClientItemKey().keySet().equals(allocationKeys)
                || !command.projectVersionsByClientItemKey().keySet().equals(allocationKeys)
                || command.projectIdsByClientItemKey().values().stream().anyMatch(Objects::isNull)
                || command.projectIdsByClientItemKey().containsValue(command.parentProjectId())
                || command.projectIdsByClientItemKey().values().stream().distinct().count()
                != command.projectIdsByClientItemKey().size()
                || command.projectVersionsByClientItemKey().values().stream()
                .anyMatch(version -> version == null || version < 0)) {
            errors.add("PROJECT_MAPPING_INVALID");
        }
        return errors;
    }

    private void validateAllocationShape(List<SplitScopePreviewCommand.Allocation> allocations,
                                         List<String> errors) {
        Set<String> keys = new HashSet<>();
        Set<String> serials = new HashSet<>();
        for (SplitScopePreviewCommand.Allocation item : allocations) {
            if (item.clientItemKey() == null || item.clientItemKey().isBlank()
                    || "REMAINDER".equals(item.clientItemKey()) || item.clientItemKey().length() > 64
                    || item.orderLineId() == null || item.quantity() == null || item.quantity().signum() <= 0) {
                errors.add("INVALID_ALLOCATION");
                continue;
            }
            if (!keys.add(item.clientItemKey())) {
                errors.add("DUPLICATE_ALLOCATION:" + item.clientItemKey());
            }
            List<String> itemSerials = normalizedSerials(item.serialNumbers());
            if (!itemSerials.isEmpty() && item.quantity().compareTo(BigDecimal.valueOf(itemSerials.size())) != 0) {
                errors.add("SERIAL_QUANTITY_MISMATCH:" + item.clientItemKey());
            }
            for (String serial : itemSerials) {
                if (serial.length() > 128) {
                    errors.add("SERIAL_TOO_LONG:" + item.clientItemKey());
                } else if (!serials.add(serial)) {
                    errors.add("DUPLICATE_SERIAL:" + serial);
                }
            }
        }
    }

    private void validateTenant(Long tenantId, List<String> errors) {
        Long trustedTenantId = TenantContextHolder.getTenantId();
        if (trustedTenantId != null && !Objects.equals(trustedTenantId, tenantId)) {
            errors.add("TENANT_MISMATCH");
        }
    }

    private void validateScopeVersion(Long expected, List<DeliveryScopeDO> scopes, List<String> errors) {
        if (expected != null && !Objects.equals(expected, currentScopeVersion(scopes))) {
            errors.add("SCOPE_VERSION_CONFLICT");
        }
    }

    private void validateQuantities(List<SplitScopePreviewCommand.Allocation> allocations,
                                    Map<Long, SalesOrderLineDO> lines, List<DeliveryScopeDO> scopes,
                                    List<String> errors) {
        Map<Long, BigDecimal> requested = allocations.stream().collect(Collectors.groupingBy(
                SplitScopePreviewCommand.Allocation::orderLineId,
                Collectors.reducing(BigDecimal.ZERO, SplitScopePreviewCommand.Allocation::quantity,
                        BigDecimal::add)));
        Map<Long, BigDecimal> available = allocatedByOrderLine(scopes);
        for (Map.Entry<Long, BigDecimal> entry : requested.entrySet()) {
            SalesOrderLineDO line = lines.get(entry.getKey());
            if (line == null || !"CONFIRMED".equals(line.getQuantityStatus())) {
                errors.add("ORDER_LINE_NOT_AVAILABLE:" + entry.getKey());
                continue;
            }
            boolean precisionInvalid = allocations.stream()
                    .filter(item -> Objects.equals(item.orderLineId(), entry.getKey()))
                    .anyMatch(item -> item.quantity().scale() > 6
                            || item.quantity().stripTrailingZeros().scale() > line.getUnitScale());
            if (precisionInvalid) {
                errors.add("UNIT_PRECISION_INVALID:" + entry.getKey());
            } else if (entry.getValue().compareTo(available.getOrDefault(entry.getKey(), BigDecimal.ZERO)) > 0) {
                errors.add("OVER_ALLOCATION:" + entry.getKey());
            }
        }
    }

    private void validateNoSerialSubjects(List<SplitScopePreviewCommand.Allocation> allocations,
                                          Map<Long, SalesOrderLineDO> lines, List<String> errors) {
        allocations.stream().filter(item -> normalizedSerials(item.serialNumbers()).isEmpty()).forEach(item -> {
            SalesOrderLineDO line = lines.get(item.orderLineId());
            if (line == null || !notBlank(line.getProductCode())) {
                errors.add("ERP_PRODUCT_CODE_REQUIRED:" + item.orderLineId());
            }
        });
    }

    private void validateRemainderSubjects(List<SplitScopePreviewCommand.Allocation> allocations,
                                           Map<Long, SalesOrderLineDO> lines,
                                           List<DeliveryScopeDO> scopes, List<String> errors) {
        Map<Long, BigDecimal> requested = allocations.stream().collect(Collectors.groupingBy(
                SplitScopePreviewCommand.Allocation::orderLineId,
                Collectors.reducing(BigDecimal.ZERO, SplitScopePreviewCommand.Allocation::quantity,
                        BigDecimal::add)));
        Map<Long, BigDecimal> available = allocatedByOrderLine(scopes);
        requested.forEach((lineId, quantity) -> {
            if (available.getOrDefault(lineId, BigDecimal.ZERO).compareTo(quantity) > 0) {
                SalesOrderLineDO line = lines.get(lineId);
                if (line == null || !notBlank(line.getProductCode())) {
                    errors.add("ERP_PRODUCT_CODE_REQUIRED_FOR_REMAINDER:" + lineId);
                }
            }
        });
    }

    private DeliveryScopeDO insertScope(SplitScopeApplyCommand command, SalesOrderLineDO line,
                                        ProjectOfficeFact project, BigDecimal quantity, long scopeVersion,
                                        String evidenceKey, LocalDateTime now) {
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setTenantId(command.tenantId());
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
        scope.setAllocationVersion(scopeVersion);
        scope.setAllocationSource("PROJECT_SPLIT");
        scope.setOfficeDepartmentId(project.officeDepartmentId());
        scope.setOfficeDepartmentCode(project.officeDepartmentCode());
        scope.setOfficeDepartmentName(project.officeDepartmentName());
        scope.setOfficeDepartmentVersion(project.officeDepartmentVersion());
        scope.setSourceEvidence(evidencePrefix(command.idempotencyKey()) + evidenceKey);
        scope.setEffectiveFrom(now);
        scope.setStatus("ENABLED");
        scope.setVersion(0);
        deliveryScopeMapper.insert(scope);
        return scope;
    }

    private void insertDetails(Long tenantId, Long scopeId, SplitScopeApplyCommand.Allocation allocation,
                               String productCode) {
        List<String> serials = normalizedSerials(allocation.serialNumbers());
        if (serials.isEmpty()) {
            insertProductDetail(tenantId, scopeId, productCode, allocation.quantity(),
                    allocation.clientItemKey());
            return;
        }
        int sequence = 1;
        for (String serial : serials) {
            DeliveryScopeDetailDO detail = detail(tenantId, scopeId, sequence++, BigDecimal.ONE,
                    allocation.clientItemKey());
            detail.setSerialNo(serial);
            detailMapper.insert(detail);
        }
    }

    private void insertProductDetail(Long tenantId, Long scopeId, String productCode, BigDecimal quantity,
                                     String sourceKey) {
        DeliveryScopeDetailDO detail = detail(tenantId, scopeId, 1, quantity, sourceKey);
        detail.setProductCode(productCode);
        detailMapper.insert(detail);
    }

    private DeliveryScopeDetailDO detail(Long tenantId, Long scopeId, int sequence, BigDecimal quantity,
                                         String sourceKey) {
        DeliveryScopeDetailDO detail = new DeliveryScopeDetailDO();
        detail.setTenantId(tenantId);
        detail.setDeliveryScopeId(scopeId);
        detail.setDetailSequence(sequence);
        detail.setSourceRecordKey(sourceKey);
        detail.setAllocatedQty(quantity);
        detail.setDetailStatus("ACTIVE");
        detail.setVersion(0);
        return detail;
    }

    private void insertAssignedOutbox(SplitScopeApplyCommand command, SplitScopeApplyCommand.Allocation item,
                                      DeliveryScopeDO scope, long version, int index, LocalDateTime now) {
        CommerceOutboxEventDO event = event(command, scope, version, "ASSIGN", index,
                "DeliveryScopeAssigned", now);
        event.setPayload("{\"tenantId\":" + command.tenantId() + ",\"scopeVersion\":" + version
                + ",\"orderLineId\":" + item.orderLineId() + ",\"projectId\":" + scope.getProjectId()
                + ",\"scopeId\":" + scope.getId() + ",\"allocatedQty\":\"" + item.quantity() + "\"}");
        outboxMapper.insert(event);
    }

    private void insertReleaseOutbox(SplitScopeApplyCommand command, DeliveryScopeDO scope,
                                     long version, int index, LocalDateTime now) {
        CommerceOutboxEventDO event = event(command, scope, version, "RELEASE", index,
                "DeliveryScopeReleased", now);
        event.setPayload("{\"tenantId\":" + command.tenantId() + ",\"scopeVersion\":" + version
                + ",\"orderLineId\":" + scope.getOrderLineId() + ",\"projectId\":" + scope.getProjectId()
                + ",\"scopeId\":" + scope.getId() + ",\"allocatedQty\":\"" + scope.getAllocatedQty() + "\"}");
        outboxMapper.insert(event);
    }

    private CommerceOutboxEventDO event(SplitScopeApplyCommand command, DeliveryScopeDO scope, long version,
                                        String operation, int index, String type, LocalDateTime now) {
        CommerceOutboxEventDO event = new CommerceOutboxEventDO();
        event.setTenantId(command.tenantId());
        event.setEventId(eventId(command.tenantId(), command.idempotencyKey(), operation, index));
        event.setEventType(type);
        event.setAggregateType("DeliveryScope");
        event.setAggregateKey(String.valueOf(scope.getId()));
        event.setScopeVersion(version);
        event.setStatus("PENDING");
        event.setOccurredAt(now);
        event.setRetryCount(0);
        return event;
    }

    private SplitScopeApplyResult replayResult(SplitScopeApplyCommand command, CommerceOutboxEventDO replay) {
        List<SplitScopeApplyResult.AppliedScope> scopes = deliveryScopeMapper
                .selectBySourceEvidencePrefix(command.tenantId(), evidencePrefix(command.idempotencyKey())).stream()
                .filter(scope -> scope.getSourceEvidence() != null
                        && !scope.getSourceEvidence().endsWith(":REMAINDER"))
                .map(scope -> new SplitScopeApplyResult.AppliedScope(
                        scope.getSourceEvidence().substring(evidencePrefix(command.idempotencyKey()).length()),
                        scope.getProjectId(), scope.getId()))
                .toList();
        return new SplitScopeApplyResult(true, true, replay.getScopeVersion(), scopes, List.of());
    }

    private Map<Long, SalesOrderLineDO> linesById(List<SalesOrderLineDO> lines) {
        return lines.stream().collect(Collectors.toMap(SalesOrderLineDO::getId, Function.identity()));
    }

    private Map<Long, BigDecimal> allocatedByOrderLine(List<DeliveryScopeDO> scopes) {
        return scopes.stream().collect(Collectors.groupingBy(DeliveryScopeDO::getOrderLineId,
                Collectors.reducing(BigDecimal.ZERO, DeliveryScopeDO::getAllocatedQty, BigDecimal::add)));
    }

    private long currentScopeVersion(List<DeliveryScopeDO> scopes) {
        return scopes.stream().map(DeliveryScopeDO::getAllocationVersion).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L);
    }

    private List<Long> sortedIds(Set<Long> ids) {
        return ids.stream().sorted().toList();
    }

    private List<String> serials(List<SplitScopePreviewCommand.Allocation> allocations) {
        return allocations.stream().flatMap(item -> normalizedSerials(item.serialNumbers()).stream())
                .sorted().toList();
    }

    private List<String> normalizedSerials(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).toList();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private SplitScopeApplyResult invalid(List<String> errors) {
        return new SplitScopeApplyResult(false, false, null, List.of(), List.copyOf(errors));
    }

    private String evidencePrefix(String idempotencyKey) {
        return "F-PROJ-002:" + idempotencyKey + ":";
    }

    private String eventId(Long tenantId, String idempotencyKey, String operation, int index) {
        String key = tenantId + ":" + idempotencyKey + ":" + operation + ":" + index;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
