package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildRevisionMutation;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildrenQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalDraftMutation;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalResolutionMutation;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRevisionAdvance;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceSourceQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalDifferenceScopeCodec;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ArrivalDifferenceTypePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Task 5B写模型；生产Bean等待Task 12依赖接通。 */
public class ArrivalAcceptanceCommandService {

    private final ArrivalAcceptanceMapper acceptanceMapper;
    private final ArrivalLineMapper lineMapper;
    private final ArrivalDifferenceMapper differenceMapper;
    private final DeliveryEvidenceMapper evidenceMapper;
    private final DeliveryEvidenceRevisionMapper revisionMapper;
    private final ProjectQualificationPort projectPort;
    private final DeliveryScopePort deliveryPort;
    private final DeviceScopeFactPort devicePort;
    private final FileArtifactFactPort filePort;
    private final ArrivalDifferenceTypePort differenceTypePort;
    private final PlatformCommandExecutionApi commandApi;
    private final Clock clock;

    public ArrivalAcceptanceCommandService(ArrivalAcceptanceMapper acceptanceMapper,
                                           ArrivalLineMapper lineMapper,
                                           ArrivalDifferenceMapper differenceMapper,
                                           DeliveryEvidenceMapper evidenceMapper,
                                           DeliveryEvidenceRevisionMapper revisionMapper,
                                           ProjectQualificationPort projectPort,
                                           DeliveryScopePort deliveryPort,
                                           DeviceScopeFactPort devicePort,
                                           FileArtifactFactPort filePort,
                                           ArrivalDifferenceTypePort differenceTypePort,
                                           PlatformCommandExecutionApi commandApi,
                                           Clock clock) {
        this.acceptanceMapper = Objects.requireNonNull(acceptanceMapper);
        this.lineMapper = Objects.requireNonNull(lineMapper);
        this.differenceMapper = Objects.requireNonNull(differenceMapper);
        this.evidenceMapper = Objects.requireNonNull(evidenceMapper);
        this.revisionMapper = Objects.requireNonNull(revisionMapper);
        this.projectPort = Objects.requireNonNull(projectPort);
        this.deliveryPort = Objects.requireNonNull(deliveryPort);
        this.devicePort = Objects.requireNonNull(devicePort);
        this.filePort = Objects.requireNonNull(filePort);
        this.differenceTypePort = Objects.requireNonNull(differenceTypePort);
        this.commandApi = Objects.requireNonNull(commandApi);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArrivalAcceptanceCommands.CommandResult patchDraft(
            ArrivalAcceptanceCommands.PatchDraftCommand command) {
        ArrivalAcceptanceDO root = lockOwnedDraft(command.tenantId(), command.arrivalAcceptanceId(),
                command.actorUserId(), command.expectedVersion());
        FrozenScope frozen = lockOwners(root, command.actorUserId(), false);
        List<ArrivalLineDO> currentLines = lineMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), root.getId()));
        if (command.lines() != null) appendLineRevisions(root, currentLines, command.lines(), frozen,
                command.actorUserId());
        EvidenceRef evidence = command.evidenceRevision() == null ? null
                : appendEvidence(root, command.evidenceRevision(), command.expectedVersion() + 1L,
                        command.actorUserId());
        int updated = acceptanceMapper.mutateDraftIfMatch(new ArrivalDraftMutation(
                command.tenantId(), root.getId(), command.expectedVersion(), command.logisticsNo(),
                command.arrivedAt(), command.signerName() == null ? null
                : JsonUtils.toJsonString(new ArrivalAcceptanceViews.SignerSnapshot(command.signerName())),
                command.actorUserId()));
        if (updated != 1) throw new VersionConflictException();
        return new ArrivalAcceptanceCommands.CommandResult(root.getId(), null, null, null, null,
                "DRAFT", command.expectedVersion() + 1, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArrivalAcceptanceCommands.CommandResult raiseDifference(
            ArrivalAcceptanceCommands.RaiseDifferenceCommand command) {
        PlatformCommandExecutionApi.ExecutionResult<ArrivalAcceptanceCommands.CommandResult> result =
                commandApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                                command.tenantId(), "IMP:ARRIVAL_RAISE_DIFFERENCE:" + command.arrivalAcceptanceId(),
                                command.actorUserId(), command.idempotencyKey()),
                        digest(command), ArrivalAcceptanceCommands.CommandResult.class,
                        () -> raiseOnce(command), response -> successFacts("ARRIVAL_DIFFERENCE_RAISE", response));
        return requireCompleted(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArrivalAcceptanceCommands.CommandResult resolveDifference(
            ArrivalAcceptanceCommands.ResolveDifferenceCommand command) {
        PlatformCommandExecutionApi.ExecutionResult<ArrivalAcceptanceCommands.CommandResult> result =
                commandApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                                command.tenantId(), "IMP:ARRIVAL_RESOLVE_DIFFERENCE:" + command.arrivalAcceptanceId(),
                                command.actorUserId(), command.idempotencyKey()),
                        digest(command), ArrivalAcceptanceCommands.CommandResult.class,
                        () -> resolveOnce(command), response -> successFacts("ARRIVAL_DIFFERENCE_RESOLVE", response));
        return requireCompleted(result);
    }

    private ArrivalAcceptanceCommands.CommandResult raiseOnce(
            ArrivalAcceptanceCommands.RaiseDifferenceCommand command) {
        ArrivalAcceptanceDO root = lockOwnedDraft(command.tenantId(), command.arrivalAcceptanceId(),
                command.actorUserId(), command.expectedVersion());
        lockOwners(root, command.actorUserId(), false);
        differenceTypePort.requireEnabled(command.differenceTypeCode());
        List<ArrivalLineDO> lines = lineMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), root.getId()));
        ArrivalLineDO line = lines.stream().filter(value -> value.getId().equals(command.arrivalLineId()))
                .findFirst().orElseThrow(LineVersionConflictException::new);
        if (!Objects.equals(line.getVersion(), command.expectedLineVersion())) {
            throw new LineVersionConflictException();
        }
        requireScopeWithinLine(command.scope(), line);
        List<ArrivalDifferenceDO> differences = differenceMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), root.getId()));
        String snapshot = ArrivalDifferenceScopeCodec.serialize(command.scope());
        if (differences.stream().anyMatch(value -> "OPEN".equals(value.getResolutionStatus())
                && snapshot.equals(value.getScopeSnapshot()))) {
            throw new IllegalStateException("duplicate current open difference scope");
        }
        EvidenceRef evidence = appendEvidence(root, command.evidenceRevision(),
                command.expectedVersion() + 1L, command.actorUserId());
        int differenceNo = differences.stream().map(ArrivalDifferenceDO::getDifferenceNo)
                .max(Integer::compareTo).orElse(0) + 1;
        ArrivalDifferenceDO inserted = difference(root, line.getId(), differenceNo, 1,
                command.differenceTypeCode(), "OPEN", command.reason(), command.riskDescription(),
                snapshot, evidence, command.actorUserId());
        if (differenceMapper.insert(inserted) != 1) throw new IllegalStateException("difference insert failed");
        if (acceptanceMapper.mutateDraftIfMatch(new ArrivalDraftMutation(command.tenantId(), root.getId(),
                command.expectedVersion(), null, null, null, command.actorUserId())) != 1) {
            throw new VersionConflictException();
        }
        return new ArrivalAcceptanceCommands.CommandResult(root.getId(), inserted.getId(), differenceNo, 1,
                "OPEN", "DRAFT", command.expectedVersion() + 1, null, command.scope());
    }

    private ArrivalAcceptanceCommands.CommandResult resolveOnce(
            ArrivalAcceptanceCommands.ResolveDifferenceCommand command) {
        ArrivalAcceptanceDO root = acceptanceMapper.selectForUpdate(
                new ArrivalRowQuery(command.tenantId(), command.arrivalAcceptanceId()));
        if (root == null || !Objects.equals(root.getVersion(), command.expectedVersion())) {
            throw new VersionConflictException();
        }
        if ("CONFIRMED".equals(root.getStatus())) {
            throw new BlockedBySpecException("successor batchCode allocation is not specified");
        }
        if (!"DIFFERENCE_PENDING".equals(root.getStatus())) {
            throw new IllegalStateException("arrival batch is not difference-pending");
        }
        lockOwners(root, command.actorUserId(), true);
        List<ArrivalLineDO> lines = lineMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), root.getId()));
        List<ArrivalDifferenceDO> differences = differenceMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), root.getId()));
        ArrivalAcceptanceCommands.Resolution resolution = command.resolution();
        ArrivalDifferenceDO current = differences.stream()
                .filter(value -> value.getId().equals(resolution.differenceId()))
                .findFirst().orElseThrow(DifferenceVersionConflictException::new);
        if (!Objects.equals(current.getRevisionNo(), resolution.expectedDifferenceRevision())
                || !Objects.equals(current.getVersion(), resolution.expectedDifferenceVersion())
                || !"OPEN".equals(current.getResolutionStatus())) {
            throw new DifferenceVersionConflictException();
        }
        ArrivalLineDO line = findCurrentLine(current, lines);
        EvidenceRef evidence = appendEvidence(root, resolution.evidenceRevision(),
                command.expectedVersion() + 1L, command.actorUserId());
        ResolutionOutcome outcome = resolutionOutcome(command, current, line, evidence);
        if (differenceMapper.clearCurrentIfMatch(new ArrivalChildRevisionMutation(command.tenantId(), root.getId(),
                current.getId(), current.getVersion(), command.actorUserId())) != 1) {
            throw new DifferenceVersionConflictException();
        }
        ArrivalDifferenceDO inserted = difference(root, outcome.line().getId(), current.getDifferenceNo(),
                current.getRevisionNo() + 1, current.getDifferenceType(), outcome.status(), resolution.reason(),
                outcome.riskDescription(), ArrivalDifferenceScopeCodec.serialize(outcome.scope()), evidence,
                command.actorUserId());
        if (resolution instanceof ArrivalAcceptanceCommands.Exempt exempt) {
            inserted.setApprovedBy(command.actorUserId());
            inserted.setApprovedAt(LocalDateTime.now(clock));
            inserted.setExemptionExpiresAt(exempt.expiresAt());
        }
        if (differenceMapper.insert(inserted) != 1) throw new IllegalStateException("resolution insert failed");
        List<ArrivalDifferenceDO> projected = new ArrayList<>(differences);
        projected.remove(current);
        projected.add(inserted);
        List<ArrivalLineDO> projectedLines = new ArrayList<>(lines);
        if (outcome.line() != line) {
            projectedLines.remove(line);
            projectedLines.add(outcome.line());
        }
        String candidate = candidateStatus(projectedLines, projected);
        if (acceptanceMapper.resolveDifferenceIfMatch(new ArrivalResolutionMutation(
                command.tenantId(), root.getId(), command.expectedVersion(), root.getStatus(), candidate,
                evidence.evidenceId(), evidence.revisionNo(), command.actorUserId())) != 1) {
            throw new VersionConflictException();
        }
        return new ArrivalAcceptanceCommands.CommandResult(root.getId(), inserted.getId(),
                inserted.getDifferenceNo(), inserted.getRevisionNo(), inserted.getResolutionStatus(), candidate,
                command.expectedVersion() + 1, null, outcome.remaining());
    }

    private ResolutionOutcome resolutionOutcome(ArrivalAcceptanceCommands.ResolveDifferenceCommand command,
                                                ArrivalDifferenceDO current, ArrivalLineDO line,
                                                EvidenceRef evidence) {
        ArrivalDifferenceScopeCodec.Scope original = ArrivalDifferenceScopeCodec.parse(current.getScopeSnapshot());
        ArrivalAcceptanceCommands.Resolution resolution = command.resolution();
        if (resolution instanceof ArrivalAcceptanceCommands.Supplement supplement) {
            SupplementOutcome supplementOutcome = supplement(line, original,
                    supplement.supplementScope(), command.actorUserId());
            ArrivalDifferenceScopeCodec.Scope remaining = supplementOutcome.remaining();
            return new ResolutionOutcome(remaining == null ? "SUPPLEMENTED" : "OPEN",
                    current.getRiskDescription(), remaining == null ? original : remaining, remaining,
                    supplementOutcome.line());
        }
        if (resolution instanceof ArrivalAcceptanceCommands.KeepRejected) {
            ArrivalLineDO appended = appendLineRevision(line, line.getAcceptedQuantity(), "REJECTED",
                    command.actorUserId());
            return new ResolutionOutcome("REJECTED", current.getRiskDescription(), original, original, appended);
        }
        if (resolution instanceof ArrivalAcceptanceCommands.Exempt exempt) {
            if (!exempt.expiresAt().isAfter(LocalDateTime.now(clock))) {
                throw new IllegalArgumentException("exemption expiry must be in the future");
            }
            return new ResolutionOutcome("EXEMPTED", exempt.riskDescription(), original, null, line);
        }
        return new ResolutionOutcome("CLOSED", current.getRiskDescription(), original, original, line);
    }

    private SupplementOutcome supplement(ArrivalLineDO line,
                                         ArrivalDifferenceScopeCodec.Scope original,
                                         ArrivalDifferenceScopeCodec.Scope supplied,
                                         Long actorUserId) {
        if (original instanceof ArrivalDifferenceScopeCodec.DeviceScope device) {
            if (!device.equals(supplied)) throw new IllegalArgumentException("device supplement must be whole");
            return new SupplementOutcome(appendLineRevision(line, line.getExpectedQuantity(),
                    "ACCEPTED", actorUserId), null);
        }
        if (!(supplied instanceof ArrivalDifferenceScopeCodec.QuantityScope supplement)
                || !(original instanceof ArrivalDifferenceScopeCodec.QuantityScope quantity)
                || !sameQuantityIdentity(quantity, supplement)
                || supplement.quantity().compareTo(quantity.quantity()) > 0) {
            throw new IllegalArgumentException("quantity supplement is outside current remainder");
        }
        ArrivalLineDO appended = appendLineRevision(line,
                line.getAcceptedQuantity().add(supplement.quantity()), "ACCEPTED", actorUserId);
        java.math.BigDecimal remaining = quantity.quantity().subtract(supplement.quantity());
        ArrivalDifferenceScopeCodec.Scope remainingScope = remaining.signum() == 0 ? null
                : new ArrivalDifferenceScopeCodec.QuantityScope(quantity.orderLineId(), quantity.productCode(),
                        quantity.modelCode(), remaining, quantity.unitCode());
        return new SupplementOutcome(appended, remainingScope);
    }

    private void appendLineRevisions(ArrivalAcceptanceDO root, List<ArrivalLineDO> current,
                                     List<ArrivalAcceptanceCommands.DraftLine> commands, FrozenScope frozen,
                                     Long actorUserId) {
        Set<String> identities = new HashSet<>();
        current.forEach(line -> identities.add(lineIdentity(line)));
        int nextLineNo = current.stream().map(ArrivalLineDO::getLineNo).max(Integer::compareTo).orElse(0);
        for (ArrivalAcceptanceCommands.DraftLine command : commands) {
            String identity = lineIdentity(command);
            ArrivalLineDO previous = command.lineId() == null ? null : current.stream()
                    .filter(value -> value.getId().equals(command.lineId())).findFirst()
                    .orElseThrow(LineVersionConflictException::new);
            if (previous != null) identities.remove(lineIdentity(previous));
            if (!identities.add(identity)) throw new IllegalArgumentException("duplicate draft line scope");
            if (previous != null && !Objects.equals(previous.getVersion(), command.expectedLineVersion())) {
                throw new LineVersionConflictException();
            }
            ArrivalLineDO appended = buildLine(root, command, previous, frozen,
                    previous == null ? ++nextLineNo : previous.getLineNo(), actorUserId);
            if (previous != null && lineMapper.clearCurrentIfMatch(new ArrivalChildRevisionMutation(
                    root.getTenantId(), root.getId(), previous.getId(), previous.getVersion(),
                    actorUserId)) != 1) {
                throw new LineVersionConflictException();
            }
            if (lineMapper.insert(appended) != 1) throw new IllegalStateException("line revision insert failed");
        }
    }

    private ArrivalLineDO buildLine(ArrivalAcceptanceDO root, ArrivalAcceptanceCommands.DraftLine command,
                                    ArrivalLineDO previous, FrozenScope frozen, int lineNo,
                                    Long actorUserId) {
        ArrivalLineDO line = new ArrivalLineDO();
        line.setTenantId(root.getTenantId());
        line.setArrivalAcceptanceId(root.getId());
        line.setLineNo(lineNo);
        line.setLineRevision(previous == null ? 1 : previous.getLineRevision() + 1);
        if (command instanceof ArrivalAcceptanceCommands.DeviceDraftLine device) {
            DeviceScopeFactPort.DeviceFact fact = frozen.devices().stream()
                    .filter(value -> value.deviceId().equals(device.deviceId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("device is outside frozen scope"));
            line.setScopeType("DEVICE");
            line.setDeviceId(device.deviceId());
            line.setDeviceAssignmentVersion(fact.projectAssignmentVersion());
            line.setExpectedQuantity(java.math.BigDecimal.ONE);
            line.setAcceptedQuantity(device.received() ? java.math.BigDecimal.ONE : java.math.BigDecimal.ZERO);
            line.setUnit("台");
            line.setStatus(device.received() ? "ACCEPTED" : "NOT_ARRIVED");
        } else if (command instanceof ArrivalAcceptanceCommands.QuantityDraftLine quantity) {
            DeliveryScopePort.AssignedLine fact = frozen.lines().stream()
                    .filter(value -> value.orderLineId().equals(quantity.orderLineId())
                            && Objects.equals(value.productCode(), quantity.productCode())
                            && Objects.equals(value.modelCode(), quantity.modelCode())
                            && value.unitCode().equals(quantity.unitCode()) && value.serialNumbers().isEmpty())
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("quantity is outside frozen scope"));
            if (quantity.acceptedQuantity().compareTo(fact.assignedQuantity()) > 0) {
                throw new IllegalArgumentException("accepted quantity exceeds assigned scope");
            }
            line.setScopeType("ORDER_MODEL_QUANTITY");
            line.setOrderLineId(quantity.orderLineId());
            line.setProductCode(quantity.productCode());
            line.setModelCode(quantity.modelCode());
            line.setExpectedQuantity(fact.assignedQuantity());
            line.setAcceptedQuantity(quantity.acceptedQuantity());
            line.setUnit(quantity.unitCode());
            line.setStatus(quantity.acceptedQuantity().signum() == 0 ? "NOT_ARRIVED" : "ACCEPTED");
        }
        line.setCurrentMarker(1);
        line.setVersion(0);
        line.setCreator(String.valueOf(actorUserId));
        line.setUpdater(String.valueOf(actorUserId));
        return line;
    }

    private ArrivalLineDO appendLineRevision(ArrivalLineDO previous, java.math.BigDecimal accepted,
                                             String status, Long actorUserId) {
        if (lineMapper.clearCurrentIfMatch(new ArrivalChildRevisionMutation(previous.getTenantId(),
                previous.getArrivalAcceptanceId(), previous.getId(), previous.getVersion(), actorUserId)) != 1) {
            throw new LineVersionConflictException();
        }
        ArrivalLineDO next = new ArrivalLineDO();
        org.springframework.beans.BeanUtils.copyProperties(previous, next, "id", "lineRevision", "version",
                "creator", "createTime", "updater", "updateTime");
        next.setId(null);
        next.setLineRevision(previous.getLineRevision() + 1);
        next.setAcceptedQuantity(accepted);
        next.setStatus(status);
        next.setCurrentMarker(1);
        next.setVersion(0);
        next.setCreator(String.valueOf(actorUserId));
        next.setUpdater(String.valueOf(actorUserId));
        if (lineMapper.insert(next) != 1) throw new IllegalStateException("line revision insert failed");
        return next;
    }

    private EvidenceRef appendEvidence(ArrivalAcceptanceDO root, ArrivalAcceptanceCommands.FileRevision input,
                                       Long sourceVersion, Long actorUserId) {
        FileArtifactVersionFact fact = filePort.lockAndRevalidateArrivalEvidence(
                new FileArtifactFactPort.ArrivalEvidenceExpectation(input.artifactId(), input.versionNo(),
                        root.getId(), input.referenceKey(), input.fileFactVersion(), input.scopeVersion()));
        if (fact == null || !input.artifactId().equals(fact.artifactId())
                || !input.versionNo().equals(fact.versionNo()) || !input.referenceKey().equals(fact.referenceKey())
                || !input.scopeVersion().equals(fact.scopeVersion())
                || !input.fileFactVersion().equals(fact.fileFactVersion()) || !input.hash().equals(fact.sha256())) {
            throw new IllegalStateException("file artifact fact is unavailable or mismatched");
        }
        DeliveryEvidenceSourceQuery query = new DeliveryEvidenceSourceQuery(
                root.getTenantId(), "EXE-01", "ARRIVAL_ACCEPTANCE", root.getId());
        DeliveryEvidenceDO evidence = evidenceMapper.selectBySourceForUpdate(query);
        int revisionNo;
        if (evidence == null) {
            evidence = new DeliveryEvidenceDO();
            evidence.setTenantId(root.getTenantId());
            evidence.setProjectId(root.getProjectId());
            evidence.setSourceRequirement("EXE-01");
            evidence.setSourceObjectType("ARRIVAL_ACCEPTANCE");
            evidence.setSourceObjectId(root.getId());
            evidence.setCurrentRevisionNo(1);
            evidence.setAccSyncStatus("NOT_PUBLISHED");
            evidence.setAccRetryCount(0);
            evidence.setVersion(0);
            evidence.setCreator(String.valueOf(actorUserId));
            evidence.setUpdater(String.valueOf(actorUserId));
            if (evidenceMapper.insert(evidence) != 1 || evidence.getId() == null) {
                throw new IllegalStateException("evidence root insert failed");
            }
            revisionNo = 1;
        } else {
            if (!"NOT_PUBLISHED".equals(evidence.getAccSyncStatus()) || evidence.getCurrentRevisionNo() == null
                    || evidence.getVersion() == null) throw new IllegalStateException("evidence is immutable");
            revisionNo = evidence.getCurrentRevisionNo() + 1;
            if (evidenceMapper.advanceRevisionIfMatch(new DeliveryEvidenceRevisionAdvance(root.getTenantId(),
                    evidence.getId(), evidence.getCurrentRevisionNo(), evidence.getVersion(), actorUserId)) != 1) {
                throw new VersionConflictException();
            }
        }
        DeliveryEvidenceRevisionDO revision = new DeliveryEvidenceRevisionDO();
        revision.setTenantId(root.getTenantId());
        revision.setEvidenceId(evidence.getId());
        revision.setRevisionNo(revisionNo);
        revision.setFileArtifactId(input.artifactId());
        revision.setFileReferenceId(input.referenceKey());
        revision.setFileVersionNo(input.versionNo());
        revision.setFileScopeVersion(input.scopeVersion());
        revision.setFileFactVersion(JsonUtils.toJsonString(input.fileFactVersion()));
        revision.setFileHash(input.hash());
        revision.setSourceRecordId(root.getId());
        revision.setSourceVersion(sourceVersion);
        revision.setCreator(String.valueOf(actorUserId));
        if (revisionMapper.insert(revision) != 1) throw new IllegalStateException("evidence revision insert failed");
        return new EvidenceRef(evidence.getId(), revisionNo);
    }

    private FrozenScope lockOwners(ArrivalAcceptanceDO root, Long actorUserId, boolean requireManager) {
        projectPort.lockAndRevalidate(new ProjectQualificationPort.RevalidationCommand(
                root.getTenantId(), root.getProjectId(), requireManager ? actorUserId : null, actorUserId,
                root.getProjectVersion(), root.getProjectParticipantFactVersion(), root.getProjectScopeVersion(),
                requireManager));
        ExpectedScopeSnapshot expected = JsonUtils.parseObject(root.getExpectedScopeSnapshot(),
                ExpectedScopeSnapshot.class);
        DeliveryScopePort.AssignedScope delivery = deliveryPort.lockAndRevalidate(
                root.getProjectId(), root.getDeliveryScopeVersion());
        if (!orderedLines(delivery.lines()).equals(orderedLines(expected.deliveryLines()))) {
            throw new IllegalStateException("delivery scope changed without version change");
        }
        List<DeviceScopeFactPort.ExpectedDeviceFact> expectation = expected.devices().stream()
                .map(device -> new DeviceScopeFactPort.ExpectedDeviceFact(device.deviceId(),
                        device.serialNumber(), device.projectAssignmentVersion())).toList();
        DeviceScopeFactPort.DeviceScopeFact devices = devicePort.lockAndRevalidate(
                root.getTenantId(), root.getProjectId(), expectation);
        if (!orderedDevices(devices.devices()).equals(orderedDevices(expected.devices()))) {
            throw new IllegalStateException("device scope changed without version change");
        }
        return new FrozenScope(delivery.lines(), devices.devices());
    }

    private ArrivalAcceptanceDO lockOwnedDraft(Long tenantId, Long id, Long actor, Integer version) {
        ArrivalAcceptanceDO root = acceptanceMapper.selectForUpdate(new ArrivalRowQuery(tenantId, id));
        if (root == null || !"DRAFT".equals(root.getStatus())
                || !String.valueOf(actor).equals(root.getCreator())) throw new IllegalStateException("draft is not editable");
        if (!Objects.equals(root.getVersion(), version)) throw new VersionConflictException();
        return root;
    }

    private static void requireScopeWithinLine(ArrivalDifferenceScopeCodec.Scope scope, ArrivalLineDO line) {
        if (scope instanceof ArrivalDifferenceScopeCodec.DeviceScope device) {
            if (!"DEVICE".equals(line.getScopeType()) || !device.deviceId().equals(line.getDeviceId())) {
                throw new IllegalArgumentException("difference device is outside line");
            }
        } else if (scope instanceof ArrivalDifferenceScopeCodec.QuantityScope quantity) {
            if (!"ORDER_MODEL_QUANTITY".equals(line.getScopeType())
                    || !quantity.orderLineId().equals(line.getOrderLineId())
                    || !Objects.equals(quantity.productCode(), line.getProductCode())
                    || !Objects.equals(quantity.modelCode(), line.getModelCode())
                    || !quantity.unitCode().equals(line.getUnit())
                    || quantity.quantity().compareTo(line.getExpectedQuantity()) > 0) {
                throw new IllegalArgumentException("difference quantity is outside line");
            }
        }
    }

    private static ArrivalDifferenceDO difference(ArrivalAcceptanceDO root, Long lineId, int differenceNo,
                                                   int revisionNo, String type, String status, String reason,
                                                   String risk, String scope, EvidenceRef evidence, Long actor) {
        ArrivalDifferenceDO row = new ArrivalDifferenceDO();
        row.setTenantId(root.getTenantId());
        row.setArrivalAcceptanceId(root.getId());
        row.setArrivalLineId(lineId);
        row.setDifferenceNo(differenceNo);
        row.setRevisionNo(revisionNo);
        row.setDifferenceType(type);
        row.setResolutionStatus(status);
        row.setReason(reason);
        row.setRiskDescription(risk);
        row.setScopeSnapshot(scope);
        row.setEvidenceId(evidence.evidenceId());
        row.setEvidenceRevision(evidence.revisionNo());
        row.setCurrentMarker(1);
        row.setVersion(0);
        row.setCreator(String.valueOf(actor));
        row.setUpdater(String.valueOf(actor));
        return row;
    }

    private static String candidateStatus(List<ArrivalLineDO> lines, List<ArrivalDifferenceDO> differences) {
        if (differences.stream().anyMatch(value -> "OPEN".equals(value.getResolutionStatus()))) {
            return "DIFFERENCE_PENDING";
        }
        boolean accepted = lines.stream().allMatch(line -> isLineSatisfied(line, differences));
        return accepted ? "ACCEPTED" : "PARTIALLY_ACCEPTED";
    }

    private static boolean isLineSatisfied(ArrivalLineDO line, List<ArrivalDifferenceDO> differences) {
        java.math.BigDecimal covered = line.getAcceptedQuantity();
        for (ArrivalDifferenceDO difference : differences) {
            if (!"EXEMPTED".equals(difference.getResolutionStatus())) continue;
            ArrivalDifferenceScopeCodec.Scope scope = ArrivalDifferenceScopeCodec.parse(
                    difference.getScopeSnapshot());
            if (scope instanceof ArrivalDifferenceScopeCodec.DeviceScope device) {
                if ("DEVICE".equals(line.getScopeType()) && device.deviceId().equals(line.getDeviceId())) {
                    covered = covered.add(java.math.BigDecimal.ONE);
                }
            } else if (scope instanceof ArrivalDifferenceScopeCodec.QuantityScope quantity
                    && "ORDER_MODEL_QUANTITY".equals(line.getScopeType())
                    && quantity.orderLineId().equals(line.getOrderLineId())
                    && Objects.equals(quantity.productCode(), line.getProductCode())
                    && Objects.equals(quantity.modelCode(), line.getModelCode())
                    && quantity.unitCode().equals(line.getUnit())) {
                covered = covered.add(quantity.quantity());
            }
        }
        if (covered.compareTo(line.getExpectedQuantity()) > 0) {
            throw new IllegalStateException("accepted and exempted scope exceeds arrival line");
        }
        return covered.compareTo(line.getExpectedQuantity()) == 0;
    }

    private static String lineIdentity(ArrivalAcceptanceCommands.DraftLine line) {
        return line instanceof ArrivalAcceptanceCommands.DeviceDraftLine device
                ? "D:" + device.deviceId()
                : "Q:" + ((ArrivalAcceptanceCommands.QuantityDraftLine) line).orderLineId();
    }

    private static String lineIdentity(ArrivalLineDO line) {
        return "DEVICE".equals(line.getScopeType()) ? "D:" + line.getDeviceId() : "Q:" + line.getOrderLineId();
    }

    private static boolean sameQuantityIdentity(ArrivalDifferenceScopeCodec.QuantityScope left,
                                                ArrivalDifferenceScopeCodec.QuantityScope right) {
        return left.orderLineId().equals(right.orderLineId())
                && Objects.equals(left.productCode(), right.productCode())
                && Objects.equals(left.modelCode(), right.modelCode())
                && left.unitCode().equals(right.unitCode());
    }

    private static ArrivalLineDO findCurrentLine(ArrivalDifferenceDO difference, List<ArrivalLineDO> lines) {
        List<ArrivalLineDO> direct = lines.stream()
                .filter(line -> Objects.equals(line.getId(), difference.getArrivalLineId())).toList();
        if (direct.size() == 1) return direct.getFirst();
        if (direct.size() > 1) throw new IllegalStateException("duplicate current arrival line id");
        ArrivalDifferenceScopeCodec.Scope scope = ArrivalDifferenceScopeCodec.parse(difference.getScopeSnapshot());
        List<ArrivalLineDO> matching = lines.stream().filter(line -> matchesScopeIdentity(scope, line)).toList();
        if (matching.size() != 1) throw new LineVersionConflictException();
        return matching.getFirst();
    }

    private static boolean matchesScopeIdentity(ArrivalDifferenceScopeCodec.Scope scope, ArrivalLineDO line) {
        if (scope instanceof ArrivalDifferenceScopeCodec.DeviceScope device) {
            return "DEVICE".equals(line.getScopeType()) && device.deviceId().equals(line.getDeviceId());
        }
        ArrivalDifferenceScopeCodec.QuantityScope quantity = (ArrivalDifferenceScopeCodec.QuantityScope) scope;
        return "ORDER_MODEL_QUANTITY".equals(line.getScopeType())
                && quantity.orderLineId().equals(line.getOrderLineId())
                && Objects.equals(quantity.productCode(), line.getProductCode())
                && Objects.equals(quantity.modelCode(), line.getModelCode())
                && quantity.unitCode().equals(line.getUnit());
    }

    private static List<DeliveryScopePort.AssignedLine> orderedLines(List<DeliveryScopePort.AssignedLine> lines) {
        return lines.stream().sorted(Comparator.comparing(DeliveryScopePort.AssignedLine::orderLineId)).toList();
    }

    private static List<DeviceScopeFactPort.DeviceFact> orderedDevices(List<DeviceScopeFactPort.DeviceFact> devices) {
        return devices.stream().sorted(Comparator.comparing(DeviceScopeFactPort.DeviceFact::deviceId)).toList();
    }

    private static String digest(Object command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(command).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static PlatformCommandExecutionApi.SuccessFacts successFacts(
            String operation, ArrivalAcceptanceCommands.CommandResult response) {
        return new PlatformCommandExecutionApi.SuccessFacts(operation, "ArrivalAcceptance",
                String.valueOf(response.arrivalAcceptanceId()), null,
                JsonUtils.toJsonString(response), List.of());
    }

    private static ArrivalAcceptanceCommands.CommandResult requireCompleted(
            PlatformCommandExecutionApi.ExecutionResult<ArrivalAcceptanceCommands.CommandResult> result) {
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new IdempotencyConflictException();
        }
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw new IdempotencyInProgressException();
        }
        return result.response();
    }

    private record ExpectedScopeSnapshot(List<DeliveryScopePort.AssignedLine> deliveryLines,
                                         List<DeviceScopeFactPort.DeviceFact> devices) {
    }

    private record FrozenScope(List<DeliveryScopePort.AssignedLine> lines,
                               List<DeviceScopeFactPort.DeviceFact> devices) {
    }

    private record EvidenceRef(Long evidenceId, Integer revisionNo) {
    }

    private record ResolutionOutcome(String status, String riskDescription,
                                     ArrivalDifferenceScopeCodec.Scope scope,
                                     ArrivalDifferenceScopeCodec.Scope remaining,
                                     ArrivalLineDO line) {
    }

    private record SupplementOutcome(ArrivalLineDO line,
                                     ArrivalDifferenceScopeCodec.Scope remaining) {
    }

    public static class VersionConflictException extends RuntimeException {
    }
    public static final class LineVersionConflictException extends VersionConflictException {
    }
    public static final class DifferenceVersionConflictException extends VersionConflictException {
    }
    public static final class IdempotencyConflictException extends RuntimeException {
    }
    public static final class IdempotencyInProgressException extends RuntimeException {
    }
    public static final class BlockedBySpecException extends RuntimeException {
        public BlockedBySpecException(String message) { super(message); }
    }
}
