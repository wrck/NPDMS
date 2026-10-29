package cn.iocoder.yudao.module.pms.project.api.workbinding;

import java.util.List;

/** Current-plan prerequisites only. Owners retain their permission, scope and lifecycle checks. */
public interface ProjectTaskPrerequisiteApi {
    record Query(Long projectId, String ownerContext, String objectType) { }
    record Readiness(boolean allowed, List<String> blockers) {
        public Readiness { blockers = List.copyOf(blockers); }
    }

    Readiness inspect(Query query);

    /** Rechecks under the project lock in the Owner's write transaction; never starts/reopens a task. */
    void requireSatisfied(Query query);
}
