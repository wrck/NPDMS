package cn.iocoder.yudao.module.pms.project.service.projectmember;

import java.time.LocalDateTime;
import java.util.List;

/** PM-01：当前主责与有效成员集合分开表达。 */
public record ProjectManagerMemberResult(Long projectId, Integer version, Long primaryUserId,
                                        String assignmentStatus, boolean changed,
                                        List<Member> members) {
    public record Member(Long assignmentId, Long userId, String name, LocalDateTime effectiveFrom) { }
}
