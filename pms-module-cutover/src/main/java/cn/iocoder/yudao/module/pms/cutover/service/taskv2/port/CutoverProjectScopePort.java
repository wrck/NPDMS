package cn.iocoder.yudao.module.pms.cutover.service.taskv2.port;

import java.util.Set;
import lombok.Builder;

/** CUT 对 PROJ 项目数据范围的消费端口。 */
public interface CutoverProjectScopePort {

    ProjectScopeFact inspect(Long actorId, Long projectId, String action);

    ProjectScopeFact lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery query);

    Set<Long> resolveAllCurrent(Long actorId, String action);

    @Builder
    record RevalidationQuery(Long actorId, Long projectId, String action,
                             long expectedProjectScopeVersion) { }

    record ProjectScopeFact(Long projectId, long projectScopeVersion, boolean allowed) {
    }
}
