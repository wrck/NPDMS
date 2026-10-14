package cn.iocoder.yudao.module.pms.cutover.service.approval.domain;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import lombok.Builder;

/** 将资格来源落到明确列；不冻结角色的整组成员。 */
public final class CutoverApprovalNodeFacts {
    private CutoverApprovalNodeFacts() { }

    public static CutoverApprovalNodeDO projectScope(CutoverApprovalProjectScopePort.ProjectScopeFact fact) {
        var row = new CutoverApprovalNodeDO();
        row.setApprovalModeCode("PERSONAL"); row.setCurrentApproverUserId(fact.subjectUserId());
        row.setProjectScopeVersion(fact.treeVersion()); row.setQualificationKindCode("PROJECT_SCOPE");
        row.setQualificationRequiredActionCode(fact.requiredAction()); row.setQualificationAllowedFlag(fact.allowed());
        return row;
    }

    public static CutoverApprovalNodeDO manager(ProjectCutoverRuntimeServiceManagerPort.ServiceManagerFact fact) {
        var row = new CutoverApprovalNodeDO();
        row.setApprovalModeCode("PERSONAL"); row.setCurrentApproverUserId(fact.userId());
        row.setProjectScopeVersion(0L); row.setQualificationKindCode("SERVICE_MANAGER");
        row.setQualificationOutcomeCode(fact.outcome().name()); row.setQualificationRoleCode(fact.roleCode());
        row.setQualificationProjectVersion(fact.projectVersion() == null ? null : fact.projectVersion().longValue());
        row.setQualificationParticipantFactVersion(fact.participantFactVersion()); row.setQualificationCheckTime(fact.checkedAt());
        return row;
    }

    public static CutoverApprovalNodeDO manager(ProjectCutoverServiceManagerPort.ServiceManagerFact fact) {
        var row = new CutoverApprovalNodeDO();
        row.setApprovalModeCode("PERSONAL"); row.setCurrentApproverUserId(fact.userId());
        row.setProjectScopeVersion(0L); row.setQualificationKindCode("SERVICE_MANAGER");
        row.setQualificationOutcomeCode(fact.outcome().name()); row.setQualificationRoleCode(fact.roleCode());
        row.setQualificationProjectVersion(fact.projectVersion() == null ? null : fact.projectVersion().longValue());
        row.setQualificationParticipantFactVersion(fact.participantFactVersion()); row.setQualificationCheckTime(fact.checkedAt());
        return row;
    }

    public static CutoverApprovalNodeDO role(CutoverApprovalRoleCandidatePort.RoleReference reference) {
        var row = new CutoverApprovalNodeDO();
        row.setApprovalModeCode("ROLE"); row.setProjectScopeVersion(0L);
        row.setCandidateRoleId(reference.roleId()); row.setCandidateRoleCode(reference.roleGroupCode());
        row.setCandidateGroupKey(reference.candidateGroupKey());
        return row;
    }

    @Builder
    public record PersonalRole(CutoverApprovalRoleCandidatePort.RoleReference role,
                               CutoverApprovalProjectScopePort.ProjectScopeFact scope) { }
    public static CutoverApprovalNodeDO personalRole(PersonalRole fact) {
        var row = role(fact.role()); row.setApprovalModeCode("PERSONAL");
        row.setQualificationKindCode("ROLE_PROJECT_SCOPE"); row.setQualificationRoleCode(fact.role().roleGroupCode());
        if (fact.scope() != null) {
            row.setCurrentApproverUserId(fact.scope().subjectUserId()); row.setProjectScopeVersion(fact.scope().treeVersion());
            row.setQualificationRequiredActionCode(fact.scope().requiredAction());
            row.setQualificationAllowedFlag(fact.scope().allowed());
        }
        return row;
    }

    public record Source(CutoverApprovalInstanceDO instance, CutoverApprovalNodeDO node) { }
    public static ProjectCutoverRuntimeServiceManagerPort.ServiceManagerFact manager(Source source) {
        var row = source.node(); var root = source.instance();
        return new ProjectCutoverRuntimeServiceManagerPort.ServiceManagerFact(
                ProjectCutoverRuntimeServiceManagerPort.Outcome.valueOf(row.getQualificationOutcomeCode()),
                root.getTenantId(), root.getProjectId(), row.getCurrentApproverUserId(), row.getQualificationRoleCode(),
                row.getQualificationProjectVersion() == null ? null : Math.toIntExact(row.getQualificationProjectVersion()),
                row.getQualificationParticipantFactVersion(), row.getQualificationCheckTime());
    }
    public static ProjectCutoverServiceManagerPort.ServiceManagerFact legacyManager(Source source) {
        var row = source.node(); var root = source.instance();
        return new ProjectCutoverServiceManagerPort.ServiceManagerFact(
                ProjectCutoverServiceManagerPort.Outcome.valueOf(row.getQualificationOutcomeCode()),
                root.getTenantId(), root.getProjectId(), row.getCurrentApproverUserId(), row.getQualificationRoleCode(),
                row.getQualificationProjectVersion() == null ? null : Math.toIntExact(row.getQualificationProjectVersion()),
                row.getQualificationParticipantFactVersion(), row.getQualificationCheckTime());
    }

    public static void apply(CutoverApprovalNodeDO row, CutoverApprovalNodeDO fact) {
        row.setCurrentApproverUserId(fact.getCurrentApproverUserId()); row.setProjectScopeVersion(fact.getProjectScopeVersion());
        row.setApprovalModeCode(fact.getApprovalModeCode()); row.setCandidateRoleId(fact.getCandidateRoleId());
        row.setCandidateRoleCode(fact.getCandidateRoleCode()); row.setCandidateGroupKey(fact.getCandidateGroupKey());
        row.setQualificationKindCode(fact.getQualificationKindCode()); row.setQualificationOutcomeCode(fact.getQualificationOutcomeCode());
        row.setQualificationRoleCode(fact.getQualificationRoleCode()); row.setQualificationProjectVersion(fact.getQualificationProjectVersion());
        row.setQualificationParticipantFactVersion(fact.getQualificationParticipantFactVersion());
        row.setQualificationRequiredActionCode(fact.getQualificationRequiredActionCode());
        row.setQualificationAllowedFlag(fact.getQualificationAllowedFlag()); row.setQualificationCheckTime(fact.getQualificationCheckTime());
    }

    public static void apply(CutoverApprovalReassignmentDO row, CutoverApprovalNodeDO fact) {
        row.setProjectScopeVersion(fact.getProjectScopeVersion()); row.setQualificationKindCode(fact.getQualificationKindCode());
        row.setQualificationOutcomeCode(fact.getQualificationOutcomeCode()); row.setQualificationRoleCode(fact.getQualificationRoleCode());
        row.setQualificationProjectVersion(fact.getQualificationProjectVersion());
        row.setQualificationParticipantFactVersion(fact.getQualificationParticipantFactVersion());
        row.setQualificationRequiredActionCode(fact.getQualificationRequiredActionCode());
        row.setQualificationAllowedFlag(fact.getQualificationAllowedFlag()); row.setQualificationCheckTime(fact.getQualificationCheckTime());
    }
}
