package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeSliceDTO;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopePreviewCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.OrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.OrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
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
public class DeliveryScopeService {

    private final OrderLineMapper orderLineMapper;
    private final DeliveryScopeMapper deliveryScopeMapper;
    private final DeliveryScopeDetailMapper deliveryScopeDetailMapper;
    private final CommerceOutboxEventMapper outboxEventMapper;

    public List<DeliveryScopeSliceDTO> getAvailableSlices(Long parentProjectId, Long expectedScopeVersion) {
        if (parentProjectId == null) {
            return List.of();
        }
        List<DeliveryScopeDO> scopes = deliveryScopeMapper.selectActiveByProjectId(parentProjectId);
        long currentVersion = currentScopeVersion(scopes);
        if (expectedScopeVersion != null && expectedScopeVersion != currentVersion) {
            throw new IllegalStateException("COM_SCOPE_VERSION_CONFLICT");
        }
        if (scopes.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> available = allocatedByOrderLine(scopes);
        Map<Long, OrderLineDO> lines = orderLineMapper.selectBatchIds(available.keySet()).stream()
                .collect(Collectors.toMap(OrderLineDO::getId, Function.identity()));
        return available.entrySet().stream().map(entry -> lines.get(entry.getKey()))
                .filter(Objects::nonNull).filter(line -> "CONFIRMED".equals(line.getQuantityStatus()))
                .map(line -> new DeliveryScopeSliceDTO(line.getId(), available.get(line.getId()),
                        line.getUnitCode(), currentVersion, line.getQuantityStatus()))
                .filter(slice -> slice.availableQuantity().signum() > 0).toList();
    }

    public SplitScopeApplyResult previewSplit(SplitScopePreviewCommand command) {
        List<String> errors = validateCommand(command);
        if (!errors.isEmpty()) {
            return invalid(errors);
        }
        List<Long> orderLineIds = command.allocations().stream().map(SplitScopePreviewCommand.Allocation::orderLineId)
                .filter(Objects::nonNull).distinct().sorted().toList();
        Map<Long, OrderLineDO> lines = orderLineMapper.selectBatchIds(orderLineIds).stream()
                .collect(Collectors.toMap(OrderLineDO::getId, Function.identity()));
        List<DeliveryScopeDO> currentScopes = deliveryScopeMapper.selectActiveByProjectId(command.parentProjectId());
        validateVersion(command.expectedScopeVersion(), currentScopes, errors);
        validateParentQuantities(command.allocations(), lines, currentScopes, errors);
        if (!errors.isEmpty()) {
            return invalid(errors);
        }
        List<SplitScopeApplyResult.AppliedScope> preview = command.allocations().stream()
                .map(item -> new SplitScopeApplyResult.AppliedScope(item.clientItemKey(), null, null)).toList();
        return new SplitScopeApplyResult(true, false, currentScopeVersion(currentScopes), preview, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public SplitScopeApplyResult applySplit(SplitScopeApplyCommand command) {
        List<String> errors = validateApplyCommand(command);
        if (!errors.isEmpty()) {
            return invalid(errors);
        }
        String replayEventId = eventId(command.tenantId(), command.idempotencyKey(), "RELEASE", 0);
        CommerceOutboxEventDO replay = outboxEventMapper.selectByEventId(replayEventId);
        if (replay != null) {
            return replayResult(command, replay);
        }
        List<Long> orderLineIds = command.allocations().stream().map(SplitScopeApplyCommand.Allocation::orderLineId)
                .distinct().sorted().toList();
        Map<Long, OrderLineDO> lockedLines = new LinkedHashMap<>();
        for (Long orderLineId : orderLineIds) {
            OrderLineDO line = orderLineMapper.selectByIdForUpdate(orderLineId);
            if (line != null) {
                lockedLines.put(orderLineId, line);
            }
        }
        List<DeliveryScopeDO> currentScopes = deliveryScopeMapper.selectActiveByProjectIdForUpdate(
                new DeliveryScopeProjectQuery(command.tenantId(), command.parentProjectId()));
        CommerceOutboxEventDO concurrentReplay = outboxEventMapper.selectByEventId(replayEventId);
        if (concurrentReplay != null) {
            return replayResult(command, concurrentReplay);
        }
        List<SplitScopePreviewCommand.Allocation> previewAllocations = command.allocations().stream()
                .map(item -> new SplitScopePreviewCommand.Allocation(item.clientItemKey(), item.orderLineId(),
                        item.quantity(), item.officeDepartmentCode(), item.serialNumbers())).toList();
        validateVersion(command.expectedScopeVersion(), currentScopes, errors);
        validateParentQuantities(previewAllocations, lockedLines, currentScopes, errors);
        for (SplitScopeApplyCommand.Allocation item : command.allocations()) {
            if (!command.projectIdsByClientItemKey().containsKey(item.clientItemKey())) {
                errors.add("PROJECT_NOT_RESOLVED:" + item.clientItemKey());
            }
        }
        if (command.projectIdsByClientItemKey().values().stream().anyMatch(Objects::isNull)
                || command.projectIdsByClientItemKey().values().stream().distinct().count()
                != command.projectIdsByClientItemKey().size()) {
            errors.add("PROJECT_MAPPING_INVALID");
        }
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
                DeliveryScopeDO remainder = insertScope(command.tenantId(), entry.getKey(), command.parentProjectId(),
                        remaining, newScopeVersion, command.idempotencyKey(), "REMAINDER", now);
                insertDetail(command.tenantId(), remainder.getId(), null, null, remaining);
            }
        }
        List<SplitScopeApplyResult.AppliedScope> applied = new ArrayList<>();
        for (SplitScopeApplyCommand.Allocation item : command.allocations()) {
            Long projectId = command.projectIdsByClientItemKey().get(item.clientItemKey());
            DeliveryScopeDO scope = insertScope(command.tenantId(), item.orderLineId(), projectId, item.quantity(),
                    newScopeVersion, command.idempotencyKey(), item.clientItemKey(), now);
            insertDetails(command.tenantId(), scope.getId(), item);
            insertAssignedOutbox(command, item, scope, newScopeVersion, eventIndex++, now);
            applied.add(new SplitScopeApplyResult.AppliedScope(item.clientItemKey(), projectId, scope.getId()));
        }
        return new SplitScopeApplyResult(true, false, newScopeVersion, List.copyOf(applied), List.of());
    }

    private List<String> validateCommand(SplitScopePreviewCommand command) {
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

    private List<String> validateApplyCommand(SplitScopeApplyCommand command) {
        if (command == null) {
            return new ArrayList<>(List.of("INVALID_COMMAND"));
        }
        List<SplitScopePreviewCommand.Allocation> allocations = command.allocations() == null ? List.of()
                : command.allocations().stream().map(item -> new SplitScopePreviewCommand.Allocation(
                        item.clientItemKey(), item.orderLineId(), item.quantity(), item.officeDepartmentCode(),
                        item.serialNumbers())).toList();
        List<String> errors = validateCommand(new SplitScopePreviewCommand(command.tenantId(),
                command.parentProjectId(), command.expectedScopeVersion(), allocations));
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128 || command.projectIdsByClientItemKey() == null) {
            errors.add("INVALID_APPLY_COMMAND");
        }
        if (command.projectIdsByClientItemKey() != null) {
            Set<String> allocationClientKeys = allocations.stream()
                    .map(SplitScopePreviewCommand.Allocation::clientItemKey).collect(Collectors.toSet());
            if (!command.projectIdsByClientItemKey().keySet().equals(allocationClientKeys)) {
                errors.add("PROJECT_MAPPING_INVALID");
            }
        }
        return errors;
    }

    private void validateTenant(Long tenantId, List<String> errors) {
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (contextTenantId != null && !Objects.equals(contextTenantId, tenantId)) {
            errors.add("TENANT_MISMATCH");
        }
    }

    private void validateAllocationShape(List<SplitScopePreviewCommand.Allocation> allocations, List<String> errors) {
        Set<String> serials = new HashSet<>();
        Set<String> dimensions = new HashSet<>();
        Set<String> allocationKeys = new HashSet<>();
        for (SplitScopePreviewCommand.Allocation item : allocations) {
            if (item.clientItemKey() == null || item.clientItemKey().isBlank()
                    || "REMAINDER".equals(item.clientItemKey()) || item.orderLineId() == null
                    || item.clientItemKey().length() > 64 || item.quantity() == null || item.quantity().signum() <= 0
                    || (item.officeDepartmentCode() != null && item.officeDepartmentCode().length() > 64)) {
                errors.add("INVALID_ALLOCATION");
                continue;
            }
            if (!allocationKeys.add(item.clientItemKey())) {
                errors.add("DUPLICATE_ALLOCATION:" + item.clientItemKey());
            }
            List<String> itemSerials = item.serialNumbers() == null ? List.of() : item.serialNumbers().stream()
                    .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).toList();
            if (!itemSerials.isEmpty() && item.quantity().compareTo(BigDecimal.valueOf(itemSerials.size())) != 0) {
                errors.add("SERIAL_QUANTITY_MISMATCH:" + item.clientItemKey());
            }
            for (String serial : itemSerials) {
                if (serial.length() > 128) {
                    errors.add("SERIAL_TOO_LONG:" + item.clientItemKey());
                    continue;
                }
                if (!serials.add(serial)) {
                    errors.add("DUPLICATE_SERIAL:" + serial);
                }
                if (!dimensions.add(item.orderLineId() + "|" + item.officeDepartmentCode() + "|" + serial)) {
                    errors.add("DUPLICATE_DIMENSION:" + serial);
                }
            }
        }
    }

    private void validateVersion(Long expectedVersion, List<DeliveryScopeDO> scopes, List<String> errors) {
        if (expectedVersion != null && expectedVersion != currentScopeVersion(scopes)) {
            errors.add("SCOPE_VERSION_CONFLICT");
        }
    }

    private void validateParentQuantities(List<SplitScopePreviewCommand.Allocation> allocations,
                                          Map<Long, OrderLineDO> lines, List<DeliveryScopeDO> parentScopes,
                                          List<String> errors) {
        Map<Long, BigDecimal> requested = allocations.stream().collect(Collectors.groupingBy(
                SplitScopePreviewCommand.Allocation::orderLineId,
                Collectors.reducing(BigDecimal.ZERO, SplitScopePreviewCommand.Allocation::quantity, BigDecimal::add)));
        Map<Long, BigDecimal> availableByLine = allocatedByOrderLine(parentScopes);
        for (Map.Entry<Long, BigDecimal> entry : requested.entrySet()) {
            OrderLineDO line = lines.get(entry.getKey());
            if (line == null || !"CONFIRMED".equals(line.getQuantityStatus())) {
                errors.add("ORDER_LINE_NOT_AVAILABLE:" + entry.getKey());
                continue;
            }
            boolean integerUnit = Set.of("EA", "PCS", "SET").contains(line.getUnitCode());
            boolean precisionInvalid = allocations.stream()
                    .filter(item -> Objects.equals(item.orderLineId(), entry.getKey()))
                    .anyMatch(item -> item.quantity().scale() > 6
                            || (integerUnit && item.quantity().stripTrailingZeros().scale() > 0));
            if (precisionInvalid) {
                errors.add("UNIT_PRECISION_INVALID:" + entry.getKey());
                continue;
            }
            BigDecimal available = availableByLine.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(available) > 0) {
                errors.add("OVER_ALLOCATION:" + entry.getKey());
            }
        }
    }

    private void insertDetails(Long tenantId, Long scopeId, SplitScopeApplyCommand.Allocation item) {
        List<String> serials = item.serialNumbers() == null ? List.of() : item.serialNumbers().stream()
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (serials.isEmpty()) {
            insertDetail(tenantId, scopeId, item.officeDepartmentCode(), null, item.quantity());
            return;
        }
        serials.forEach(serial -> insertDetail(tenantId, scopeId, item.officeDepartmentCode(), serial, BigDecimal.ONE));
    }

    private DeliveryScopeDO insertScope(Long tenantId, Long orderLineId, Long projectId, BigDecimal quantity,
                                        long scopeVersion, String idempotencyKey, String evidenceKey,
                                        LocalDateTime now) {
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setTenantId(tenantId);
        scope.setOrderLineId(orderLineId);
        scope.setProjectId(projectId);
        scope.setAllocatedQty(quantity);
        scope.setScopeStatus("ACTIVE");
        scope.setAllocationVersion(scopeVersion);
        scope.setSourceEvidence(evidencePrefix(idempotencyKey) + evidenceKey);
        scope.setEffectiveFrom(now);
        scope.setVersion(0);
        deliveryScopeMapper.insert(scope);
        return scope;
    }

    private void insertDetail(Long tenantId, Long scopeId, String officeDepartmentCode, String serial,
                              BigDecimal quantity) {
        DeliveryScopeDetailDO detail = new DeliveryScopeDetailDO();
        detail.setTenantId(tenantId);
        detail.setDeliveryScopeId(scopeId);
        detail.setOfficeDepartmentCode(officeDepartmentCode);
        detail.setSerialNo(serial);
        detail.setAllocatedQty(quantity);
        detail.setDetailStatus("ACTIVE");
        detail.setVersion(0);
        deliveryScopeDetailMapper.insert(detail);
    }

    private void insertAssignedOutbox(SplitScopeApplyCommand command, SplitScopeApplyCommand.Allocation item,
                                      DeliveryScopeDO scope, long scopeVersion, int index, LocalDateTime now) {
        CommerceOutboxEventDO event = new CommerceOutboxEventDO();
        event.setTenantId(command.tenantId());
        event.setEventId(eventId(command.tenantId(), command.idempotencyKey(), "ASSIGN", index));
        event.setEventType("DeliveryScopeAssigned");
        event.setAggregateType("DeliveryScope");
        event.setAggregateKey(String.valueOf(scope.getId()));
        event.setScopeVersion(scopeVersion);
        event.setPayload("{\"eventId\":\"" + event.getEventId() + "\",\"tenantId\":" + command.tenantId()
                + ",\"scopeVersion\":" + scopeVersion + ",\"occurredAt\":\"" + now
                + "\",\"orderLineId\":" + item.orderLineId() + ",\"projectId\":" + scope.getProjectId()
                + ",\"scopeId\":" + scope.getId() + ",\"allocatedQty\":\"" + item.quantity()
                + "\",\"dimensionDigest\":\"" + dimensionDigest(item) + "\"}");
        event.setStatus("PENDING");
        event.setOccurredAt(now);
        event.setRetryCount(0);
        outboxEventMapper.insert(event);
    }

    private void insertReleaseOutbox(SplitScopeApplyCommand command, DeliveryScopeDO scope,
                                     long scopeVersion, int index, LocalDateTime now) {
        CommerceOutboxEventDO event = new CommerceOutboxEventDO();
        event.setTenantId(command.tenantId());
        event.setEventId(eventId(command.tenantId(), command.idempotencyKey(), "RELEASE", index));
        event.setEventType("DeliveryScopeReleased");
        event.setAggregateType("DeliveryScope");
        event.setAggregateKey(String.valueOf(scope.getId()));
        event.setScopeVersion(scopeVersion);
        event.setPayload("{\"eventId\":\"" + event.getEventId() + "\",\"tenantId\":" + command.tenantId()
                + ",\"scopeVersion\":" + scopeVersion + ",\"occurredAt\":\"" + now
                + "\",\"orderLineId\":" + scope.getOrderLineId() + ",\"projectId\":" + scope.getProjectId()
                + ",\"scopeId\":" + scope.getId() + ",\"allocatedQty\":\"" + scope.getAllocatedQty()
                + "\",\"dimensionDigest\":\"RELEASED\"}");
        event.setStatus("PENDING");
        event.setOccurredAt(now);
        event.setRetryCount(0);
        outboxEventMapper.insert(event);
    }

    private Map<Long, BigDecimal> allocatedByOrderLine(List<DeliveryScopeDO> scopes) {
        return scopes.stream().collect(Collectors.groupingBy(DeliveryScopeDO::getOrderLineId,
                Collectors.reducing(BigDecimal.ZERO, DeliveryScopeDO::getAllocatedQty, BigDecimal::add)));
    }

    private long currentScopeVersion(List<DeliveryScopeDO> scopes) {
        return scopes.stream().map(DeliveryScopeDO::getAllocationVersion).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L);
    }

    private SplitScopeApplyResult invalid(List<String> errors) {
        return new SplitScopeApplyResult(false, false, null, List.of(), List.copyOf(errors));
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

    private String evidencePrefix(String idempotencyKey) {
        return "F-PROJ-002:" + idempotencyKey + ":";
    }

    private String eventId(Long tenantId, String idempotencyKey, String operation, int index) {
        String key = tenantId + ":" + idempotencyKey + ":" + operation + ":" + index;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String dimensionDigest(SplitScopeApplyCommand.Allocation item) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String serials = item.serialNumbers() == null ? "" : item.serialNumbers().stream()
                    .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                    .sorted().toList().toString();
            return HexFormat.of().formatHex(digest.digest((item.officeDepartmentCode() + "|" + serials)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
