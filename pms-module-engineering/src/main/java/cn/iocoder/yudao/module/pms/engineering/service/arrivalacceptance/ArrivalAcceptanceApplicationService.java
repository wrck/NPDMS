package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalScopeWatermark;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalQuantityScopeFact;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalDifferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalLineDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalDifferenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalLineMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildrenQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalSubmissionUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRevisionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceSourceQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalAcceptanceRules;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalAcceptanceStateMachine;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalFactCalculator;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 到货签收应用核心。COM/AST生产Provider形成前不注册Spring Bean，只允许显式组装测试替身。
 */
public final class ArrivalAcceptanceApplicationService {

    private final ArrivalAcceptanceMapper acceptanceMapper;
    private final ArrivalLineMapper lineMapper;
    private final ArrivalDifferenceMapper differenceMapper;
    private final DeliveryEvidenceMapper evidenceMapper;
    private final DeliveryEvidenceRevisionMapper evidenceRevisionMapper;
    private final ProjectQualificationPort projectQualificationPort;
    private final DeliveryScopePort deliveryScopePort;
    private final DeviceScopeFactPort deviceScopeFactPort;
    private final FileArtifactFactPort fileArtifactFactPort;
    private final ArrivalAcceptanceRules rules = new ArrivalAcceptanceRules();
    private final ArrivalAcceptanceStateMachine stateMachine = new ArrivalAcceptanceStateMachine();
    private final ArrivalFactCalculator factCalculator = new ArrivalFactCalculator();

    public ArrivalAcceptanceApplicationService(ArrivalAcceptanceMapper acceptanceMapper,
                                               ArrivalLineMapper lineMapper,
                                               ArrivalDifferenceMapper differenceMapper,
                                               DeliveryEvidenceMapper evidenceMapper,
                                               DeliveryEvidenceRevisionMapper evidenceRevisionMapper,
                                               ProjectQualificationPort projectQualificationPort,
                                               DeliveryScopePort deliveryScopePort,
                                               DeviceScopeFactPort deviceScopeFactPort,
                                               FileArtifactFactPort fileArtifactFactPort) {
        this.acceptanceMapper = acceptanceMapper;
        this.lineMapper = lineMapper;
        this.differenceMapper = differenceMapper;
        this.evidenceMapper = evidenceMapper;
        this.evidenceRevisionMapper = evidenceRevisionMapper;
        this.projectQualificationPort = projectQualificationPort;
        this.deliveryScopePort = deliveryScopePort;
        this.deviceScopeFactPort = deviceScopeFactPort;
        this.fileArtifactFactPort = fileArtifactFactPort;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArrivalAcceptanceDO createDraft(CreateDraftCommand command) {
        requireCommand(command);
        ProjectQualificationPort.ProjectQualificationFact project = projectQualificationPort.inspect(
                command.tenantId(), command.projectId(), command.actorUserId());
        requireProject(project, command.projectId());

        DeliveryScopePort.AssignedScope deliveryScope =
                deliveryScopePort.inspectAssignedScope(command.projectId());
        requireDeliveryScope(deliveryScope, command.projectId());
        Set<String> serialNumbers = collectSerialNumbers(deliveryScope.lines());
        DeviceScopeFactPort.DeviceScopeFact deviceScope = deviceScopeFactPort.resolveBySerials(
                command.tenantId(), command.projectId(), serialNumbers);
        requireDeviceScope(deviceScope, command.projectId(), serialNumbers);

        ArrivalAcceptanceDO row = new ArrivalAcceptanceDO();
        row.setTenantId(command.tenantId());
        row.setProjectId(command.projectId());
        row.setBatchCode(command.batchCode().trim());
        row.setLogisticsNo(command.logisticsNo().trim());
        row.setArrivedAt(command.arrivedAt());
        row.setSignerSnapshot(JsonUtils.toJsonString(new SignerSnapshot(command.signerName().trim())));
        row.setStatus(ArrivalAcceptanceStateMachine.DRAFT);
        row.setProjectVersion(project.projectVersion());
        row.setProjectParticipantFactVersion(project.factVersion());
        row.setProjectScopeVersion(project.scopeVersion());
        row.setDeliveryScopeVersion(deliveryScope.scopeVersion());
        row.setExpectedScopeSnapshot(JsonUtils.toJsonString(new ExpectedScopeSnapshot(
                orderedLines(deliveryScope.lines()), orderedDevices(deviceScope.devices()))));
        row.setScopeWatermark(JsonUtils.toJsonString(new ArrivalScopeWatermark(
                deliveryScope.scopeVersion(), assignmentVersions(deviceScope.devices()))));
        row.setMigrationResolutionStatus("NOT_APPLICABLE");
        row.setVersion(0);
        row.setCreator(String.valueOf(command.actorUserId()));
        row.setUpdater(String.valueOf(command.actorUserId()));
        if (acceptanceMapper.insert(row) != 1 || row.getId() == null) {
            throw new IllegalStateException("arrival acceptance draft creation failed");
        }
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public SubmissionResult submit(SubmitCommand command) {
        requireSubmitCommand(command);
        ArrivalAcceptanceDO root = acceptanceMapper.selectForUpdate(
                new ArrivalRowQuery(command.tenantId(), command.arrivalAcceptanceId()));
        requireOwnedDraft(root, command);
        projectQualificationPort.lockAndRevalidate(new ProjectQualificationPort.RevalidationCommand(
                command.tenantId(), root.getProjectId(), null, command.actorUserId(),
                root.getProjectVersion(), root.getProjectParticipantFactVersion(),
                root.getProjectScopeVersion(), false));

        ExpectedScopeSnapshot expected = JsonUtils.parseObject(
                root.getExpectedScopeSnapshot(), ExpectedScopeSnapshot.class);
        DeliveryScopePort.AssignedScope delivery = deliveryScopePort.lockAndRevalidate(
                root.getProjectId(), root.getDeliveryScopeVersion());
        requireDeliveryScope(delivery, root.getProjectId());
        if (!orderedLines(delivery.lines()).equals(expected.deliveryLines())) {
            throw new IllegalStateException("assigned delivery scope payload changed without version change");
        }
        List<DeviceScopeFactPort.ExpectedDeviceFact> expectedDevices = expected.devices().stream()
                .map(device -> new DeviceScopeFactPort.ExpectedDeviceFact(
                        device.deviceId(), device.serialNumber(), device.projectAssignmentVersion()))
                .toList();
        DeviceScopeFactPort.DeviceScopeFact devices = deviceScopeFactPort.lockAndRevalidate(
                command.tenantId(), root.getProjectId(), expectedDevices);
        requireDeviceScope(devices, root.getProjectId(), collectSerialNumbers(delivery.lines()));
        if (!orderedDevices(devices.devices()).equals(expected.devices())) {
            throw new IllegalStateException("device assignment fact changed without version change");
        }

        EvidenceRevision evidence = lockEvidence(root);
        List<ArrivalLineDO> lines = lineMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), root.getId()));
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("arrival acceptance lines are required");
        }
        List<ArrivalDifferenceDO> differences = differenceMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), root.getId()));
        SubmissionScope scope = submissionScope(root.getId(), expected, lines, differences);
        rules.validateSubmission(scope.expectedDeviceIds(), scope.expectedQuantityScopes(),
                scope.acceptedDeviceIds(), scope.acceptedQuantityScopes(), true);
        ArrivalFactCalculator.CalculationResult calculation = factCalculator.calculate(
                new ArrivalFactCalculator.CalculationInput(
                        scope.expectedDeviceIds(), scope.expectedQuantityScopes(),
                        scope.acceptedDeviceIds().stream()
                                .map(deviceId -> new ArrivalFactCalculator.DeviceContribution(root.getId(), deviceId))
                                .toList(),
                        scope.acceptedQuantityScopes().stream()
                                .map(quantity -> new ArrivalFactCalculator.QuantityContribution(root.getId(), quantity))
                                .toList(), List.of(), List.of(), LocalDateTime.now()));
        String submittedStatus = stateMachine.submit(scope.hasOpenDifference(),
                ArrivalAcceptanceFact.DECISION_ACCEPTED.equals(calculation.decision()));
        LocalDateTime submittedAt = LocalDateTime.now();
        int updated = acceptanceMapper.updateSubmittedIfMatch(new ArrivalSubmissionUpdate(
                command.tenantId(), root.getId(), command.expectedVersion(), submittedStatus,
                evidence.root().getId(), evidence.revision().getRevisionNo(),
                command.actorUserId(), submittedAt));
        if (updated != 1) {
            throw new IllegalStateException("arrival acceptance version changed before submit");
        }
        return new SubmissionResult(root.getId(), submittedStatus, command.expectedVersion() + 1,
                evidence.root().getId(), evidence.revision().getRevisionNo());
    }

    private EvidenceRevision lockEvidence(ArrivalAcceptanceDO root) {
        DeliveryEvidenceDO evidence = evidenceMapper.selectBySourceForUpdate(new DeliveryEvidenceSourceQuery(
                root.getTenantId(), "EXE-01", "ARRIVAL_ACCEPTANCE", root.getId()));
        if (evidence == null || !root.getProjectId().equals(evidence.getProjectId())
                || evidence.getCurrentRevisionNo() == null || evidence.getCurrentRevisionNo() <= 0) {
            throw new IllegalStateException("current arrival evidence is unavailable");
        }
        DeliveryEvidenceRevisionDO revision = evidenceRevisionMapper.selectRevision(
                new DeliveryEvidenceRevisionQuery(root.getTenantId(), evidence.getId(),
                        evidence.getCurrentRevisionNo()));
        if (revision == null || !root.getId().equals(revision.getSourceRecordId())) {
            throw new IllegalStateException("arrival evidence revision is unavailable or mismatched");
        }
        FileFactVersion fileFactVersion = JsonUtils.parseObject(
                revision.getFileFactVersion(), FileFactVersion.class);
        FileArtifactVersionFact current = fileArtifactFactPort.lockAndRevalidateArrivalEvidence(
                new FileArtifactFactPort.ArrivalEvidenceExpectation(
                        revision.getFileArtifactId(), revision.getFileVersionNo(), root.getId(),
                        revision.getFileReferenceId(), fileFactVersion, revision.getFileScopeVersion()));
        if (current == null || !revision.getFileArtifactId().equals(current.artifactId())
                || !revision.getFileVersionNo().equals(current.versionNo())
                || !revision.getFileReferenceId().equals(current.referenceKey())
                || !fileFactVersion.equals(current.fileFactVersion())
                || !revision.getFileScopeVersion().equals(current.scopeVersion())
                || !revision.getFileHash().equals(current.sha256())) {
            throw new IllegalStateException("arrival evidence fact is stale or mismatched");
        }
        return new EvidenceRevision(evidence, revision);
    }

    private static SubmissionScope submissionScope(Long acceptanceId, ExpectedScopeSnapshot expected,
                                                   List<ArrivalLineDO> lines,
                                                   List<ArrivalDifferenceDO> differences) {
        Set<Long> expectedDeviceIds = expected.devices().stream()
                .map(DeviceScopeFactPort.DeviceFact::deviceId).collect(java.util.stream.Collectors.toSet());
        List<ArrivalQuantityScopeFact> expectedQuantities = expected.deliveryLines().stream()
                .filter(line -> line.serialNumbers().isEmpty())
                .map(line -> new ArrivalQuantityScopeFact(line.orderLineId(), line.productCode(),
                        line.modelCode(), line.assignedQuantity(), line.unitCode()))
                .toList();
        Set<Long> acceptedDevices = new HashSet<>();
        List<ArrivalQuantityScopeFact> acceptedQuantities = new java.util.ArrayList<>();
        for (ArrivalLineDO line : lines) {
            if (!acceptanceId.equals(line.getArrivalAcceptanceId())) {
                throw new IllegalStateException("arrival line belongs to another batch");
            }
            if (!"ACCEPTED".equals(line.getStatus())) continue;
            if ("DEVICE".equals(line.getScopeType())) {
                if (line.getDeviceId() == null || !acceptedDevices.add(line.getDeviceId())) {
                    throw new IllegalStateException("accepted device line is invalid or duplicated");
                }
            } else if ("ORDER_MODEL_QUANTITY".equals(line.getScopeType())) {
                acceptedQuantities.add(new ArrivalQuantityScopeFact(
                        line.getOrderLineId(), line.getProductCode(), line.getModelCode(),
                        line.getAcceptedQuantity(), line.getUnit()));
            } else {
                throw new IllegalStateException("arrival line scope type is unsupported");
            }
        }
        boolean hasOpenDifference = differences != null && differences.stream().anyMatch(difference -> {
            if (!acceptanceId.equals(difference.getArrivalAcceptanceId())) {
                throw new IllegalStateException("arrival difference belongs to another batch");
            }
            return "OPEN".equals(difference.getResolutionStatus());
        });
        return new SubmissionScope(Set.copyOf(expectedDeviceIds), expectedQuantities,
                Set.copyOf(acceptedDevices), List.copyOf(acceptedQuantities), hasOpenDifference);
    }

    private static void requireSubmitCommand(SubmitCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.arrivalAcceptanceId() == null || command.arrivalAcceptanceId() <= 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0) {
            throw new IllegalArgumentException("invalid arrival acceptance submit command");
        }
    }

    private static void requireOwnedDraft(ArrivalAcceptanceDO root, SubmitCommand command) {
        if (root == null || !ArrivalAcceptanceStateMachine.DRAFT.equals(root.getStatus())
                || !command.expectedVersion().equals(root.getVersion())
                || !String.valueOf(command.actorUserId()).equals(root.getCreator())
                || root.getProjectVersion() == null || root.getProjectParticipantFactVersion() == null
                || root.getProjectScopeVersion() == null) {
            throw new IllegalStateException("arrival acceptance draft is unavailable, stale or not owned by actor");
        }
    }

    private static void requireCommand(CreateDraftCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.projectId() == null || command.projectId() <= 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || blank(command.batchCode()) || blank(command.logisticsNo())
                || command.arrivedAt() == null || blank(command.signerName())) {
            throw new IllegalArgumentException("invalid arrival acceptance draft command");
        }
    }

    private static void requireProject(ProjectQualificationPort.ProjectQualificationFact project,
                                       Long projectId) {
        if (project == null || !projectId.equals(project.projectId())) {
            throw new IllegalStateException("project qualification fact is unavailable or mismatched");
        }
    }

    private static void requireDeliveryScope(DeliveryScopePort.AssignedScope scope, Long projectId) {
        if (scope == null || !projectId.equals(scope.projectId())) {
            throw new IllegalStateException("assigned delivery scope is unavailable or mismatched");
        }
    }

    private static Set<String> collectSerialNumbers(List<DeliveryScopePort.AssignedLine> lines) {
        Set<String> serialNumbers = new HashSet<>();
        for (DeliveryScopePort.AssignedLine line : lines) {
            for (String serialNumber : line.serialNumbers()) {
                if (!serialNumbers.add(serialNumber)) {
                    throw new IllegalStateException("assigned serial number is duplicated");
                }
            }
        }
        return Set.copyOf(serialNumbers);
    }

    private static void requireDeviceScope(DeviceScopeFactPort.DeviceScopeFact scope, Long projectId,
                                           Set<String> expectedSerialNumbers) {
        if (scope == null || !projectId.equals(scope.projectId())) {
            throw new IllegalStateException("device scope fact is unavailable or mismatched");
        }
        Set<String> actualSerialNumbers = new HashSet<>();
        Set<Long> deviceIds = new HashSet<>();
        for (DeviceScopeFactPort.DeviceFact device : scope.devices()) {
            if (!projectId.equals(device.currentProjectId())
                    || !actualSerialNumbers.add(device.serialNumber()) || !deviceIds.add(device.deviceId())) {
                throw new IllegalStateException("device scope contains foreign or duplicate device");
            }
        }
        if (!actualSerialNumbers.equals(expectedSerialNumbers)) {
            throw new IllegalStateException("device scope does not resolve every assigned serial number");
        }
    }

    private static List<DeliveryScopePort.AssignedLine> orderedLines(
            List<DeliveryScopePort.AssignedLine> lines) {
        return lines.stream().sorted(Comparator.comparing(DeliveryScopePort.AssignedLine::orderLineId)).toList();
    }

    private static List<DeviceScopeFactPort.DeviceFact> orderedDevices(
            List<DeviceScopeFactPort.DeviceFact> devices) {
        return devices.stream().sorted(Comparator.comparing(DeviceScopeFactPort.DeviceFact::deviceId)).toList();
    }

    private static Map<Long, Long> assignmentVersions(List<DeviceScopeFactPort.DeviceFact> devices) {
        Map<Long, Long> versions = new LinkedHashMap<>();
        orderedDevices(devices).forEach(device ->
                versions.put(device.deviceId(), device.projectAssignmentVersion()));
        return Map.copyOf(versions);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record CreateDraftCommand(Long tenantId, Long projectId, Long actorUserId,
                                     String batchCode, String logisticsNo,
                                     LocalDateTime arrivedAt, String signerName) {
    }

    public record SubmitCommand(Long tenantId, Long arrivalAcceptanceId,
                                Long actorUserId, Integer expectedVersion) {
    }

    public record SubmissionResult(Long arrivalAcceptanceId, String status, Integer version,
                                   Long evidenceId, Integer evidenceRevision) {
    }

    private record SignerSnapshot(String signerName) {
    }

    private record ExpectedScopeSnapshot(List<DeliveryScopePort.AssignedLine> deliveryLines,
                                         List<DeviceScopeFactPort.DeviceFact> devices) {
    }

    private record EvidenceRevision(DeliveryEvidenceDO root, DeliveryEvidenceRevisionDO revision) {
    }

    private record SubmissionScope(Set<Long> expectedDeviceIds,
                                   List<ArrivalQuantityScopeFact> expectedQuantityScopes,
                                   Set<Long> acceptedDeviceIds,
                                   List<ArrivalQuantityScopeFact> acceptedQuantityScopes,
                                   boolean hasOpenDifference) {
    }
}
