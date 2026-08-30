package cn.iocoder.yudao.module.pms.engineering.api.arrival;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalQuantityScopeFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalScopeWatermark;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalDifferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalLineDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalDifferenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalLineMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.projection.ArrivalProjectFactAllocation;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactAllocationQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalDifferenceScopeCodec;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalFactCalculator;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.OwnerFactVersionMismatchException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** F-IMP-002公开的EXE-01项目到货事实；生产Bean等待COM/AST正式Provider。 */
public class ArrivalAcceptanceFactApiImpl implements ArrivalAcceptanceFactApi {

    private static final String SOURCE_ACCEPTANCE = "ACCEPTANCE";
    private static final String SOURCE_DIFFERENCE = "DIFFERENCE";

    private final ArrivalAcceptanceMapper acceptanceMapper;
    private final ArrivalLineMapper lineMapper;
    private final ArrivalDifferenceMapper differenceMapper;
    private final DeliveryScopePort deliveryScopePort;
    private final DeviceScopeFactPort deviceScopeFactPort;
    private final ArrivalFactCalculator calculator = new ArrivalFactCalculator();
    private final Clock clock;

    public ArrivalAcceptanceFactApiImpl(ArrivalAcceptanceMapper acceptanceMapper,
                                        ArrivalLineMapper lineMapper,
                                        ArrivalDifferenceMapper differenceMapper,
                                        DeliveryScopePort deliveryScopePort,
                                        DeviceScopeFactPort deviceScopeFactPort) {
        this(acceptanceMapper, lineMapper, differenceMapper, deliveryScopePort,
                deviceScopeFactPort, Clock.systemDefaultZone());
    }

    ArrivalAcceptanceFactApiImpl(ArrivalAcceptanceMapper acceptanceMapper,
                                 ArrivalLineMapper lineMapper,
                                 ArrivalDifferenceMapper differenceMapper,
                                 DeliveryScopePort deliveryScopePort,
                                 DeviceScopeFactPort deviceScopeFactPort,
                                 Clock clock) {
        this.acceptanceMapper = acceptanceMapper;
        this.lineMapper = lineMapper;
        this.differenceMapper = differenceMapper;
        this.deliveryScopePort = deliveryScopePort;
        this.deviceScopeFactPort = deviceScopeFactPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ArrivalAcceptanceFact inspect(ArrivalAcceptanceFactQuery query) {
        requireTenant(query == null ? null : query.tenantId());
        CurrentScope scope = inspectScope(query.tenantId(), query.projectId(),
                query.deviceIds(), query.quantityScopes());
        return readFact(query.tenantId(), query.projectId(), scope, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArrivalAcceptanceFact lockAndRevalidate(ArrivalAcceptanceFactRevalidationQuery query) {
        requireTenant(query == null ? null : query.tenantId());
        CurrentScope inspected = inspectScope(query.tenantId(), query.projectId(),
                query.deviceIds(), query.quantityScopes());
        if (!Objects.equals(query.expectedScopeWatermark(), inspected.watermark())) {
            return stale(readFact(query.tenantId(), query.projectId(), inspected, false));
        }
        CurrentScope locked;
        try {
            locked = lockScope(query, inspected);
        } catch (OwnerFactVersionMismatchException ex) {
            CurrentScope current = inspectScope(query.tenantId(), query.projectId(),
                    query.deviceIds(), query.quantityScopes());
            return stale(readFact(query.tenantId(), query.projectId(), current, false));
        }
        ArrivalAcceptanceFact current = readFact(query.tenantId(), query.projectId(), locked, true);
        if (!Objects.equals(query.expectedFactVersion(), current.factVersion())
                || !Objects.equals(query.expectedScopeWatermark(), current.scopeWatermark())) {
            return stale(current);
        }
        return current;
    }

    private CurrentScope lockScope(ArrivalAcceptanceFactRevalidationQuery query, CurrentScope inspected) {
        DeliveryScopePort.AssignedScope delivery = deliveryScopePort.lockAndRevalidate(
                query.projectId(), query.expectedScopeWatermark().deliveryScopeVersion());
        ScopeComposition deliveryComposition = requireDeliveryScope(delivery, query.projectId());
        DeviceScopeFactPort.DeviceScopeFact currentDevices = requireDeviceScope(
                deviceScopeFactPort.resolveBySerials(query.tenantId(), query.projectId(),
                        deliveryComposition.serialNumbers()),
                query.projectId(), deliveryComposition.serialNumbers());
        Map<Long, DeviceScopeFactPort.DeviceFact> byId = indexDevices(currentDevices.devices());
        List<DeviceScopeFactPort.ExpectedDeviceFact> expected = query.deviceIds().stream()
                .sorted().map(deviceId -> {
                    DeviceScopeFactPort.DeviceFact device = byId.get(deviceId);
                    Long version = query.expectedScopeWatermark().deviceAssignmentVersions().get(deviceId);
                    if (device == null || version == null) {
                        throw new OwnerFactVersionMismatchException("arrival device scope version changed");
                    }
                    return new DeviceScopeFactPort.ExpectedDeviceFact(
                            device.deviceId(), device.serialNumber(), version);
                }).toList();
        DeviceScopeFactPort.DeviceScopeFact lockedDevices = requireDeviceScope(
                deviceScopeFactPort.lockAndRevalidate(query.tenantId(), query.projectId(), expected),
                query.projectId(), expected.stream().map(DeviceScopeFactPort.ExpectedDeviceFact::serialNumber)
                        .collect(java.util.stream.Collectors.toCollection(TreeSet::new)));
        CurrentScope locked = requestedScope(query.deviceIds(), query.quantityScopes(),
                delivery, deliveryComposition, lockedDevices);
        if (!Objects.equals(inspected.watermark(), locked.watermark())) {
            throw new OwnerFactVersionMismatchException("arrival scope changed while locking");
        }
        return locked;
    }

    private CurrentScope inspectScope(Long tenantId, Long projectId, Set<Long> deviceIds,
                                      List<ArrivalQuantityScopeFact> quantities) {
        DeliveryScopePort.AssignedScope delivery = deliveryScopePort.inspectAssignedScope(projectId);
        ScopeComposition composition = requireDeliveryScope(delivery, projectId);
        DeviceScopeFactPort.DeviceScopeFact devices = requireDeviceScope(
                deviceScopeFactPort.resolveBySerials(tenantId, projectId, composition.serialNumbers()),
                projectId, composition.serialNumbers());
        return requestedScope(deviceIds, quantities, delivery, composition, devices);
    }

    private CurrentScope requestedScope(Set<Long> requestedDeviceIds,
                                        List<ArrivalQuantityScopeFact> requestedQuantities,
                                        DeliveryScopePort.AssignedScope delivery,
                                        ScopeComposition composition,
                                        DeviceScopeFactPort.DeviceScopeFact devices) {
        Map<Long, DeviceScopeFactPort.DeviceFact> byId = indexDevices(devices.devices());
        if (!byId.keySet().containsAll(requestedDeviceIds)) {
            throw new IllegalStateException("requested device is outside current delivery scope");
        }
        for (ArrivalQuantityScopeFact requested : requestedQuantities) {
            BigDecimal assigned = composition.quantities().get(QuantityKey.from(requested));
            if (assigned == null || requested.quantity().compareTo(assigned) > 0) {
                throw new IllegalStateException("requested quantity is outside current delivery scope");
            }
        }
        TreeMap<Long, Long> versions = new TreeMap<>();
        requestedDeviceIds.forEach(deviceId -> versions.put(
                deviceId, byId.get(deviceId).projectAssignmentVersion()));
        List<ArrivalQuantityScopeFact> assignedQuantities = composition.quantities().entrySet().stream()
                .map(entry -> entry.getKey().scope(entry.getValue()))
                .toList();
        return new CurrentScope(Set.copyOf(requestedDeviceIds), List.copyOf(requestedQuantities),
                Set.copyOf(byId.keySet()), assignedQuantities,
                new ArrivalScopeWatermark(delivery.scopeVersion(), versions));
    }

    private ArrivalAcceptanceFact readFact(Long tenantId, Long projectId,
                                           CurrentScope scope, boolean locked) {
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        ArrivalProjectFactQuery factQuery = new ArrivalProjectFactQuery(tenantId, projectId, checkedAt);
        List<ArrivalLineDO> lines = locked
                ? lineMapper.selectConfirmedAcceptedByProjectForUpdate(factQuery)
                : lineMapper.selectConfirmedAcceptedByProject(factQuery);
        List<ArrivalAcceptanceDO> roots = locked
                ? acceptanceMapper.selectConfirmedByProjectForUpdate(factQuery)
                : acceptanceMapper.selectConfirmedByProject(factQuery);
        List<ArrivalDifferenceDO> differences = locked
                ? differenceMapper.selectEffectiveExemptionsByProjectForUpdate(factQuery)
                : differenceMapper.selectEffectiveExemptionsByProject(factQuery);
        requireLists(lines, differences, roots);
        FactVersion factVersion = factVersion(tenantId, projectId, roots, locked);
        Contributions allContributions = contributions(lines, differences, checkedAt);
        calculator.calculate(new ArrivalFactCalculator.CalculationInput(
                scope.assignedDeviceIds(), scope.assignedQuantities(),
                allContributions.acceptedDevices(), allContributions.acceptedQuantities(),
                allContributions.deviceExemptions(), allContributions.quantityExemptions(), checkedAt));
        Contributions contributions = projectContributions(scope, allContributions);
        ArrivalFactCalculator.CalculationResult calculated = calculator.calculate(
                new ArrivalFactCalculator.CalculationInput(scope.deviceIds(), scope.quantities(),
                        contributions.acceptedDevices(), contributions.acceptedQuantities(),
                        contributions.deviceExemptions(), contributions.quantityExemptions(), checkedAt));
        return new ArrivalAcceptanceFact(tenantId, projectId, calculated.sourceAcceptanceIds(),
                calculated.decision(), factVersion.version(), scope.watermark(), factVersion.reopened(),
                calculated.acceptedDeviceIds(), calculated.exemptedDeviceIds(),
                calculated.unmetDeviceIds(), calculated.acceptedQuantityScopes(),
                calculated.exemptedQuantityScopes(), calculated.unmetQuantityScopes());
    }

    private FactVersion factVersion(Long tenantId, Long projectId,
                                    List<ArrivalAcceptanceDO> roots, boolean locked) {
        for (ArrivalAcceptanceDO root : roots) {
            if (root == null || !Objects.equals(projectId, root.getProjectId())
                    || !"CONFIRMED".equals(root.getStatus()) || root.getProjectFactVersion() == null) {
                throw new IllegalStateException("confirmed arrival fact root is damaged");
            }
        }
        ArrivalProjectFactAllocationQuery query = new ArrivalProjectFactAllocationQuery(tenantId, projectId);
        List<ArrivalProjectFactAllocation> allocations = locked
                ? lockedAllocations(query)
                : acceptanceMapper.selectLatestProjectFactAllocations(query);
        if (allocations == null || allocations.isEmpty()) {
            if (!roots.isEmpty()) throw new IllegalStateException("arrival fact allocation source is missing");
            return new FactVersion(0L, false);
        }
        allocations.forEach(ArrivalAcceptanceFactApiImpl::requireAllocation);
        ArrivalProjectFactAllocation latest = allocations.getFirst();
        if (allocations.size() > 1
                && Objects.equals(latest.projectFactVersion(), allocations.get(1).projectFactVersion())) {
            throw new IllegalStateException("arrival fact allocation source is duplicated");
        }
        Set<Long> confirmedIds = roots.stream().map(ArrivalAcceptanceDO::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!confirmedIds.contains(latest.acceptanceId())) {
            throw new IllegalStateException("arrival fact allocation parent is unavailable");
        }
        if (SOURCE_DIFFERENCE.equals(latest.sourceType())) {
            return new FactVersion(latest.projectFactVersion(), true);
        }
        if (!SOURCE_ACCEPTANCE.equals(latest.sourceType())
                || latest.predecessorAcceptanceId() != null) {
            throw new IllegalStateException("arrival fact root reopen semantics are not provable");
        }
        return new FactVersion(latest.projectFactVersion(), false);
    }

    private List<ArrivalProjectFactAllocation> lockedAllocations(
            ArrivalProjectFactAllocationQuery query) {
        List<ArrivalProjectFactAllocation> roots = acceptanceMapper
                .selectLatestAllocatedRootsForUpdate(query);
        List<ArrivalProjectFactAllocation> differences = differenceMapper
                .selectLatestAllocatedDifferencesForUpdate(query);
        if (roots == null || differences == null) {
            throw new IllegalStateException("arrival fact allocation source is unavailable");
        }
        ArrayList<ArrivalProjectFactAllocation> combined = new ArrayList<>(roots.size() + differences.size());
        combined.addAll(roots);
        combined.addAll(differences);
        combined.forEach(ArrivalAcceptanceFactApiImpl::requireAllocation);
        combined.sort(Comparator.comparing(ArrivalProjectFactAllocation::projectFactVersion).reversed()
                .thenComparing(ArrivalProjectFactAllocation::sourceType)
                .thenComparing(ArrivalProjectFactAllocation::sourceId));
        return combined.size() <= 2 ? List.copyOf(combined) : List.copyOf(combined.subList(0, 2));
    }

    private static Contributions contributions(List<ArrivalLineDO> lines,
                                               List<ArrivalDifferenceDO> differences,
                                               LocalDateTime checkedAt) {
        List<ArrivalFactCalculator.DeviceContribution> acceptedDevices = new ArrayList<>();
        List<ArrivalFactCalculator.QuantityContribution> acceptedQuantities = new ArrayList<>();
        List<ArrivalFactCalculator.DeviceExemption> deviceExemptions = new ArrayList<>();
        List<ArrivalFactCalculator.QuantityExemption> quantityExemptions = new ArrayList<>();
        for (ArrivalLineDO line : lines) {
            if ("DEVICE".equals(line.getScopeType())) {
                acceptedDevices.add(new ArrivalFactCalculator.DeviceContribution(
                        line.getArrivalAcceptanceId(), line.getDeviceId()));
            } else if ("ORDER_MODEL_QUANTITY".equals(line.getScopeType())) {
                acceptedQuantities.add(new ArrivalFactCalculator.QuantityContribution(
                        line.getArrivalAcceptanceId(), quantityFact(line)));
            } else if (!"DEVICE".equals(line.getScopeType())) {
                throw new IllegalStateException("confirmed arrival line scope type is unsupported");
            }
        }
        Set<Long> acceptedDeviceIds = acceptedDevices.stream()
                .map(ArrivalFactCalculator.DeviceContribution::deviceId)
                .collect(java.util.stream.Collectors.toSet());
        for (ArrivalDifferenceDO difference : differences) {
            if (!isEffectiveExemption(difference, checkedAt)) {
                throw new IllegalStateException("effective exemption query returned invalid revision");
            }
            ArrivalDifferenceScopeCodec.Scope parsed = ArrivalDifferenceScopeCodec.parse(
                    difference.getScopeSnapshot());
            if (parsed instanceof ArrivalDifferenceScopeCodec.DeviceScope device) {
                if (!acceptedDeviceIds.contains(device.deviceId())) {
                    deviceExemptions.add(deviceExemption(difference, device.deviceId()));
                }
            } else if (parsed instanceof ArrivalDifferenceScopeCodec.QuantityScope quantity) {
                quantityExemptions.add(quantityExemption(difference, quantity));
            }
        }
        return new Contributions(List.copyOf(acceptedDevices), List.copyOf(acceptedQuantities),
                List.copyOf(deviceExemptions), List.copyOf(quantityExemptions));
    }

    private static Contributions projectContributions(CurrentScope scope, Contributions all) {
        List<ArrivalFactCalculator.DeviceContribution> acceptedDevices = all.acceptedDevices().stream()
                .filter(contribution -> scope.deviceIds().contains(contribution.deviceId()))
                .toList();
        Set<Long> acceptedDeviceIds = acceptedDevices.stream()
                .map(ArrivalFactCalculator.DeviceContribution::deviceId)
                .collect(java.util.stream.Collectors.toSet());
        List<ArrivalFactCalculator.DeviceExemption> deviceExemptions = all.deviceExemptions().stream()
                .filter(exemption -> scope.deviceIds().contains(exemption.deviceId()))
                .filter(exemption -> !acceptedDeviceIds.contains(exemption.deviceId()))
                .toList();
        List<ArrivalFactCalculator.QuantityContribution> acceptedQuantities = new ArrayList<>();
        List<ArrivalFactCalculator.QuantityExemption> quantityExemptions = new ArrayList<>();
        Map<QuantityKey, BigDecimal> remaining = requestedQuantities(scope.quantities());
        for (ArrivalFactCalculator.QuantityContribution contribution : all.acceptedQuantities()) {
            projectAcceptedQuantity(contribution, remaining, acceptedQuantities);
        }
        for (ArrivalFactCalculator.QuantityExemption exemption : all.quantityExemptions()) {
            projectQuantityExemption(exemption, remaining, quantityExemptions);
        }
        return new Contributions(List.copyOf(acceptedDevices), List.copyOf(acceptedQuantities),
                List.copyOf(deviceExemptions), List.copyOf(quantityExemptions));
    }

    private static void projectAcceptedQuantity(
            ArrivalFactCalculator.QuantityContribution contribution,
            Map<QuantityKey, BigDecimal> remaining,
            List<ArrivalFactCalculator.QuantityContribution> contributions) {
        QuantityKey key = QuantityKey.from(contribution.scope());
        BigDecimal left = remaining.get(key);
        if (left == null || left.signum() == 0) return;
        BigDecimal used = contribution.scope().quantity().min(left);
        contributions.add(new ArrivalFactCalculator.QuantityContribution(contribution.sourceAcceptanceId(),
                key.scope(used)));
        remaining.put(key, left.subtract(used));
    }

    private static void projectQuantityExemption(
            ArrivalFactCalculator.QuantityExemption exemption,
            Map<QuantityKey, BigDecimal> remaining,
            List<ArrivalFactCalculator.QuantityExemption> exemptions) {
        QuantityKey key = QuantityKey.from(exemption.scope());
        BigDecimal left = remaining.get(key);
        if (left == null || left.signum() == 0) return;
        BigDecimal used = exemption.scope().quantity().min(left);
        exemptions.add(new ArrivalFactCalculator.QuantityExemption(
                exemption.sourceAcceptanceId(), key.scope(used), exemption.reason(),
                exemption.riskDescription(), exemption.approvedBy(), exemption.approvedAt(),
                exemption.evidenceId(), exemption.evidenceRevision(), exemption.expiresAt()));
        remaining.put(key, left.subtract(used));
    }

    private static ArrivalQuantityScopeFact quantityFact(ArrivalLineDO line) {
        return new ArrivalQuantityScopeFact(line.getOrderLineId(), line.getProductCode(),
                line.getModelCode(), line.getAcceptedQuantity(), line.getUnit());
    }

    private static ArrivalFactCalculator.DeviceExemption deviceExemption(
            ArrivalDifferenceDO difference, Long deviceId) {
        return new ArrivalFactCalculator.DeviceExemption(
                difference.getArrivalAcceptanceId(), deviceId, difference.getReason(),
                difference.getRiskDescription(), difference.getApprovedBy(), difference.getApprovedAt(),
                difference.getEvidenceId(), difference.getEvidenceRevision(),
                difference.getExemptionExpiresAt());
    }

    private static ArrivalFactCalculator.QuantityExemption quantityExemption(
            ArrivalDifferenceDO difference, ArrivalDifferenceScopeCodec.QuantityScope quantity) {
        return new ArrivalFactCalculator.QuantityExemption(
                difference.getArrivalAcceptanceId(), new ArrivalQuantityScopeFact(quantity.orderLineId(),
                quantity.productCode(), quantity.modelCode(), quantity.quantity(), quantity.unitCode()),
                difference.getReason(), difference.getRiskDescription(), difference.getApprovedBy(),
                difference.getApprovedAt(), difference.getEvidenceId(), difference.getEvidenceRevision(),
                difference.getExemptionExpiresAt());
    }

    private static Map<QuantityKey, BigDecimal> requestedQuantities(
            List<ArrivalQuantityScopeFact> quantities) {
        Map<QuantityKey, BigDecimal> result = new TreeMap<>();
        for (ArrivalQuantityScopeFact quantity : quantities) {
            if (result.put(QuantityKey.from(quantity), quantity.quantity()) != null) {
                throw new IllegalStateException("requested quantity key is duplicated");
            }
        }
        return result;
    }

    private static ScopeComposition requireDeliveryScope(
            DeliveryScopePort.AssignedScope delivery, Long projectId) {
        if (delivery == null || !Objects.equals(projectId, delivery.projectId())) {
            throw new IllegalStateException("assigned delivery scope is unavailable");
        }
        TreeSet<String> serials = new TreeSet<>();
        TreeMap<QuantityKey, BigDecimal> quantities = new TreeMap<>();
        Set<Long> orderLineIds = new HashSet<>();
        for (DeliveryScopePort.AssignedLine line : delivery.lines()) {
            if (line == null || !orderLineIds.add(line.orderLineId())) {
                throw new IllegalStateException("assigned delivery line is invalid or duplicated");
            }
            if (line.serialNumbers().isEmpty()) {
                QuantityKey key = new QuantityKey(line.orderLineId(), line.productCode(),
                        line.modelCode(), line.unitCode());
                if (quantities.put(key, line.assignedQuantity()) != null) {
                    throw new IllegalStateException("assigned delivery quantity is duplicated");
                }
            } else {
                for (String serialNumber : line.serialNumbers()) {
                    if (!serials.add(serialNumber)) {
                        throw new IllegalStateException("assigned serial number is duplicated");
                    }
                }
            }
        }
        return new ScopeComposition(Set.copyOf(serials), Map.copyOf(quantities));
    }

    private static DeviceScopeFactPort.DeviceScopeFact requireDeviceScope(
            DeviceScopeFactPort.DeviceScopeFact fact, Long projectId, Set<String> expectedSerials) {
        if (fact == null || !Objects.equals(projectId, fact.projectId())) {
            throw new IllegalStateException("device scope fact is unavailable");
        }
        TreeSet<String> actualSerials = new TreeSet<>();
        Set<Long> ids = new HashSet<>();
        for (DeviceScopeFactPort.DeviceFact device : fact.devices()) {
            if (!Objects.equals(projectId, device.currentProjectId())
                    || !ids.add(device.deviceId()) || !actualSerials.add(device.serialNumber())) {
                throw new IllegalStateException("device scope fact is inconsistent");
            }
        }
        if (!actualSerials.equals(expectedSerials)) {
            throw new IllegalStateException("device scope fact is incomplete");
        }
        return fact;
    }

    private static Map<Long, DeviceScopeFactPort.DeviceFact> indexDevices(
            List<DeviceScopeFactPort.DeviceFact> devices) {
        Map<Long, DeviceScopeFactPort.DeviceFact> result = new HashMap<>();
        for (DeviceScopeFactPort.DeviceFact device : devices) {
            if (result.put(device.deviceId(), device) != null) {
                throw new IllegalStateException("device scope contains duplicate device id");
            }
        }
        return result;
    }

    private static boolean isEffectiveExemption(ArrivalDifferenceDO difference, LocalDateTime checkedAt) {
        return difference != null && "EXEMPTED".equals(difference.getResolutionStatus())
                && hasText(difference.getReason()) && hasText(difference.getRiskDescription())
                && difference.getApprovedBy() != null && difference.getApprovedAt() != null
                && difference.getEvidenceId() != null && difference.getEvidenceRevision() != null
                && difference.getEvidenceRevision() > 0 && difference.getExemptionExpiresAt() != null
                && difference.getExemptionExpiresAt().isAfter(checkedAt);
    }

    private static void requireLists(List<ArrivalLineDO> lines, List<ArrivalDifferenceDO> differences,
                                     List<ArrivalAcceptanceDO> roots) {
        if (lines == null || differences == null || roots == null
                || lines.stream().anyMatch(Objects::isNull)
                || differences.stream().anyMatch(Objects::isNull)
                || roots.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("arrival project fact source is unavailable");
        }
    }

    private static void requireAllocation(ArrivalProjectFactAllocation allocation) {
        if (allocation == null || allocation.projectFactVersion() == null
                || allocation.projectFactVersion() <= 0 || allocation.sourceId() == null
                || allocation.sourceId() <= 0 || allocation.acceptanceId() == null
                || allocation.acceptanceId() <= 0) {
            throw new IllegalStateException("arrival fact allocation source is damaged");
        }
    }

    private static void requireTenant(Long tenantId) {
        if (tenantId == null || !Objects.equals(TenantContextHolder.getRequiredTenantId(), tenantId)) {
            throw new IllegalArgumentException("arrival fact tenant does not match runtime context");
        }
    }

    private static ArrivalAcceptanceFact stale(ArrivalAcceptanceFact fact) {
        return new ArrivalAcceptanceFact(fact.tenantId(), fact.projectId(), fact.sourceAcceptanceIds(),
                ArrivalAcceptanceFact.DECISION_STALE, fact.factVersion(), fact.scopeWatermark(),
                fact.reopened(), fact.acceptedDeviceIds(), fact.exemptedDeviceIds(),
                fact.unmetDeviceIds(), fact.acceptedQuantityScopes(), fact.exemptedQuantityScopes(),
                fact.unmetQuantityScopes());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CurrentScope(Set<Long> deviceIds, List<ArrivalQuantityScopeFact> quantities,
                                Set<Long> assignedDeviceIds,
                                List<ArrivalQuantityScopeFact> assignedQuantities,
                                ArrivalScopeWatermark watermark) {
    }

    private record ScopeComposition(Set<String> serialNumbers,
                                    Map<QuantityKey, BigDecimal> quantities) {
    }

    private record FactVersion(Long version, boolean reopened) {
    }

    private record Contributions(
            List<ArrivalFactCalculator.DeviceContribution> acceptedDevices,
            List<ArrivalFactCalculator.QuantityContribution> acceptedQuantities,
            List<ArrivalFactCalculator.DeviceExemption> deviceExemptions,
            List<ArrivalFactCalculator.QuantityExemption> quantityExemptions) {
    }

    private record QuantityKey(Long orderLineId, String productCode, String modelCode,
                               String unitCode) implements Comparable<QuantityKey> {

        private static QuantityKey from(ArrivalQuantityScopeFact scope) {
            return new QuantityKey(scope.orderLineId(), scope.productCode(),
                    scope.modelCode(), scope.unitCode());
        }

        private ArrivalQuantityScopeFact scope(BigDecimal quantity) {
            return new ArrivalQuantityScopeFact(orderLineId, productCode, modelCode, quantity, unitCode);
        }

        @Override
        public int compareTo(QuantityKey other) {
            int compared = orderLineId.compareTo(other.orderLineId);
            if (compared != 0) return compared;
            compared = compareNullable(productCode, other.productCode);
            if (compared != 0) return compared;
            compared = compareNullable(modelCode, other.modelCode);
            if (compared != 0) return compared;
            return unitCode.compareTo(other.unitCode);
        }

        private static int compareNullable(String left, String right) {
            if (left == null) return right == null ? 0 : -1;
            return right == null ? 1 : left.compareTo(right);
        }
    }
}
