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
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalDueExemptionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalPredecessorQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactVersionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalResolutionMutation;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceIdentityQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRevisionAdvance;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRevisionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceSourceQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalDifferenceScopeCodec;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ArrivalDifferenceTypePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectSystemQualificationPort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
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
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final ProjectSystemQualificationPort systemProjectPort;
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
                                           ProjectSystemQualificationPort systemProjectPort,
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
        this.systemProjectPort = Objects.requireNonNull(systemProjectPort);
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
                "DRAFT", command.expectedVersion() + 1, null, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArrivalAcceptanceCommands.CommandResult raiseDifference(
            ArrivalAcceptanceCommands.RaiseDifferenceCommand command) {
        PlatformCommandExecutionApi.ExecutionResult<ArrivalAcceptanceCommands.CommandResult> result =
                commandApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                                command.tenantId(), "IMP:ARRIVAL_RAISE_DIFFERENCE:" + command.arrivalAcceptanceId(),
                                command.actorUserId(), command.idempotencyKey()),
                        digestExcludingCorrelation(command), ArrivalAcceptanceCommands.CommandResult.class,
                        () -> raiseOnce(command), response -> successFacts(
                                "ARRIVAL_DIFFERENCE_RAISE", command.correlationId(), response));
        return requireCompleted(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArrivalAcceptanceCommands.CommandResult resolveDifference(
            ArrivalAcceptanceCommands.ResolveDifferenceCommand command) {
        PlatformCommandExecutionApi.ExecutionResult<ArrivalAcceptanceCommands.CommandResult> result =
                commandApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                                command.tenantId(), "IMP:ARRIVAL_RESOLVE_DIFFERENCE:" + command.arrivalAcceptanceId(),
                                command.actorUserId(), command.idempotencyKey()),
                        digestExcludingCorrelation(command), ArrivalAcceptanceCommands.CommandResult.class,
                        () -> resolveOnce(command), response -> successFacts(
                                "ARRIVAL_DIFFERENCE_RESOLVE", command.correlationId(), response));
        return requireCompleted(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ArrivalAcceptanceCommands.CommandResult> expireExemptions(
            ArrivalAcceptanceCommands.ExpireArrivalExemptionsCommand command) {
        LocalDateTime processingTime = LocalDateTime.now(clock);
        List<ArrivalDifferenceDO> due = differenceMapper.selectDueExemptions(
                new ArrivalDueExemptionQuery(processingTime, command.pageSize()));
        List<ArrivalAcceptanceCommands.CommandResult> results = new ArrayList<>();
        for (ArrivalDifferenceDO difference : due) {
            String key = difference.getTenantId() + ":" + difference.getArrivalAcceptanceId() + ":"
                    + difference.getDifferenceNo() + ":" + difference.getRevisionNo()
                    + ":EXEMPTION_INVALIDATION";
            PlatformCommandExecutionApi.ExecutionResult<ArrivalAcceptanceCommands.CommandResult> execution =
                    commandApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                                    difference.getTenantId(), "IMP:ARRIVAL_EXEMPTION_INVALIDATION",
                                    0L, key), digest(Map.of("key", key)),
                            ArrivalAcceptanceCommands.CommandResult.class,
                            () -> expireOnce(difference, processingTime),
                            response -> successFacts("ARRIVAL_EXEMPTION_INVALIDATION", key, response));
            results.add(requireCompleted(execution));
        }
        return List.copyOf(results);
    }

    private ArrivalAcceptanceCommands.CommandResult expireOnce(ArrivalDifferenceDO claimed,
                                                                 LocalDateTime processingTime) {
        ArrivalAcceptanceDO observed = acceptanceMapper.selectRow(new ArrivalRowQuery(
                claimed.getTenantId(), claimed.getArrivalAcceptanceId()));
        if (observed == null || !"CONFIRMED".equals(observed.getStatus())
                || claimed.getApprovedBy() == null || claimed.getApprovedBy() <= 0) {
            throw new StateConflictException();
        }
        ProjectSystemQualificationPort.CurrentProjectQualification project =
                systemProjectPort.lockCurrent(observed.getTenantId(), observed.getProjectId());
        ArrivalAcceptanceDO source = acceptanceMapper.selectForUpdate(new ArrivalRowQuery(
                claimed.getTenantId(), claimed.getArrivalAcceptanceId()));
        if (!sameLockedSource(observed, source) || !"CONFIRMED".equals(source.getStatus())) {
            throw new StateConflictException();
        }
        List<ArrivalLineDO> sourceLines = lineMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(source.getTenantId(), source.getId()));
        List<ArrivalDifferenceDO> sourceDifferences = differenceMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(source.getTenantId(), source.getId()));
        ArrivalDifferenceDO current = sourceDifferences.stream()
                .filter(value -> Objects.equals(value.getId(), claimed.getId()))
                .findFirst().orElseThrow(DifferenceVersionConflictException::new);
        if (!Objects.equals(current.getRevisionNo(), claimed.getRevisionNo())
                || !Objects.equals(current.getVersion(), claimed.getVersion())
                || !"EXEMPTED".equals(current.getResolutionStatus())
                || current.getExemptionExpiresAt() == null
                || current.getExemptionExpiresAt().isAfter(processingTime)
                || !Objects.equals(current.getApprovedBy(), claimed.getApprovedBy())) {
            throw new DifferenceVersionConflictException();
        }
        lockScopeOwners(source);
        lockStoredEvidence(source, current);
        SuccessorContext successor = createSuccessor(source, sourceLines, sourceDifferences,
                "EXEMPTION_INVALIDATION", 0L, project);
        ArrivalDifferenceDO copied = successor.differencesByNumber().get(current.getDifferenceNo());
        ArrivalLineDO copiedLine = successor.linesBySourceId().get(current.getArrivalLineId());
        if (copied == null || copiedLine == null) throw new IllegalStateException("successor copy is incomplete");
        if (differenceMapper.clearCurrentIfMatch(new ArrivalChildRevisionMutation(source.getTenantId(),
                successor.root().getId(), copied.getId(), copied.getVersion(), 0L)) != 1) {
            throw new DifferenceVersionConflictException();
        }
        Long currentMax = acceptanceMapper.selectMaxAllocatedProjectFactVersion(
                new ArrivalProjectFactVersionQuery(source.getTenantId(), source.getProjectId()));
        long factVersion = currentMax == null ? 1L : Math.addExact(currentMax, 1L);
        ArrivalDifferenceDO invalidation = difference(successor.root(), copiedLine.getId(),
                copied.getDifferenceNo(), copied.getRevisionNo() + 1, copied.getDifferenceType(), "OPEN",
                "豁免到期", copied.getRiskDescription(), copied.getScopeSnapshot(),
                new EvidenceRef(copied.getEvidenceId(), copied.getEvidenceRevision()), 0L);
        invalidation.setFactImpactType("EXEMPTION_INVALIDATION");
        invalidation.setProjectFactVersion(factVersion);
        if (differenceMapper.insert(invalidation) != 1) {
            throw new IllegalStateException("exemption invalidation insert failed");
        }
        return new ArrivalAcceptanceCommands.CommandResult(source.getId(), invalidation.getId(),
                invalidation.getDifferenceNo(), invalidation.getRevisionNo(), "OPEN", "DRAFT", 0,
                successor.root().getId(), factVersion, "EXEMPTION_INVALIDATION",
                ArrivalDifferenceScopeCodec.parse(invalidation.getScopeSnapshot()));
    }

    private void lockStoredEvidence(ArrivalAcceptanceDO source, ArrivalDifferenceDO difference) {
        if (difference.getEvidenceId() == null || difference.getEvidenceRevision() == null) {
            throw new IllegalStateException("exemption evidence is unavailable");
        }
        DeliveryEvidenceRevisionDO revision = revisionMapper.selectRevision(
                new DeliveryEvidenceRevisionQuery(source.getTenantId(), difference.getEvidenceId(),
                        difference.getEvidenceRevision()));
        if (revision == null || revision.getSourceRecordId() == null || revision.getSourceRecordId() <= 0) {
            throw new IllegalStateException("exemption evidence revision is mismatched");
        }
        DeliveryEvidenceDO evidence = evidenceMapper.selectByIdentityForUpdate(
                new DeliveryEvidenceIdentityQuery(source.getTenantId(), difference.getEvidenceId()));
        if (evidence == null || !Objects.equals(source.getTenantId(), evidence.getTenantId())
                || !Objects.equals(source.getProjectId(), evidence.getProjectId())
                || !"EXE-01".equals(evidence.getSourceRequirement())
                || !"ARRIVAL_ACCEPTANCE".equals(evidence.getSourceObjectType())
                || !Objects.equals(revision.getSourceRecordId(), evidence.getSourceObjectId())) {
            throw new IllegalStateException("exemption evidence root is mismatched");
        }
        requireEvidenceSourceInLineage(source, revision.getSourceRecordId());
        FileFactVersion factVersion = JsonUtils.parseObject(revision.getFileFactVersion(), FileFactVersion.class);
        FileArtifactVersionFact current = filePort.lockAndRevalidateArrivalEvidence(
                new FileArtifactFactPort.ArrivalEvidenceExpectation(revision.getFileArtifactId(),
                        revision.getFileVersionNo(), revision.getSourceRecordId(), revision.getFileReferenceId(),
                        factVersion, revision.getFileScopeVersion()));
        if (current == null || !revision.getFileArtifactId().equals(current.artifactId())
                || !revision.getFileVersionNo().equals(current.versionNo())
                || !revision.getFileReferenceId().equals(current.referenceKey())
                || !factVersion.equals(current.fileFactVersion())
                || !revision.getFileScopeVersion().equals(current.scopeVersion())
                || !revision.getFileHash().equals(current.sha256())) {
            throw new IllegalStateException("exemption evidence fact is stale or mismatched");
        }
    }

    private void requireEvidenceSourceInLineage(ArrivalAcceptanceDO source, Long evidenceSourceId) {
        ArrivalAcceptanceDO cursor = source;
        Set<Long> visited = new HashSet<>();
        while (true) {
            if (cursor.getId() == null || !visited.add(cursor.getId())
                    || !Objects.equals(source.getTenantId(), cursor.getTenantId())
                    || !Objects.equals(source.getProjectId(), cursor.getProjectId())
                    || !Objects.equals(source.getBatchCode(), cursor.getBatchCode())) {
                throw new IllegalStateException("arrival acceptance predecessor lineage is invalid");
            }
            if (Objects.equals(cursor.getId(), evidenceSourceId)) return;
            if (cursor.getPredecessorAcceptanceId() == null) {
                throw new IllegalStateException("evidence source is not an arrival acceptance ancestor");
            }
            Long predecessorId = cursor.getPredecessorAcceptanceId();
            cursor = acceptanceMapper.selectRow(new ArrivalRowQuery(source.getTenantId(), predecessorId));
            if (cursor == null || !Objects.equals(predecessorId, cursor.getId())) {
                throw new IllegalStateException("arrival acceptance predecessor lineage is broken");
            }
        }
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
                "OPEN", "DRAFT", command.expectedVersion() + 1, null, null, null, command.scope());
    }

    private ArrivalAcceptanceCommands.CommandResult resolveOnce(
            ArrivalAcceptanceCommands.ResolveDifferenceCommand command) {
        ArrivalAcceptanceDO root = acceptanceMapper.selectForUpdate(
                new ArrivalRowQuery(command.tenantId(), command.arrivalAcceptanceId()));
        if (root == null || !Objects.equals(root.getVersion(), command.expectedVersion())) {
            throw new VersionConflictException();
        }
        if (command.resolution() instanceof ArrivalAcceptanceCommands.CorrectInformation correction) {
            return correctInformation(root, command, correction);
        }
        if ("CONFIRMED".equals(root.getStatus())) {
            return resolveConfirmed(root, command,
                    (ArrivalAcceptanceCommands.DifferenceResolution) command.resolution());
        }
        if (!"DIFFERENCE_PENDING".equals(root.getStatus())) {
            throw new IllegalStateException("arrival batch is not difference-pending");
        }
        lockOwners(root, command.actorUserId(), true);
        List<ArrivalLineDO> lines = lineMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), root.getId()));
        List<ArrivalDifferenceDO> differences = differenceMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), root.getId()));
        ArrivalAcceptanceCommands.DifferenceResolution resolution =
                (ArrivalAcceptanceCommands.DifferenceResolution) command.resolution();
        ArrivalDifferenceDO current = differences.stream()
                .filter(value -> value.getId().equals(resolution.differenceId()))
                .findFirst().orElseThrow(DifferenceVersionConflictException::new);
        requireExpectedDifference(current, resolution.expectedDifferenceRevision(),
                resolution.expectedDifferenceVersion());
        if (!"OPEN".equals(current.getResolutionStatus())) throw new StateConflictException();
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
                command.expectedVersion() + 1, null, null, null, outcome.remaining());
    }

    private ArrivalAcceptanceCommands.CommandResult resolveConfirmed(
            ArrivalAcceptanceDO source, ArrivalAcceptanceCommands.ResolveDifferenceCommand command,
            ArrivalAcceptanceCommands.DifferenceResolution resolution) {
        lockOwners(source, command.actorUserId(), true);
        List<ArrivalLineDO> sourceLines = lineMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), source.getId()));
        List<ArrivalDifferenceDO> sourceDifferences = differenceMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), source.getId()));
        ArrivalDifferenceDO sourceDifference = requireConfirmedDifference(sourceDifferences, resolution);
        SuccessorContext successor = createSuccessor(source, sourceLines, sourceDifferences,
                resolution instanceof ArrivalAcceptanceCommands.Supplement
                        ? "SUPPLEMENT" : "DIFFERENCE_CLOSURE", command.actorUserId());
        ArrivalDifferenceDO current = successor.differencesByNumber().get(sourceDifference.getDifferenceNo());
        ArrivalLineDO line = successor.linesBySourceId().get(sourceDifference.getArrivalLineId());
        if (current == null || line == null) throw new IllegalStateException("successor copy is incomplete");
        EvidenceRef evidence = appendEvidence(successor.root(), resolution.evidenceRevision(), 1L,
                command.actorUserId());
        ResolutionOutcome outcome = resolutionOutcome(command, current, line, evidence);
        if (differenceMapper.clearCurrentIfMatch(new ArrivalChildRevisionMutation(command.tenantId(),
                successor.root().getId(), current.getId(), current.getVersion(), command.actorUserId())) != 1) {
            throw new DifferenceVersionConflictException();
        }
        ArrivalDifferenceDO inserted = difference(successor.root(), outcome.line().getId(),
                current.getDifferenceNo(), current.getRevisionNo() + 1, current.getDifferenceType(),
                outcome.status(), resolution.reason(), outcome.riskDescription(),
                ArrivalDifferenceScopeCodec.serialize(outcome.scope()), evidence, command.actorUserId());
        if (resolution instanceof ArrivalAcceptanceCommands.Exempt exempt) {
            inserted.setApprovedBy(command.actorUserId());
            inserted.setApprovedAt(LocalDateTime.now(clock));
            inserted.setExemptionExpiresAt(exempt.expiresAt());
        }
        if (differenceMapper.insert(inserted) != 1) throw new IllegalStateException("resolution insert failed");
        if (acceptanceMapper.mutateDraftIfMatch(new ArrivalDraftMutation(command.tenantId(),
                successor.root().getId(), 0, null, null, null, command.actorUserId())) != 1) {
            throw new VersionConflictException();
        }
        return new ArrivalAcceptanceCommands.CommandResult(source.getId(), inserted.getId(),
                inserted.getDifferenceNo(), inserted.getRevisionNo(), inserted.getResolutionStatus(), "DRAFT",
                1, successor.root().getId(), null, null, outcome.remaining());
    }

    private ArrivalAcceptanceCommands.CommandResult correctInformation(
            ArrivalAcceptanceDO source, ArrivalAcceptanceCommands.ResolveDifferenceCommand command,
            ArrivalAcceptanceCommands.CorrectInformation correction) {
        if (!"CONFIRMED".equals(source.getStatus())
                || !Objects.equals(correction.expectedSourceVersion(), source.getVersion())) {
            throw new VersionConflictException();
        }
        FrozenScope frozen = lockOwners(source, command.actorUserId(), true);
        List<ArrivalLineDO> sourceLines = lineMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), source.getId()));
        List<ArrivalDifferenceDO> sourceDifferences = differenceMapper.selectCurrentListForUpdate(
                new ArrivalChildrenQuery(command.tenantId(), source.getId()));
        SuccessorContext successor = createSuccessor(source, sourceLines, sourceDifferences,
                "CORRECTION", command.actorUserId());
        appendEvidence(successor.root(), correction.evidenceRevision(), 1L, command.actorUserId());
        ArrivalAcceptanceCommands.CorrectionPatch patch = correction.correctionPatch();
        if (patch.lines() != null) {
            List<ArrivalAcceptanceCommands.DraftLine> translated = patch.lines().stream()
                    .map(line -> translateCorrectionLine(line, successor))
                    .toList();
            appendLineRevisions(successor.root(), new ArrayList<>(successor.linesBySourceId().values()),
                    translated, frozen, command.actorUserId());
        }
        String signer = patch.signerName() == null ? null
                : JsonUtils.toJsonString(new ArrivalAcceptanceViews.SignerSnapshot(patch.signerName()));
        if (acceptanceMapper.mutateDraftIfMatch(new ArrivalDraftMutation(command.tenantId(),
                successor.root().getId(), 0, patch.logisticsNo(), patch.arrivedAt(), signer,
                command.actorUserId())) != 1) {
            throw new VersionConflictException();
        }
        return new ArrivalAcceptanceCommands.CommandResult(source.getId(), null, null, null, null,
                "DRAFT", 1, successor.root().getId(), null, null, null);
    }

    private static ArrivalAcceptanceCommands.DraftLine translateCorrectionLine(
            ArrivalAcceptanceCommands.DraftLine requested, SuccessorContext successor) {
        ArrivalLineDO copied = successor.linesBySourceId().get(requested.lineId());
        if (copied == null || !Objects.equals(requested.expectedLineVersion(),
                successor.sourceLineVersions().get(requested.lineId()))) {
            throw new LineVersionConflictException();
        }
        if (requested instanceof ArrivalAcceptanceCommands.DeviceDraftLine device) {
            return new ArrivalAcceptanceCommands.DeviceDraftLine(copied.getId(), copied.getVersion(),
                    device.deviceId(), device.received());
        }
        ArrivalAcceptanceCommands.QuantityDraftLine quantity =
                (ArrivalAcceptanceCommands.QuantityDraftLine) requested;
        return new ArrivalAcceptanceCommands.QuantityDraftLine(copied.getId(), copied.getVersion(),
                quantity.orderLineId(), quantity.productCode(), quantity.modelCode(),
                quantity.acceptedQuantity(), quantity.unitCode());
    }

    private ArrivalDifferenceDO requireConfirmedDifference(List<ArrivalDifferenceDO> differences,
                                                           ArrivalAcceptanceCommands.DifferenceResolution resolution) {
        if (differences.stream().anyMatch(value -> "OPEN".equals(value.getResolutionStatus()))) {
            throw new StateConflictException();
        }
        ArrivalDifferenceDO current = differences.stream()
                .filter(value -> value.getId().equals(resolution.differenceId()))
                .findFirst().orElseThrow(DifferenceVersionConflictException::new);
        requireExpectedDifference(current, resolution.expectedDifferenceRevision(),
                resolution.expectedDifferenceVersion());
        if (!"REJECTED".equals(current.getResolutionStatus())
                || resolution instanceof ArrivalAcceptanceCommands.KeepRejected) {
            throw new StateConflictException();
        }
        return current;
    }

    private SuccessorContext createSuccessor(ArrivalAcceptanceDO source, List<ArrivalLineDO> sourceLines,
                                             List<ArrivalDifferenceDO> sourceDifferences,
                                             String reason, Long actorUserId) {
        return createSuccessor(source, sourceLines, sourceDifferences, reason, actorUserId, null);
    }

    private SuccessorContext createSuccessor(ArrivalAcceptanceDO source, List<ArrivalLineDO> sourceLines,
                                             List<ArrivalDifferenceDO> sourceDifferences,
                                             String reason, Long actorUserId,
                                             ProjectSystemQualificationPort.CurrentProjectQualification project) {
        if (acceptanceMapper.selectSuccessorForUpdate(new ArrivalPredecessorQuery(
                source.getTenantId(), source.getId())) != null) {
            throw new StateConflictException();
        }
        if (source.getBatchCode() == null || source.getBatchCode().isBlank()
                || !source.getBatchCode().equals(source.getBatchCode().trim())
                || source.getBatchCode().length() > 64) {
            throw new IllegalStateException("source batch code is invalid");
        }
        ArrivalAcceptanceDO successor = new ArrivalAcceptanceDO();
        successor.setTenantId(source.getTenantId());
        successor.setProjectId(source.getProjectId());
        successor.setBatchCode(source.getBatchCode());
        successor.setBatchRootMarker(null);
        successor.setLogisticsNo(source.getLogisticsNo());
        successor.setArrivedAt(source.getArrivedAt());
        successor.setSignerSnapshot(source.getSignerSnapshot());
        successor.setStatus("DRAFT");
        if (project != null && !Objects.equals(source.getProjectId(), project.projectId())) {
            throw new IllegalStateException("current project system qualification is mismatched");
        }
        successor.setProjectVersion(project == null ? source.getProjectVersion() : project.projectVersion());
        successor.setProjectParticipantFactVersion(project == null
                ? source.getProjectParticipantFactVersion() : project.participantFactVersion());
        successor.setProjectScopeVersion(project == null ? source.getProjectScopeVersion() : project.treeVersion());
        successor.setDeliveryScopeVersion(source.getDeliveryScopeVersion());
        successor.setExpectedScopeSnapshot(source.getExpectedScopeSnapshot());
        successor.setScopeWatermark(source.getScopeWatermark());
        successor.setMigrationResolutionStatus("NOT_APPLICABLE");
        successor.setPredecessorAcceptanceId(source.getId());
        successor.setSuccessorReason(reason);
        successor.setVersion(0);
        successor.setCreator(String.valueOf(actorUserId));
        successor.setUpdater(String.valueOf(actorUserId));
        if (acceptanceMapper.insert(successor) != 1 || successor.getId() == null) {
            throw new IllegalStateException("successor insert failed");
        }
        Map<Long, ArrivalLineDO> copiedLines = new LinkedHashMap<>();
        Map<Long, Integer> sourceLineVersions = new LinkedHashMap<>();
        for (ArrivalLineDO sourceLine : sourceLines) {
            ArrivalLineDO copied = new ArrivalLineDO();
            org.springframework.beans.BeanUtils.copyProperties(sourceLine, copied,
                    "id", "arrivalAcceptanceId", "version", "creator", "createTime", "updater", "updateTime");
            copied.setId(null);
            copied.setArrivalAcceptanceId(successor.getId());
            copied.setVersion(0);
            copied.setCreator(String.valueOf(actorUserId));
            copied.setUpdater(String.valueOf(actorUserId));
            if (lineMapper.insert(copied) != 1 || copied.getId() == null) {
                throw new IllegalStateException("successor line copy failed");
            }
            copiedLines.put(sourceLine.getId(), copied);
            sourceLineVersions.put(sourceLine.getId(), sourceLine.getVersion());
        }
        Map<Integer, ArrivalDifferenceDO> copiedDifferences = new LinkedHashMap<>();
        for (ArrivalDifferenceDO sourceDifference : sourceDifferences) {
            ArrivalLineDO copiedLine = copiedLines.get(sourceDifference.getArrivalLineId());
            if (copiedLine == null) throw new IllegalStateException("difference line copy is missing");
            ArrivalDifferenceDO copied = new ArrivalDifferenceDO();
            org.springframework.beans.BeanUtils.copyProperties(sourceDifference, copied,
                    "id", "arrivalAcceptanceId", "arrivalLineId", "projectFactVersion", "factImpactType",
                    "version", "creator", "createTime", "updater", "updateTime");
            copied.setId(null);
            copied.setArrivalAcceptanceId(successor.getId());
            copied.setArrivalLineId(copiedLine.getId());
            copied.setProjectFactVersion(null);
            copied.setFactImpactType(null);
            copied.setVersion(0);
            copied.setCreator(String.valueOf(actorUserId));
            copied.setUpdater(String.valueOf(actorUserId));
            if (differenceMapper.insert(copied) != 1 || copied.getId() == null) {
                throw new IllegalStateException("successor difference copy failed");
            }
            copiedDifferences.put(sourceDifference.getDifferenceNo(), copied);
        }
        return new SuccessorContext(successor, Map.copyOf(copiedLines),
                Map.copyOf(sourceLineVersions), Map.copyOf(copiedDifferences));
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
        lockProject(root, actorUserId, requireManager);
        return lockScopeOwners(root);
    }

    private void lockProject(ArrivalAcceptanceDO root, Long actorUserId, boolean requireManager) {
        ProjectQualificationPort.ProjectQualificationFact project = projectPort.lockAndRevalidate(
                new ProjectQualificationPort.RevalidationCommand(
                root.getTenantId(), root.getProjectId(), requireManager ? actorUserId : null, actorUserId,
                root.getProjectVersion(), root.getProjectParticipantFactVersion(), root.getProjectScopeVersion(),
                requireManager));
        if (project == null || !root.getProjectId().equals(project.projectId())) {
            throw new IllegalStateException("project qualification fact is unavailable or mismatched");
        }
    }

    private FrozenScope lockScopeOwners(ArrivalAcceptanceDO root) {
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
        Set<String> expectedSerials = collectSerialNumbers(expected.deliveryLines());
        Set<String> frozenDeviceSerials = expectation.stream()
                .map(DeviceScopeFactPort.ExpectedDeviceFact::serialNumber)
                .collect(java.util.stream.Collectors.toSet());
        if (!expectedSerials.equals(frozenDeviceSerials)) {
            throw new IllegalStateException("frozen device scope does not match delivery serials");
        }
        DeviceScopeFactPort.DeviceScopeFact devices = expectedSerials.isEmpty()
                ? new DeviceScopeFactPort.DeviceScopeFact(root.getProjectId(), List.of())
                : devicePort.lockAndRevalidate(root.getTenantId(), root.getProjectId(), expectation);
        if (!orderedDevices(devices.devices()).equals(orderedDevices(expected.devices()))) {
            throw new IllegalStateException("device scope changed without version change");
        }
        return new FrozenScope(delivery.lines(), devices.devices());
    }

    private static boolean sameLockedSource(ArrivalAcceptanceDO observed, ArrivalAcceptanceDO locked) {
        return locked != null
                && Objects.equals(observed.getTenantId(), locked.getTenantId())
                && Objects.equals(observed.getId(), locked.getId())
                && Objects.equals(observed.getProjectId(), locked.getProjectId())
                && Objects.equals(observed.getVersion(), locked.getVersion())
                && Objects.equals(observed.getProjectVersion(), locked.getProjectVersion())
                && Objects.equals(observed.getProjectParticipantFactVersion(),
                        locked.getProjectParticipantFactVersion())
                && Objects.equals(observed.getProjectScopeVersion(), locked.getProjectScopeVersion())
                && Objects.equals(observed.getDeliveryScopeVersion(), locked.getDeliveryScopeVersion())
                && Objects.equals(observed.getExpectedScopeSnapshot(), locked.getExpectedScopeSnapshot())
                && Objects.equals(observed.getScopeWatermark(), locked.getScopeWatermark());
    }

    private ArrivalAcceptanceDO lockOwnedDraft(Long tenantId, Long id, Long actor, Integer version) {
        ArrivalAcceptanceDO root = acceptanceMapper.selectForUpdate(new ArrivalRowQuery(tenantId, id));
        if (root == null || !String.valueOf(actor).equals(root.getCreator())) {
            throw ArrivalAcceptanceContractException.notVisible("arrival acceptance draft is not visible or does not exist");
        }
        if (!Objects.equals(root.getVersion(), version)) {
            throw ArrivalAcceptanceContractException.aggregateVersion(root.getVersion(),
                    "arrival acceptance version changed before patch");
        }
        if (!"DRAFT".equals(root.getStatus())) {
            throw ArrivalAcceptanceContractException.stateConflict(root.getVersion(),
                    "arrival acceptance is not an editable draft");
        }
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

    private static void requireExpectedDifference(ArrivalDifferenceDO current,
                                                  Integer expectedRevision, Integer expectedVersion) {
        if (!Objects.equals(current.getRevisionNo(), expectedRevision)) {
            throw new DifferenceVersionConflictException("DIFFERENCE_REVISION_STALE",
                    current.getRevisionNo(), current.getVersion());
        }
        if (!Objects.equals(current.getVersion(), expectedVersion)) {
            throw new DifferenceVersionConflictException("DIFFERENCE_VERSION_STALE",
                    current.getRevisionNo(), current.getVersion());
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

    private static Set<String> collectSerialNumbers(List<DeliveryScopePort.AssignedLine> lines) {
        Set<String> serialNumbers = new java.util.HashSet<>();
        for (DeliveryScopePort.AssignedLine line : lines) {
            for (String serialNumber : line.serialNumbers()) {
                if (!serialNumbers.add(serialNumber)) {
                    throw new IllegalStateException("assigned serial number is duplicated");
                }
            }
        }
        return Set.copyOf(serialNumbers);
    }

    private static String digest(Object command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(command).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static String digestExcludingCorrelation(Object command) {
        Map<String, Object> payload = JsonUtils.parseObject(JsonUtils.toJsonString(command), Map.class);
        if (payload == null || payload.remove("correlationId") == null) {
            throw new IllegalArgumentException("trusted correlation id is missing from command");
        }
        return digest(payload);
    }

    private static PlatformCommandExecutionApi.SuccessFacts successFacts(
            String operation, String correlationId, ArrivalAcceptanceCommands.CommandResult response) {
        return new PlatformCommandExecutionApi.SuccessFacts(operation, "ArrivalAcceptance",
                String.valueOf(response.arrivalAcceptanceId()), correlationId,
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

    private record SuccessorContext(ArrivalAcceptanceDO root,
                                    Map<Long, ArrivalLineDO> linesBySourceId,
                                    Map<Long, Integer> sourceLineVersions,
                                    Map<Integer, ArrivalDifferenceDO> differencesByNumber) {
    }

    public static class VersionConflictException extends RuntimeException {
    }
    public static final class LineVersionConflictException extends VersionConflictException {
    }
    public static final class DifferenceVersionConflictException extends VersionConflictException {
        private final String reasonCode;
        private final Integer currentRevision;
        private final Integer currentVersion;

        public DifferenceVersionConflictException() {
            this("DIFFERENCE_VERSION_STALE", null, null);
        }

        public DifferenceVersionConflictException(String reasonCode, Integer currentRevision,
                                                  Integer currentVersion) {
            this.reasonCode = reasonCode;
            this.currentRevision = currentRevision;
            this.currentVersion = currentVersion;
        }

        public String reasonCode() { return reasonCode; }
        public Integer currentRevision() { return currentRevision; }
        public Integer currentVersion() { return currentVersion; }
    }
    public static final class IdempotencyConflictException extends RuntimeException {
    }
    public static final class IdempotencyInProgressException extends RuntimeException {
    }
    public static final class StateConflictException extends RuntimeException {
    }
}
