package cn.iocoder.yudao.module.pms.cutover.service.taskv2.port;

import java.util.Set;

/** CUT 对 PROJ 项目数据范围的消费端口。 */
public interface CutoverProjectScopePort {

    ProjectScopeFact inspect(Long actorId, Long projectId, String action);

    ProjectScopeFact lockAndRevalidate(Long actorId, Long projectId, String action,
                                         long expectedProjectScopeVersion);

    Set<Long> resolveAllCurrent(Long actorId, String action);

    record ProjectScopeFact(Long projectId, long projectScopeVersion, boolean allowed) {
    }
}
