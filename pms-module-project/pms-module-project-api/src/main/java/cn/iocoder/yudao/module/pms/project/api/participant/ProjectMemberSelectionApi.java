package cn.iocoder.yudao.module.pms.project.api.participant;

import java.util.List;
import lombok.Builder;

/** 当前项目有效成员的选择事实；不授予成员管理或项目权限。 */
public interface ProjectMemberSelectionApi {
    Page page(Query query);
    Member lockAndInspect(Revalidation query);
    @Builder
    record Query(Long tenantId, Long actorId, Long projectId, int pageNo, int pageSize, String role) {}
    @Builder
    record Revalidation(Long tenantId, Long actorId, Long projectId, Long assignmentId, Long expectedVersion, String role) {}
    record Member(Long assignmentId, Long version, String name, String phone) {}
    record Page(List<Member> members, long total) {}
}
