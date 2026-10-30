package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;
import java.util.List;
public record DeviceOrganizationProjectQuery(Long tenantId, List<Grant> grants) {
    public record Grant(Long companyId, Long departmentId) {}
}
