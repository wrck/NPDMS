package cn.iocoder.yudao.module.pms.cutover.service.spare;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare.query.SpareApplicationQueries;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.CutoverSpareFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.CutoverSpareFilePort.*;
import cn.iocoder.yudao.module.pms.cutover.service.spare.view.CutoverSpareViews;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** CUT-08备件工作台只读查询；跨模块文件事实仅经端口读取。 */
public final class CutoverSpareQueryService {
    private final CutoverTaskMapper taskMapper;
    private final CutoverSpareApplicationReferenceMapper applicationMapper;
    private final CutoverSpareStatusRevisionMapper statusMapper;
    private final CutoverSpareManualEvidenceMapper evidenceMapper;
    private final CutoverSpareNeedAssembler needAssembler;
    private final CutoverSpareFilePort filePort;

    public CutoverSpareQueryService(CutoverTaskMapper taskMapper,
            CutoverSpareApplicationReferenceMapper applicationMapper,
            CutoverSpareStatusRevisionMapper statusMapper,
            CutoverSpareManualEvidenceMapper evidenceMapper,
            CutoverSpareNeedAssembler needAssembler, CutoverSpareFilePort filePort) {
        this.taskMapper = taskMapper;
        this.applicationMapper = applicationMapper;
        this.statusMapper = statusMapper;
        this.evidenceMapper = evidenceMapper;
        this.needAssembler = needAssembler;
        this.filePort = filePort;
    }

    public CutoverSpareViews.Detail detail(long tenantId, long taskId, long actorId, ActionAccess access) {
        require(tenantId > 0 && taskId > 0 && actorId > 0 && access != null && access.queryAllowed(), "request");
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && Long.valueOf(tenantId).equals(task.getTenantId()), "task");
        var need = needAssembler.assemble(tenantId, task);
        List<CutoverSpareApplicationReferenceDO> roots = applicationMapper
                .selectByTask(new SpareApplicationQueries.ByTask(tenantId, taskId));
        List<CutoverSpareViews.Application> applications = roots.stream()
                .sorted(Comparator.comparing(CutoverSpareApplicationReferenceDO::getId))
                .map(root -> application(tenantId, taskId, root)).toList();
        List<CutoverSpareViews.ManualEvidence> evidence = evidenceMapper
                .selectByTask(new SpareApplicationQueries.EvidenceByTask(tenantId, taskId)).stream()
                .sorted(Comparator.comparing(CutoverSpareManualEvidenceDO::getCreatedAt)
                        .thenComparing(CutoverSpareManualEvidenceDO::getId))
                .map(row -> evidence(tenantId, actorId, task, row)).toList();
        List<String> actions = new ArrayList<>();
        if (access.manageAllowed() && access.owner() && access.projectEditable()) {
            if (need.required()) actions.add("INITIATE");
            if (!applications.isEmpty()) actions.add("REFRESH");
            actions.add("ADD_EVIDENCE");
        }
        return new CutoverSpareViews.Detail(taskId, task.getVersion(), need, applications, evidence, List.copyOf(actions));
    }

    public CutoverSpareViews.ApprovalSummary approvalSummary(long tenantId, long taskId, long actorId) {
        CutoverSpareViews.Detail detail = detail(tenantId, taskId, actorId,
                new ActionAccess(true, false, false, false));
        return new CutoverSpareViews.ApprovalSummary(detail.need().required(),
                detail.applications().stream().map(value -> new CutoverSpareViews.ApplicationApprovalSummary(
                        value.integrationStatus(), value.externalSystemCode(), value.externalApplicationNo(),
                        value.currentStatus() == null ? null : value.currentStatus().externalStatusRaw(),
                        value.currentStatus() == null ? null : value.currentStatus().observedAt(),
                        value.lastFailureCode())).toList(),
                detail.manualEvidence().stream().map(value -> new CutoverSpareViews.EvidenceApprovalSummary(
                        value.fileFact().displayName(), value.description(), value.createdAt())).toList());
    }

    private CutoverSpareViews.Application application(long tenantId, long taskId,
            CutoverSpareApplicationReferenceDO root) {
        require(root != null && Long.valueOf(tenantId).equals(root.getTenantId())
                && Long.valueOf(taskId).equals(root.getCutoverTaskId()), "application identity");
        CutoverSpareStatusRevisionDO status = null;
        if (root.getCurrentStatusRevisionId() != null) {
            status = statusMapper.selectByApplication(new SpareApplicationQueries.StatusByApplication(tenantId, root.getId()))
                    .stream().filter(row -> root.getCurrentStatusRevisionId().equals(row.getId())).findFirst()
                    .orElseThrow(() -> corrupted("current status"));
            require(Integer.valueOf(1).equals(status.getCurrentMarker()), "current status marker");
        }
        return new CutoverSpareViews.Application(root.getId(), root.getPlatformRequestId(), root.getIntegrationStatus(),
                root.getExternalSystemCode(), root.getExternalRequestId(), root.getExternalApplicationNo(),
                root.getLaunchUrl(), status == null ? null : status(status), root.getLastFailureCode(),
                root.getRetryCount(), root.getUpdateTime());
    }

    private CutoverSpareViews.Status status(CutoverSpareStatusRevisionDO row) {
        return new CutoverSpareViews.Status(row.getStatusVersion(), row.getExternalStatusRaw(), row.getSourceType(),
                row.getExternalOccurredAt(), row.getObservedAt());
    }

    private CutoverSpareViews.ManualEvidence evidence(long tenantId, long actorId, CutoverTaskDO task,
            CutoverSpareManualEvidenceDO row) {
        require(row != null && Long.valueOf(tenantId).equals(row.getTenantId())
                && task.getId().equals(row.getCutoverTaskId()), "evidence identity");
        FileFactVersion version;
        try {
            version = new FileFactVersion(Math.toIntExact(row.getFileArtifactFactVersion()),
                    Math.toIntExact(row.getFileReferenceFactVersion()), Math.toIntExact(row.getFileAvailabilityFactVersion()));
        } catch (RuntimeException exception) {
            throw corrupted("file fact version");
        }
        FileExpectation expectation = new FileExpectation(tenantId, actorId, task.getProjectId(), task.getId(),
                row.getFileArtifactId(), row.getFileReferenceKey(), row.getFileVersionNo(), version,
                row.getFileScopeVersion());
        FileFact fact = filePort.inspect(expectation);
        require(fact.artifactId().equals(expectation.artifactId())
                && fact.referenceKey().equals(expectation.referenceKey())
                && fact.versionNo().equals(expectation.versionNo())
                && fact.fileFactVersion().equals(expectation.fileFactVersion())
                && fact.scopeVersion().equals(expectation.scopeVersion()), "file fact mismatch");
        return new CutoverSpareViews.ManualEvidence(row.getId(), row.getApplicationReferenceId(), fact,
                row.getDescription(), row.getUploadedBy(), row.getCreatedAt());
    }

    public record ActionAccess(boolean queryAllowed, boolean manageAllowed, boolean owner, boolean projectEditable) { }

    private static void require(boolean condition, String field) {
        if (!condition) throw corrupted(field);
    }

    private static IllegalStateException corrupted(String field) {
        return new IllegalStateException("spare query failed closed: " + field);
    }
}
