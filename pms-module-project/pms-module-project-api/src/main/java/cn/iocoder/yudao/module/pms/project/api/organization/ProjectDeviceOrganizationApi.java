package cn.iocoder.yudao.module.pms.project.api.organization;
import java.util.List;
import java.util.Set;
/** 当前租户项目组织事实；设备消费者负责已授权设备的详情读取。 */
public interface ProjectDeviceOrganizationApi {
    record Organization(Long projectId, Long companyId, String companyName,
                        Long departmentId, String departmentCode, String departmentName) {}
    Set<Long> visibleProjectIds(Long tenantId, Long userId);
    List<Organization> getOrganizations(Long tenantId, Set<Long> projectIds);
}
