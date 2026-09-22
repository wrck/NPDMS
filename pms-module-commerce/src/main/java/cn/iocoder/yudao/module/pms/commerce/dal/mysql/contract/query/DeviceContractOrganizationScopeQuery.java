package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;
import java.util.List;
public record DeviceContractOrganizationScopeQuery(Long tenantId,List<Grant> grants,java.util.Set<String> contractNumbers) {
    public DeviceContractOrganizationScopeQuery(Long tenantId,List<Grant> grants) {
        this(tenantId,grants,null);
    }
    public record Grant(Long companyId, Long departmentId, String departmentCode) {}
}
