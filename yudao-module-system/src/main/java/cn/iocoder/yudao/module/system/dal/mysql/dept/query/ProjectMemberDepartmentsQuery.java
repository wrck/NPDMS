package cn.iocoder.yudao.module.system.dal.mysql.dept.query;
import java.util.Set;
/** Names of candidate/page departments, always under ordinary department data permission. */
public record ProjectMemberDepartmentsQuery(Long tenantId, Set<Long> departmentIds, String keyword) { }
