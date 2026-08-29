package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port;

import java.util.Set;

/** 到货签收使用的项目资格、阶段、角色和编辑范围端口。 */
public interface ProjectQualificationPort {

    ProjectQualificationFact inspect(Long tenantId, Long projectId, Long actorUserId);

    ProjectQualificationFact lockAndRevalidate(RevalidationCommand command);

    record RevalidationCommand(
            Long tenantId,
            Long projectId,
            Long actorUserId,
            Integer expectedProjectVersion,
            Long expectedFactVersion,
            Long expectedScopeVersion,
            boolean requireProjectManager) {

        public RevalidationCommand {
            if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                    || actorUserId == null || actorUserId <= 0
                    || expectedProjectVersion == null || expectedProjectVersion < 0
                    || expectedFactVersion == null || expectedFactVersion < 0
                    || expectedScopeVersion == null || expectedScopeVersion < 0) {
                throw new IllegalArgumentException("invalid project qualification revalidation command");
            }
        }
    }

    record ProjectQualificationFact(
            Long projectId,
            Long actorUserId,
            Set<String> effectiveRoleCodes,
            String lifecycleStatus,
            String currentStage,
            Integer projectVersion,
            Long factVersion,
            Long scopeVersion) {

        public ProjectQualificationFact {
            if (projectId == null || projectId <= 0 || actorUserId == null || actorUserId <= 0
                    || effectiveRoleCodes == null || effectiveRoleCodes.isEmpty()
                    || lifecycleStatus == null || currentStage == null
                    || projectVersion == null || projectVersion < 0
                    || factVersion == null || factVersion < 0
                    || scopeVersion == null || scopeVersion < 0) {
                throw new IllegalArgumentException("invalid project qualification fact");
            }
            effectiveRoleCodes = Set.copyOf(effectiveRoleCodes);
        }
    }
}
