package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ImplementationEvidencePublishedMessage;
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
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalConfirmationUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactVersionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalSubmissionUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRevisionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidencePublishUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceSourceQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalAcceptanceRules;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalAcceptanceStateMachine;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalDifferenceScopeCodec;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalFactCalculator;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence.ArrivalEvidenceEventFactory;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 到货签收应用核心。COM/AST生产Provider形成前不注册Spring Bean，只允许显式组装测试替身。
 */
public class ArrivalAcceptanceApplicationService {

    private final ArrivalAcceptanceMapper acceptanceMapper;
    private final ArrivalLineMapper lineMapper;
    private final ArrivalDifferenceMapper differenceMapper;
    private final DeliveryEvidenceMapper evidenceMapper;
    private final DeliveryEvidenceRevisionMapper evidenceRevisionMapper;
    private final ProjectQualificationPort projectQualificationPort;
    private final DeliveryScopePort deliveryScopePort;
    private final DeviceScopeFactPort deviceScopeFactPort;
    private final FileArtifactFactPort fileArtifactFactPort;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final ArrivalEvidenceEventFactory evidenceEventFactory;
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
                                               FileArtifactFactPort fileArtifactFactPort,
                                               PlatformCommandExecutionApi commandExecutionApi) {
        this.acceptanceMapper = acceptanceMapper;
        this.lineMapper = lineMapper;
        this.differenceMapper = differenceMapper;
        this.evidenceMapper = evidenceMapper;
        this.evidenceRevisionMapper = evidenceRevisionMapper;
        this.projectQualificationPort = projectQualificationPort;
        this.deliveryScopePort = deliveryScopePort;
        this.deviceScopeFactPort = deviceScopeFactPort;
        this.fileArtifactFactPort = fileArtifactFactPort;
        this.commandExecutionApi = commandExecutionApi;
        this.evidenceEventFactory = new ArrivalEvidenceEventFactory();
    }

    @Transactional(rollbackFor = Exception.class)
    public ArrivalAcceptanceDO createDraft(CreateDraftCommand command) {
        requireCommand(command);
        PlatformCommandExecutionApi.ExecutionResult<ArrivalAcceptanceDO> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "IMP:ARRIVAL_CREATE:" + command.projectId(), command.actorUserId(), command.idempotencyKey()),
                createDigest(command), ArrivalAcceptanceDO.class, () -> createDraftOnce(command),
                result -> new PlatformCommandExecutionApi.SuccessFacts("ARRIVAL_ACCEPTANCE_CREATE",
                        "ArrivalAcceptance", String.valueOf(result.getId()), null,
                        JsonUtils.toJsonString(result), List.of()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw new IllegalStateException("arrival acceptance creation is conflicting or in progress");
        }
        return execution.response();
    }

    private ArrivalAcceptanceDO createDraftOnce(CreateDraftCommand command) {
        ProjectQualificationPort.ProjectQualificationFact project = projectQualificationPort.inspect(
                command.tenantId(), command.projectId(), command.actorUserId());
        requireProject(project, command.projectId());

        DeliveryScopePort.AssignedScope deliveryScope =
                deliveryScopePort.inspectAssignedScope(command.projectId());
        requireDeliveryScope(deliveryScope, command.projectId());
        if (command.expectedDeliveryScopeVersion() != null
                && !command.expectedDeliveryScopeVersion().equals(deliveryScope.scopeVersion())) {
            throw new IllegalStateException("assigned delivery scope version is stale");
        }
        Set<String> serialNumbers = collectSerialNumbers(deliveryScope.lines());
        DeviceScopeFactPort.DeviceScopeFact deviceScope = deviceScopeFactPort.resolveBySerials(
                command.tenantId(), command.projectId(), serialNumbers);
        requireDeviceScope(deviceScope, command.projectId(), serialNumbers);

        ArrivalAcceptanceDO row = new ArrivalAcceptanceDO();
        row.setTenantId(command.tenantId());
        row.setProjectId(command.projectId());
        row.setBatchCode(command.batchCode().trim());
        row.setBatchRootMarker(1);
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
        PlatformCommandExecutionApi.ExecutionResult<SubmissionResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "IMP:ARRIVAL_SUBMIT:" + command.arrivalAcceptanceId(),
                        command.actorUserId(), command.idempotencyKey()),
                submitDigest(command), SubmissionResult.class, () -> submitOnce(command),
                result -> new PlatformCommandExecutionApi.SuccessFacts("ARRIVAL_ACCEPTANCE_SUBMIT",
                        "ArrivalAcceptance", String.valueOf(result.arrivalAcceptanceId()), null,
                        JsonUtils.toJsonString(result), List.of()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw new IllegalStateException("arrival acceptance submission is conflicting or in progress");
        }
        return execution.response();
    }

    private SubmissionResult submitOnce(SubmitCommand command) {
        ArrivalAcceptanceDO root = acceptanceMapper.selectForUpdate(
                new ArrivalRowQuery(command.tenantId(), command.arrivalAcceptanceId()));
        requireOwnedDraft(root, command);
        LockedEvaluation evaluation = lockAndEvaluate(root, command.actorUserId(), false);
        String submittedStatus = stateMachine.submit(evaluation.scope().hasOpenDifference(),
                ArrivalAcceptanceFact.DECISION_ACCEPTED.equals(evaluation.calculation().decision()));
        LocalDateTime submittedAt = LocalDateTime.now();
        int updated = acceptanceMapper.updateSubmittedIfMatch(new ArrivalSubmissionUpdate(
                command.tenantId(), root.getId(), command.expectedVersion(), submittedStatus,
                evaluation.evidence().root().getId(), evaluation.evidence().revision().getRevisionNo(),
                command.actorUserId(), submittedAt));
        if (updated != 1) {
            throw new IllegalStateException("arrival acceptance version changed before submit");
        }
        return new SubmissionResult(root.getId(), submittedStatus, command.expectedVersion() + 1,
                evaluation.evidence().root().getId(), evaluation.evidence().revision().getRevisionNo());
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationResult confirm(ConfirmCommand command) {
        requireConfirmCommand(command);
        PlatformCommandExecutionApi.ExecutionResult<ConfirmationResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "IMP:ARRIVAL_CONFIRM:" + command.arrivalAcceptanceId(),
                        command.actorUserId(), command.idempotencyKey()),
                confirmationDigest(command), ConfirmationResult.class,
                () -> confirmOnce(command),
                result -> confirmationSuccessFacts(command, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw new IllegalStateException("arrival acceptance confirmation is conflicting or in progress");
        }
        return execution.response();
    }

    private ConfirmationResult confirmOnce(ConfirmCommand command) {
        ArrivalAcceptanceDO root = acceptanceMapper.selectForUpdate(
                new ArrivalRowQuery(command.tenantId(), command.arrivalAcceptanceId()));
        requireConfirmable(root, command);
        stateMachine.confirm(root.getStatus());
        LockedEvaluation evaluation = lockAndEvaluate(root, command.actorUserId(), true);
        String recalculatedStatus = stateMachine.submit(evaluation.scope().hasOpenDifference(),
                ArrivalAcceptanceFact.DECISION_ACCEPTED.equals(evaluation.calculation().decision()));
        if (!root.getStatus().equals(recalculatedStatus)) {
            throw new IllegalStateException("arrival acceptance candidate fact changed before confirm");
        }
        EvidenceRevision evidence = evaluation.evidence();
        if (!root.getEvidenceId().equals(evidence.root().getId())
                || !root.getEvidenceRevision().equals(evidence.revision().getRevisionNo())
                || !"NOT_PUBLISHED".equals(evidence.root().getAccSyncStatus())
                || evidence.root().getVersion() == null) {
            throw new IllegalStateException("arrival evidence is not publishable for this batch revision");
        }
        Long currentMax = acceptanceMapper.selectMaxAllocatedProjectFactVersion(
                new ArrivalProjectFactVersionQuery(command.tenantId(), root.getProjectId()));
        long projectFactVersion = currentMax == null ? 1L : Math.addExact(currentMax, 1L);
        LocalDateTime confirmedAt = LocalDateTime.now();
        String eventId = evidenceEventFactory.nextEventId();
        int updated = acceptanceMapper.updateConfirmedIfMatch(new ArrivalConfirmationUpdate(
                command.tenantId(), root.getId(), command.expectedVersion(), projectFactVersion,
                command.actorUserId(), confirmedAt));
        if (updated != 1) {
            throw new IllegalStateException("arrival acceptance version changed before confirm");
        }
        int evidenceUpdated = evidenceMapper.markPublishedPendingAccIfMatch(
                new DeliveryEvidencePublishUpdate(command.tenantId(), evidence.root().getId(),
                        evidence.revision().getRevisionNo(), evidence.root().getVersion(), eventId,
                        command.correlationId(), command.actorUserId(), confirmedAt));
        if (evidenceUpdated != 1) {
            throw new IllegalStateException("arrival evidence changed before publish");
        }
        return new ConfirmationResult(root.getId(), ArrivalAcceptanceStateMachine.CONFIRMED,
                command.expectedVersion() + 1, projectFactVersion,
                evidence.root().getId(), evidence.revision().getRevisionNo(),
                evidence.revision().getFileArtifactId(), evidence.revision().getFileVersionNo(),
                evidence.revision().getFileReferenceId(), evidence.revision().getFileHash(),
                evidence.revision().getSourceVersion(), root.getScopeWatermark(), eventId, confirmedAt);
    }

    private PlatformCommandExecutionApi.SuccessFacts confirmationSuccessFacts(
            ConfirmCommand command, ConfirmationResult result) {
        ImplementationEvidencePublishedMessage message = new ImplementationEvidencePublishedMessage(
                result.eventId(), command.tenantId(), result.evidenceId(), result.evidenceRevision(),
                result.artifactId(), result.fileVersion(), result.fileReference(), result.hash(),
                "EXE-01", result.arrivalAcceptanceId(), result.sourceVersion(),
                result.sourceScopeWatermark(), result.confirmedAt(), command.correlationId());
        PlatformCommandExecutionApi.BusinessEvent event = evidenceEventFactory.published(message);
        return new PlatformCommandExecutionApi.SuccessFacts(
                "ARRIVAL_ACCEPTANCE_CONFIRM", "ArrivalAcceptance",
                String.valueOf(result.arrivalAcceptanceId()), command.correlationId(),
                JsonUtils.toJsonString(result), List.of(event));
    }

    private LockedEvaluation lockAndEvaluate(ArrivalAcceptanceDO root, Long actorUserId,
                                              boolean requireActorAsProjectManager) {
        ProjectQualificationPort.ProjectQualificationFact project =
                projectQualificationPort.lockAndRevalidate(
                        new ProjectQualificationPort.RevalidationCommand(
                                root.getTenantId(), root.getProjectId(),
                                requireActorAsProjectManager ? actorUserId : null, actorUserId,
                                root.getProjectVersion(), root.getProjectParticipantFactVersion(),
                                root.getProjectScopeVersion(), requireActorAsProjectManager));
        requireProject(project, root.getProjectId());
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
                root.getTenantId(), root.getProjectId(), expectedDevices);
        requireDeviceScope(devices, root.getProjectId(), collectSerialNumbers(delivery.lines()));
        if (!orderedDevices(devices.devices()).equals(expected.devices())) {
            throw new IllegalStateException("device assignment fact changed without version change");
        }
        EvidenceRevision evidence = lockEvidence(root);
        List<ArrivalLineDO> lines = lineMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(root.getTenantId(), root.getId()));
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("arrival acceptance lines are required");
        }
        List<ArrivalDifferenceDO> differences = differenceMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(root.getTenantId(), root.getId()));
        SubmissionScope scope = submissionScope(root.getId(), expected, lines, differences);
        rules.validateSubmission(scope.expectedDeviceIds(), scope.expectedQuantityScopes(),
                scope.acceptedDeviceIds(), scope.acceptedQuantityScopes(), true);
        LocalDateTime checkedAt = LocalDateTime.now();
        ArrivalProjectFactQuery factQuery = new ArrivalProjectFactQuery(
                root.getTenantId(), root.getProjectId(), checkedAt);
        List<ArrivalLineDO> confirmedLines = lineMapper.selectConfirmedAcceptedByProject(factQuery);
        List<ArrivalDifferenceDO> confirmedExemptions =
                differenceMapper.selectEffectiveExemptionsByProject(factQuery);
        List<ArrivalFactCalculator.DeviceContribution> acceptedDevices = new java.util.ArrayList<>();
        List<ArrivalFactCalculator.QuantityContribution> acceptedQuantities = new java.util.ArrayList<>();
        List<ArrivalFactCalculator.DeviceExemption> deviceExemptions = new java.util.ArrayList<>();
        List<ArrivalFactCalculator.QuantityExemption> quantityExemptions = new java.util.ArrayList<>();
        addContributions(root.getId(), scope.acceptedDeviceIds(), scope.acceptedQuantityScopes(),
                acceptedDevices, acceptedQuantities);
        addConfirmedContributions(confirmedLines, acceptedDevices, acceptedQuantities);
        addEffectiveExemptions(differences, checkedAt, deviceExemptions, quantityExemptions);
        addEffectiveExemptions(confirmedExemptions, checkedAt, deviceExemptions, quantityExemptions);
        ArrivalFactCalculator.CalculationResult calculation = factCalculator.calculate(
                new ArrivalFactCalculator.CalculationInput(
                        scope.expectedDeviceIds(), scope.expectedQuantityScopes(),
                        List.copyOf(acceptedDevices), List.copyOf(acceptedQuantities),
                        List.copyOf(deviceExemptions), List.copyOf(quantityExemptions), checkedAt));
        return new LockedEvaluation(scope, calculation, evidence);
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

    private static void addContributions(Long acceptanceId, Set<Long> deviceIds,
                                         List<ArrivalQuantityScopeFact> quantities,
                                         List<ArrivalFactCalculator.DeviceContribution> devices,
                                         List<ArrivalFactCalculator.QuantityContribution> quantityContributions) {
        deviceIds.forEach(deviceId -> devices.add(
                new ArrivalFactCalculator.DeviceContribution(acceptanceId, deviceId)));
        quantities.forEach(quantity -> quantityContributions.add(
                new ArrivalFactCalculator.QuantityContribution(acceptanceId, quantity)));
    }

    private static void addConfirmedContributions(List<ArrivalLineDO> confirmedLines,
                                                  List<ArrivalFactCalculator.DeviceContribution> devices,
                                                  List<ArrivalFactCalculator.QuantityContribution> quantities) {
        if (confirmedLines == null) return;
        for (ArrivalLineDO line : confirmedLines) {
            if ("DEVICE".equals(line.getScopeType())) {
                devices.add(new ArrivalFactCalculator.DeviceContribution(
                        line.getArrivalAcceptanceId(), line.getDeviceId()));
            } else if ("ORDER_MODEL_QUANTITY".equals(line.getScopeType())) {
                quantities.add(new ArrivalFactCalculator.QuantityContribution(
                        line.getArrivalAcceptanceId(), new ArrivalQuantityScopeFact(
                        line.getOrderLineId(), line.getProductCode(), line.getModelCode(),
                        line.getAcceptedQuantity(), line.getUnit())));
            } else {
                throw new IllegalStateException("confirmed arrival line scope type is unsupported");
            }
        }
    }

    private static void addEffectiveExemptions(
            List<ArrivalDifferenceDO> differences, LocalDateTime checkedAt,
            List<ArrivalFactCalculator.DeviceExemption> devices,
            List<ArrivalFactCalculator.QuantityExemption> quantities) {
        if (differences == null) return;
        for (ArrivalDifferenceDO difference : differences) {
            if (!isEffectiveExemption(difference, checkedAt)) continue;
            ArrivalDifferenceScopeCodec.Scope scope =
                    ArrivalDifferenceScopeCodec.parse(difference.getScopeSnapshot());
            if (scope instanceof ArrivalDifferenceScopeCodec.DeviceScope device) {
                devices.add(new ArrivalFactCalculator.DeviceExemption(
                        difference.getArrivalAcceptanceId(), device.deviceId(),
                        difference.getReason(), difference.getRiskDescription(),
                        difference.getApprovedBy(), difference.getApprovedAt(),
                        difference.getEvidenceId(), difference.getEvidenceRevision(),
                        difference.getExemptionExpiresAt()));
            } else if (scope instanceof ArrivalDifferenceScopeCodec.QuantityScope quantity) {
                quantities.add(new ArrivalFactCalculator.QuantityExemption(
                        difference.getArrivalAcceptanceId(), new ArrivalQuantityScopeFact(
                        quantity.orderLineId(), quantity.productCode(), quantity.modelCode(),
                        quantity.quantity(), quantity.unitCode()),
                        difference.getReason(), difference.getRiskDescription(),
                        difference.getApprovedBy(), difference.getApprovedAt(),
                        difference.getEvidenceId(), difference.getEvidenceRevision(),
                        difference.getExemptionExpiresAt()));
            }
        }
    }

    private static boolean isEffectiveExemption(ArrivalDifferenceDO difference, LocalDateTime checkedAt) {
        return difference != null && "EXEMPTED".equals(difference.getResolutionStatus())
                && !blank(difference.getReason()) && !blank(difference.getRiskDescription())
                && difference.getApprovedBy() != null && difference.getApprovedAt() != null
                && difference.getEvidenceId() != null
                && difference.getEvidenceRevision() != null && difference.getEvidenceRevision() > 0
                && difference.getExemptionExpiresAt() != null
                && difference.getExemptionExpiresAt().isAfter(checkedAt);
    }

    private static void requireSubmitCommand(SubmitCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.arrivalAcceptanceId() == null || command.arrivalAcceptanceId() <= 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || blank(command.idempotencyKey())) {
            throw new IllegalArgumentException("invalid arrival acceptance submit command");
        }
    }

    private static void requireConfirmCommand(ConfirmCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.arrivalAcceptanceId() == null || command.arrivalAcceptanceId() <= 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || blank(command.idempotencyKey()) || blank(command.correlationId())
                || command.correlationId().length() > 128
                || !command.correlationId().equals(command.correlationId().trim())) {
            throw new IllegalArgumentException("invalid arrival acceptance confirm command");
        }
    }

    private static void requireConfirmable(ArrivalAcceptanceDO root, ConfirmCommand command) {
        if (root == null || !command.expectedVersion().equals(root.getVersion())
                || root.getProjectVersion() == null || root.getProjectParticipantFactVersion() == null
                || root.getProjectScopeVersion() == null || root.getProjectFactVersion() != null
                || root.getEvidenceId() == null || root.getEvidenceRevision() == null) {
            throw new IllegalStateException("arrival acceptance candidate is unavailable or stale");
        }
    }

    private static String confirmationDigest(ConfirmCommand command) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("arrivalAcceptanceId", command.arrivalAcceptanceId());
        normalized.put("expectedVersion", command.expectedVersion());
        return digest(normalized);
    }

    private static String createDigest(CreateDraftCommand command) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("projectId", command.projectId());
        normalized.put("batchCode", command.batchCode().trim());
        normalized.put("logisticsNo", command.logisticsNo().trim());
        normalized.put("arrivedAt", command.arrivedAt());
        normalized.put("signerName", command.signerName().trim());
        normalized.put("expectedDeliveryScopeVersion", command.expectedDeliveryScopeVersion());
        return digest(normalized);
    }

    private static String submitDigest(SubmitCommand command) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("arrivalAcceptanceId", command.arrivalAcceptanceId());
        normalized.put("expectedVersion", command.expectedVersion());
        return digest(normalized);
    }

    private static String digest(Map<String, Object> normalized) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(normalized).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", ex);
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
                || command.arrivedAt() == null || blank(command.signerName())
                || command.expectedDeliveryScopeVersion() != null
                && command.expectedDeliveryScopeVersion() < 0
                || blank(command.idempotencyKey())) {
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
                                     LocalDateTime arrivedAt, String signerName,
                                     Long expectedDeliveryScopeVersion, String idempotencyKey) {
    }

    public record SubmitCommand(Long tenantId, Long arrivalAcceptanceId,
                                Long actorUserId, Integer expectedVersion, String idempotencyKey) {
    }

    public record ConfirmCommand(Long tenantId, Long arrivalAcceptanceId,
                                 Long actorUserId, Integer expectedVersion,
                                 String idempotencyKey, String correlationId) {
    }

    public record SubmissionResult(Long arrivalAcceptanceId, String status, Integer version,
                                   Long evidenceId, Integer evidenceRevision) {
    }

    public record ConfirmationResult(
            Long arrivalAcceptanceId,
            String status,
            Integer version,
            Long projectFactVersion,
            Long evidenceId,
            Integer evidenceRevision,
            Long artifactId,
            Integer fileVersion,
            String fileReference,
            String hash,
            Long sourceVersion,
            String sourceScopeWatermark,
            String eventId,
            LocalDateTime confirmedAt) {
    }

    private record SignerSnapshot(String signerName) {
    }

    private record ExpectedScopeSnapshot(List<DeliveryScopePort.AssignedLine> deliveryLines,
                                         List<DeviceScopeFactPort.DeviceFact> devices) {
    }

    private record EvidenceRevision(DeliveryEvidenceDO root, DeliveryEvidenceRevisionDO revision) {
    }

    private record LockedEvaluation(SubmissionScope scope,
                                    ArrivalFactCalculator.CalculationResult calculation,
                                    EvidenceRevision evidence) {
    }

    private record SubmissionScope(Set<Long> expectedDeviceIds,
                                   List<ArrivalQuantityScopeFact> expectedQuantityScopes,
                                   Set<Long> acceptedDeviceIds,
                                   List<ArrivalQuantityScopeFact> acceptedQuantityScopes,
                                   boolean hasOpenDifference) {
    }
}
