package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;
import java.util.Set;
public record DeviceProjectOrganizationListQuery(Long tenantId, Set<Long> projectIds) {}
