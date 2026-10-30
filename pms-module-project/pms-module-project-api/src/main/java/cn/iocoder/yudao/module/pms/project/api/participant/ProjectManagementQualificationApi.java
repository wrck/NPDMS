package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;

/**
 * PROJ owns current project management qualification: confirmed management member or regional service manager.
 * This does not grant functional permissions, project action scope, assignee rights or approval responsibility.
 */
public interface ProjectManagementQualificationApi {
    /** Returns null only when neither independently verified qualification exists. */
    Fact inspect(Query query);

    default boolean permits(Query query) { return inspect(query) != null; }

    /** Joins the caller transaction; revalidates exactly the inspected source and never selects a replacement. */
    Fact lockAndRevalidate(Revalidation query);

    /** For callers already holding the project lock: select once, then lock that same source with the caller version. */
    Fact lockCurrent(CurrentRevalidation query);

    record Query(Long tenantId, Long projectId, Long userId) { }

    enum Source { PROJECT_MEMBER, REGIONAL_MANAGER }

    /** Underlying facts retain their actual identity; a regional manager is never represented as a PM member. */
    record Fact(Query subject, Source source, ProjectParticipantFact member, ProjectRegionalManagerFactApi.Fact regional) {
        public Fact {
            if (subject == null || subject.tenantId() == null || subject.projectId() == null || subject.userId() == null
                    || source == null || (source == Source.PROJECT_MEMBER ? member == null || regional != null : regional == null || member != null))
                throw new IllegalArgumentException("PROJECT_MANAGEMENT_QUALIFICATION_SOURCE_INVALID");
            var projectId = source == Source.PROJECT_MEMBER ? member.projectId() : regional.projectId();
            var userId = source == Source.PROJECT_MEMBER ? member.userId() : regional.userId();
            var version = source == Source.PROJECT_MEMBER ? member.projectVersion() : regional.projectVersion();
            if (!subject.projectId().equals(projectId) || !subject.userId().equals(userId) || version == null || version < 0)
                throw new IllegalArgumentException("PROJECT_MANAGEMENT_QUALIFICATION_IDENTITY_INVALID");
        }

        public Long projectVersion() {
            return source == Source.PROJECT_MEMBER ? member.projectVersion() : regional.projectVersion();
        }
    }

    record Revalidation(Fact expected, Long expectedProjectVersion, String requiredLifecycleStatus, String requiredCurrentStage) { }

    record CurrentRevalidation(Query subject, Long expectedProjectVersion, String requiredLifecycleStatus, String requiredCurrentStage) { }
}
