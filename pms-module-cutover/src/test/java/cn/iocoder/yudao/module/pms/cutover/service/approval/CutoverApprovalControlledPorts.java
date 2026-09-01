package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.ProjectCutoverServiceManagerPort;

import java.time.LocalDateTime;
import java.util.List;

public final class CutoverApprovalControlledPorts {

    private CutoverApprovalControlledPorts() {
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
            public CandidateSet inspectCandidates(long tenantId, String roleGroupCode) {
                return new CandidateSet(tenantId, roleGroupCode, List.of(
                        new Candidate(201L, 31L, 4L, 8L),
                        new Candidate(202L, 31L, 5L, 9L)));
            }

            @Override
            public CandidateRevalidation lockAndRevalidate(CandidateSet expected) {
                return new CandidateRevalidation(Revalidation.VALID, expected);
            }

            @Override
            public ExplicitCandidate lockExplicitCandidate(long tenantId, String roleGroupCode,
                                                            long subjectUserId) {
                Candidate candidate = inspectCandidates(tenantId, roleGroupCode).candidates().stream()
                        .filter(value -> value.adminUserId() == subjectUserId).findFirst().orElse(null);
                return candidate == null
                        ? new ExplicitCandidate(Eligibility.INELIGIBLE, null)
                        : new ExplicitCandidate(Eligibility.ELIGIBLE, candidate);
            }
        };
    }

    public static CutoverApprovalProjectScopePort projectScope(long uniquelyVisibleUserId) {
        return new CutoverApprovalProjectScopePort() {
            @Override
            public ProjectScopeFact inspect(long tenantId, long projectId, long subjectUserId,
                                            String requiredAction) {
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
