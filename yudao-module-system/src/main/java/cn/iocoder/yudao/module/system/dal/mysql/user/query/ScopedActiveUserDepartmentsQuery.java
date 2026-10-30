package cn.iocoder.yudao.module.system.dal.mysql.user.query;
import java.util.Set;
/** Only metadata of currently qualified user IDs; never a directory search. */
public record ScopedActiveUserDepartmentsQuery(Long tenantId, Set<Long> userIds) { }
