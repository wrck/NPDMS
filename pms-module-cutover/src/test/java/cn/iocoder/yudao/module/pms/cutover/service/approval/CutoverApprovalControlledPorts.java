package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.ProjectCutoverServiceManagerPort;

import java.time.LocalDateTime;
import java.util.List;

public final class CutoverApprovalControlledPorts {

    private CutoverApprovalControlledPorts() {
    }

    public static String approvalSource(String grade) {
        var sections = List.of(new cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort.TemplateSectionSnapshot(
                "OPERATION", "Operation", 1, List.of("VERSION_UPGRADE"), List.of(grade), true),
                new cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort.TemplateSectionSnapshot(
                "ROLLBACK", "Rollback", 2, List.of("VERSION_UPGRADE"), List.of(grade), true));
        var source = new cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort.SourceSnapshot(
                1, 100L, 5L, 600L, 2L, grade, "D".equals(grade) ? null : 700L, "D".equals(grade) ? null : 3L,
                10L, 4, 12L, List.of(new cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort.DeviceSnapshot(
                3101L, "SN-001", 2L, "ROUTER", "TYPE-V1")), 3201L, "CUT-CONF", 1, sections, List.of());
        var sourceNode = (tools.jackson.databind.node.ObjectNode) cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(source));
        if (source.checklistId() == null) sourceNode.putNull("checklistId");
        if (source.checklistVersion() == null) sourceNode.putNull("checklistVersion");
        var codec = new cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec();
        var snapshot = new cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec.ApprovalSourceSnapshot(
                1, 100L, 5L, source.checklistId(), source.checklistVersion(),
                new cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec.ProjectApprovalSnapshot(
                10L, 4, "P-001", "Project One", 4001L, "C-001", "Customer One", 5001L, "OFF-01", "Office One", 12L),
                new cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec.CollectionAnalysisSnapshot(
                "VERSION_UPGRADE", null, 1788585600000L), List.of(), List.of(),
                new cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec.AssessmentApprovalSnapshot(
                600L, 2L, 7001L, "HIGH", "MEDIUM", "LOW", false, "GOLD", grade, 202L, 1788458400000L),
                new cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec.PlanApprovalSnapshot(
                900L, 1, 2L, sourceNode,
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree("{\"editMode\":\"FULL_FILE_UPLOAD\",\"ownershipConfirmed\":true,\"fileArtifactFact\":{\"artifactId\":3301,\"versionNo\":1,\"referenceKey\":\"cut-plan-file\",\"fileFactVersion\":{\"artifactVersion\":1,\"referenceVersion\":1,\"availabilityVersion\":1},\"scopeVersion\":2,\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}}")));
        return codec.encode(snapshot);
    }

    public static ProjectCutoverServiceManagerPort serviceManager(long managerUserId) {
        return new ProjectCutoverServiceManagerPort() {
            @Override
            public ServiceManagerFact inspectCurrent(long tenantId, long projectId, LocalDateTime checkedAt) {
                return new ServiceManagerFact(Outcome.FOUND, tenantId, projectId, managerUserId,
                        "SERVICE_MANAGER_L1", 3, 7L, checkedAt);
            }

            @Override
            public ServiceManagerRevalidation lockAndRevalidate(ServiceManagerFact expected) {
                return new ServiceManagerRevalidation(Revalidation.VALID, expected);
            }
        };
    }

    public static CutoverApprovalRoleCandidatePort roleCandidates() {
        return new CutoverApprovalRoleCandidatePort() {
            @Override
            public CandidateSet inspectCandidates(RoleQuery query) {
                long tenantId = query.tenantId(); String roleGroupCode = query.roleGroupCode();
                return new CandidateSet(tenantId, roleGroupCode, List.of(
                        new Candidate(201L, 31L, 4L, 8L),
                        new Candidate(202L, 31L, 5L, 9L)));
            }

            @Override
            public CandidateRevalidation lockAndRevalidate(CandidateSet expected) {
                return new CandidateRevalidation(Revalidation.VALID, expected);
            }

            @Override public RoleReference inspectRole(RoleQuery query) {
                return new RoleReference(query.tenantId(), 31L, query.roleGroupCode());
            }
            @Override public List<ActorMembership> inspectActor(ActorQuery query) {
                if (query.actorId() != 201L && query.actorId() != 202L) return List.of();
                return java.util.stream.Stream.of("CUT_SECOND_LINE_APPROVER", "CUT_RND_APPROVER", "probSupporter", "probRD")
                        .map(code -> new ActorMembership(new RoleReference(query.tenantId(), 31L, code),
                                new Candidate(query.actorId(), 31L, query.actorId() == 201L ? 4L : 5L, query.actorId() == 201L ? 8L : 9L))).toList();
            }
            @Override public List<ActorMembership> lockAndInspectActor(ActorQuery query) { return inspectActor(query); }
        };
    }

    public static CutoverApprovalProjectScopePort projectScope(long uniquelyVisibleUserId) {
        return new CutoverApprovalProjectScopePort() {
            @Override
            public ProjectScopeFact inspect(CutoverApprovalProjectScopePort.ScopeQuery query) {
        long tenantId = query.tenantId();
        long projectId = query.projectId();
        long subjectUserId = query.subjectUserId();
        String requiredAction = query.requiredAction();

                return new ProjectScopeFact(tenantId, projectId, subjectUserId, requiredAction,
                        subjectUserId == uniquelyVisibleUserId, 11L);
            }

            @Override
            public ProjectScopeRevalidation lockAndRevalidate(ProjectScopeFact expected) {
                return new ProjectScopeRevalidation(Revalidation.VALID, expected);
            }
        };
    }
}
