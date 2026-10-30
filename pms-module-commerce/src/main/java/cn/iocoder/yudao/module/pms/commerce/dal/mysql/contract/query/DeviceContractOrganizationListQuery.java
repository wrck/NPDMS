package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;
import java.util.Set;
public record DeviceContractOrganizationListQuery(Long tenantId,Set<String> contractNumbers) {}
