package cn.iocoder.yudao.module.pms.project.api.organization;
import java.util.List;
import java.util.Set;
/** 当前租户项目组织事实；设备消费者负责已授权设备的详情读取。 */
public interface ProjectDeviceOrganizationApi {
    record Organization(Long projectId, Long companyId, String companyName,
                        Long departmentId, String departmentCode, String departmentName, String projectName,
                        String projectCode) {}
    /** 仅为消费者筛选提供候选ID，消费者仍须与自身授权范围取交集。 */
    record NameQuery(Long tenantId, String keyword) {}
    Set<Long> findProjectIdsByName(NameQuery query);
    Set<Long> visibleProjectIds(Long tenantId, Long userId);
    List<Organization> getOrganizations(Long tenantId, Set<Long> projectIds);
}
