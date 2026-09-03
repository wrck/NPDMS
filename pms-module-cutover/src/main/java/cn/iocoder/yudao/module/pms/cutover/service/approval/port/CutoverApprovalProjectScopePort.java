package cn.iocoder.yudao.module.pms.cutover.service.approval.port;

import java.util.List;

import static cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules.require;

public interface CutoverApprovalProjectScopePort {

    ProjectScopeFact inspect(long tenantId, long projectId, long subjectUserId, String requiredAction);

    ProjectScopeRevalidation lockAndRevalidate(ProjectScopeFact expected);

    record ProjectScopeFact(long tenantId, long projectId, long subjectUserId,
                            String requiredAction, boolean allowed, long treeVersion) {
        public ProjectScopeFact {
            require(tenantId > 0 && projectId > 0 && subjectUserId > 0
                    && List.of("ACTION_VIEW", "ACTION_EDIT").contains(requiredAction)
                    && treeVersion >= 0, "projectScopeFact");
        }
    }

    record ProjectScopeRevalidation(Revalidation outcome, ProjectScopeFact current) {
        public ProjectScopeRevalidation {
            require(outcome != null && current != null, "projectScopeRevalidation");
        }
    }

    enum Revalidation { VALID, STALE }
}
