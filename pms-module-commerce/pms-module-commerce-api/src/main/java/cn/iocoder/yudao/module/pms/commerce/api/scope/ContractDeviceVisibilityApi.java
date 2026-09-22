package cn.iocoder.yudao.module.pms.commerce.api.scope;
import java.util.Set;
import java.util.List;
/** 设备授权使用的只读合同范围及组织事实；不按合同号猜测跨公司归属。 */
public interface ContractDeviceVisibilityApi {
    record Organization(String contractNo, Long companyId, String companyName,
                        Long departmentId, String departmentCode, String departmentName) {}
    Set<String> getVisibleContractNumbers(Long tenantId, Long userId);
    Set<String> getOrganizationVisibleContractNumbers(Long tenantId, Long userId);
    /** 只校验候选合同；null/空集合均无结果，候选集本身不授予权限。 */
    Set<String> getVisibleContractNumbers(Long tenantId, Long userId, Set<String> candidates);
    List<Organization> getOrganizations(Long tenantId, Set<String> contractNumbers);
}
