package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalScopeWatermark;
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
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildrenBatchQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRevisionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceSourceQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** COM/AST生产依赖接通前仅显式组装，不注册Spring Bean。 */
public class ArrivalAcceptanceQueryService {

    private final ArrivalAcceptanceMapper acceptanceMapper;
    private final ArrivalLineMapper lineMapper;
    private final ArrivalDifferenceMapper differenceMapper;
    private final DeliveryEvidenceMapper evidenceMapper;
    private final DeliveryEvidenceRevisionMapper revisionMapper;

    public ArrivalAcceptanceQueryService(ArrivalAcceptanceMapper acceptanceMapper,
                                         ArrivalLineMapper lineMapper,
                                         ArrivalDifferenceMapper differenceMapper,
                                         DeliveryEvidenceMapper evidenceMapper,
                                         DeliveryEvidenceRevisionMapper revisionMapper) {
        this.acceptanceMapper = Objects.requireNonNull(acceptanceMapper);
        this.lineMapper = Objects.requireNonNull(lineMapper);
        this.differenceMapper = Objects.requireNonNull(differenceMapper);
        this.evidenceMapper = Objects.requireNonNull(evidenceMapper);
        this.revisionMapper = Objects.requireNonNull(revisionMapper);
    }

    @Transactional(readOnly = true)
    public PageResult<ArrivalAcceptanceViews.ArrivalListItem> page(
            ArrivalAcceptanceViews.PageRequest request) {
        requirePageRequest(request);
        if (request.access().visibleProjectIds().isEmpty()) {
            return PageResult.empty();
        }
        ArrivalPageQuery query = new ArrivalPageQuery(request.tenantId(),
                request.access().visibleProjectIds(), request.projectId(),
                normalizeOptional(request.batchCode()), request.status(),
                Math.multiplyExact(request.pageNo() - 1, request.pageSize()), request.pageSize());
        List<ArrivalAcceptanceDO> rows = acceptanceMapper.selectPageRows(query);
        ArrivalChildrenBatchQuery childrenQuery = new ArrivalChildrenBatchQuery(request.tenantId(),
                rows.stream().map(ArrivalAcceptanceDO::getId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, List<ArrivalLineDO>> linesByAcceptance = lineMapper
                .selectCurrentListByAcceptanceIds(childrenQuery).stream()
                .collect(Collectors.groupingBy(ArrivalLineDO::getArrivalAcceptanceId));
        Map<Long, List<ArrivalDifferenceDO>> differencesByAcceptance = differenceMapper
                .selectCurrentListByAcceptanceIds(childrenQuery).stream()
                .collect(Collectors.groupingBy(ArrivalDifferenceDO::getArrivalAcceptanceId));
        Map<Long, DeliveryEvidenceDO> evidenceByAcceptance = uniqueEvidenceByAcceptance(
                evidenceMapper.selectByArrivalAcceptanceIds(childrenQuery));
        List<ArrivalAcceptanceViews.ArrivalListItem> items = rows.stream()
                .map(row -> toListItem(row,
                        linesByAcceptance.getOrDefault(row.getId(), List.of()),
                        differencesByAcceptance.getOrDefault(row.getId(), List.of()),
                        evidenceByAcceptance.get(row.getId()), request.access()))
                .toList();
        return new PageResult<>(items, acceptanceMapper.selectPageCount(query));
    }

    @Transactional(readOnly = true)
    public ArrivalAcceptanceViews.ArrivalDetail detail(ArrivalAcceptanceViews.DetailRequest request) {
        requireDetailRequest(request);
        ArrivalAcceptanceDO root = acceptanceMapper.selectRow(
                new ArrivalRowQuery(request.tenantId(), request.arrivalAcceptanceId()));
        if (root == null || !request.access().visibleProjectIds().contains(root.getProjectId())) {
            throw new NotVisibleException();
        }
        ArrivalChildrenQuery children = new ArrivalChildrenQuery(request.tenantId(), root.getId());
        List<ArrivalLineDO> lines = lineMapper.selectCurrentList(children);
        List<ArrivalDifferenceDO> differences = differenceMapper.selectCurrentList(children);
        DeliveryEvidenceDO evidence = evidence(root);
        return new ArrivalAcceptanceViews.ArrivalDetail(
                root.getId(), root.getProjectId(), root.getBatchCode(), root.getLogisticsNo(),
                root.getArrivedAt(), signerName(root), root.getStatus(), root.getDeliveryScopeVersion(),
                watermark(root), root.getEvidenceId(), root.getEvidenceRevision(), root.getProjectFactVersion(),
                root.getPredecessorAcceptanceId(), root.getSuccessorReason(), root.getSubmittedBy(),
                root.getSubmittedAt(), root.getConfirmedBy(), root.getConfirmedAt(), root.getVersion(),
                allowedActions(root, lines, differences, evidence, request.access()),
                lines.stream().sorted(Comparator.comparing(ArrivalLineDO::getLineNo)
                                .thenComparing(ArrivalLineDO::getId)).map(ArrivalAcceptanceQueryService::line).toList(),
                differences.stream().sorted(Comparator.comparing(ArrivalDifferenceDO::getDifferenceNo)
                                .thenComparing(ArrivalDifferenceDO::getRevisionNo)
                                .thenComparing(ArrivalDifferenceDO::getId))
                        .map(ArrivalAcceptanceQueryService::difference).toList(),
                evidence == null ? null : evidence(evidence, request.tenantId()));
    }

    private ArrivalAcceptanceViews.ArrivalListItem toListItem(
            ArrivalAcceptanceDO row, List<ArrivalLineDO> lines, List<ArrivalDifferenceDO> differences,
            DeliveryEvidenceDO evidence, ArrivalAcceptanceViews.AccessContext access) {
        return new ArrivalAcceptanceViews.ArrivalListItem(
                row.getId(), row.getProjectId(), row.getBatchCode(), row.getLogisticsNo(), row.getArrivedAt(),
                signerName(row), row.getStatus(), evidence == null ? null : evidence.getAccSyncStatus(),
                row.getVersion(), allowedActions(row, lines, differences, evidence, access), row.getCreateTime());
    }

    private static Map<Long, DeliveryEvidenceDO> uniqueEvidenceByAcceptance(List<DeliveryEvidenceDO> evidence) {
        Map<Long, DeliveryEvidenceDO> result = new LinkedHashMap<>();
        for (DeliveryEvidenceDO row : evidence) {
            DeliveryEvidenceDO previous = result.putIfAbsent(row.getSourceObjectId(), row);
            if (previous != null) {
                throw new IllegalStateException("multiple delivery evidence roots for arrival acceptance");
            }
        }
        return result;
    }

    private DeliveryEvidenceDO evidence(ArrivalAcceptanceDO root) {
        return evidenceMapper.selectBySource(new DeliveryEvidenceSourceQuery(
                root.getTenantId(), "EXE-01", "ARRIVAL_ACCEPTANCE", root.getId()));
    }

    private ArrivalAcceptanceViews.DeliveryEvidenceData evidence(DeliveryEvidenceDO root, Long tenantId) {
        DeliveryEvidenceRevisionDO revision = root.getCurrentRevisionNo() == null ? null
                : revisionMapper.selectRevision(new DeliveryEvidenceRevisionQuery(
                        tenantId, root.getId(), root.getCurrentRevisionNo()));
        FileFactVersion factVersion = revision == null || revision.getFileFactVersion() == null ? null
                : JsonUtils.parseObject(revision.getFileFactVersion(), FileFactVersion.class);
        return new ArrivalAcceptanceViews.DeliveryEvidenceData(
                root.getId(), root.getCurrentRevisionNo(), revision == null ? null : revision.getFileArtifactId(),
                revision == null ? null : revision.getFileReferenceId(),
                revision == null ? null : revision.getFileVersionNo(), factVersion,
                revision == null ? null : revision.getFileScopeVersion(),
                revision == null ? null : revision.getFileHash(), root.getAccSyncStatus(),
                root.getAccNextRetryAt(), root.getAccRetryCount());
    }

    static List<String> allowedActions(ArrivalAcceptanceDO root, List<ArrivalLineDO> lines,
                                       List<ArrivalDifferenceDO> differences, DeliveryEvidenceDO evidence,
                                       ArrivalAcceptanceViews.AccessContext access) {
        boolean visible = access.visibleProjectIds().contains(root.getProjectId());
        boolean editable = access.editableProjectIds().contains(root.getProjectId());
        boolean manager = access.currentManagerProjectIds().contains(root.getProjectId());
        boolean team = manager || access.authorizedTeamProjectIds().contains(root.getProjectId());
        boolean creator = Objects.equals(String.valueOf(access.actorUserId()), root.getCreator());
        List<String> actions = new ArrayList<>();
        if (visible && editable && team && creator && "DRAFT".equals(root.getStatus())
                && access.functionPermissions().contains(ArrivalAcceptanceViews.PERMISSION_EDIT_OWN_DRAFT)) {
            actions.add("EDIT_DRAFT");
            if (!lines.isEmpty() && evidence != null && evidence.getCurrentRevisionNo() != null) {
                actions.add("SUBMIT");
            }
        }
        if (visible && editable && manager
                && ("PARTIALLY_ACCEPTED".equals(root.getStatus()) || "ACCEPTED".equals(root.getStatus()))
                && access.functionPermissions().contains(ArrivalAcceptanceViews.PERMISSION_CONFIRM)) {
            actions.add("CONFIRM");
        }
        if (visible && editable && team && creator && "DRAFT".equals(root.getStatus()) && !lines.isEmpty()
                && access.functionPermissions().contains(ArrivalAcceptanceViews.PERMISSION_RESOLVE_DIFFERENCE)) {
            actions.add("RAISE_DIFFERENCE");
        }
        boolean hasOpen = differences.stream().anyMatch(difference ->
                Integer.valueOf(1).equals(difference.getCurrentMarker())
                        && "OPEN".equals(difference.getResolutionStatus()));
        if (visible && editable && manager
                && ("DIFFERENCE_PENDING".equals(root.getStatus()) || "CONFIRMED".equals(root.getStatus()))
                && (hasOpen || "CONFIRMED".equals(root.getStatus()))
                && access.functionPermissions().contains(ArrivalAcceptanceViews.PERMISSION_RESOLVE_DIFFERENCE)) {
            actions.add("RESOLVE_DIFFERENCE");
        }
        return List.copyOf(actions);
    }

    private static ArrivalAcceptanceViews.ArrivalLineData line(ArrivalLineDO line) {
        return new ArrivalAcceptanceViews.ArrivalLineData(line.getId(), line.getLineNo(), line.getLineRevision(),
                line.getScopeType(), line.getDeviceId(), line.getDeviceAssignmentVersion(), line.getOrderLineId(),
                line.getProductCode(), line.getModelCode(), line.getExpectedQuantity(), line.getAcceptedQuantity(),
                line.getUnit(), line.getStatus(), line.getVersion());
    }

    private static ArrivalAcceptanceViews.ArrivalDifferenceData difference(ArrivalDifferenceDO difference) {
        return new ArrivalAcceptanceViews.ArrivalDifferenceData(
                difference.getId(), difference.getArrivalLineId(), difference.getDifferenceNo(),
                difference.getRevisionNo(), difference.getDifferenceType(), difference.getResolutionStatus(),
                difference.getReason(), difference.getRiskDescription(), difference.getScopeSnapshot(),
                difference.getApprovedBy(), difference.getApprovedAt(), difference.getExemptionExpiresAt(),
                difference.getEvidenceId(), difference.getEvidenceRevision(),
                Integer.valueOf(1).equals(difference.getCurrentMarker()), difference.getProjectFactVersion(),
                difference.getFactImpactType(), difference.getVersion());
    }

    private static ArrivalAcceptanceViews.ScopeWatermarkData watermark(ArrivalAcceptanceDO root) {
        ArrivalScopeWatermark value = JsonUtils.parseObject(root.getScopeWatermark(), ArrivalScopeWatermark.class);
        return new ArrivalAcceptanceViews.ScopeWatermarkData(value.deliveryScopeVersion(),
                value.deviceAssignmentVersions().entrySet().stream()
                        .map(entry -> new ArrivalAcceptanceViews.DeviceAssignmentVersionData(
                                entry.getKey(), entry.getValue())).toList());
    }

    private static String signerName(ArrivalAcceptanceDO root) {
        ArrivalAcceptanceViews.SignerSnapshot signer = JsonUtils.parseObject(
                root.getSignerSnapshot(), ArrivalAcceptanceViews.SignerSnapshot.class);
        if (signer == null || signer.signerName() == null || signer.signerName().isBlank()) {
            throw new IllegalStateException("arrival signer snapshot is invalid");
        }
        return signer.signerName();
    }

    private static void requirePageRequest(ArrivalAcceptanceViews.PageRequest request) {
        if (request == null || request.tenantId() == null || request.tenantId() < 0 || request.access() == null
                || request.projectId() != null && request.projectId() <= 0
                || request.pageNo() <= 0 || request.pageSize() <= 0 || request.pageSize() > 100
                || request.status() != null && !List.of("DRAFT", "PARTIALLY_ACCEPTED", "DIFFERENCE_PENDING",
                        "ACCEPTED", "CONFIRMED").contains(request.status())) {
            throw new IllegalArgumentException("invalid arrival page request");
        }
    }

    private static void requireDetailRequest(ArrivalAcceptanceViews.DetailRequest request) {
        if (request == null || request.tenantId() == null || request.tenantId() < 0
                || request.arrivalAcceptanceId() == null || request.arrivalAcceptanceId() <= 0
                || request.access() == null) {
            throw new IllegalArgumentException("invalid arrival detail request");
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("blank optional filter");
        }
        return normalized;
    }

    public static final class NotVisibleException extends RuntimeException {
    }
}
