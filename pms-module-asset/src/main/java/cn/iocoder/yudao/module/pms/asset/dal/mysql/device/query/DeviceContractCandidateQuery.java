package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

/** 授权前内部候选集，不作为可见设备结果返回，也不提前分页。 */
public record DeviceContractCandidateQuery(Long tenantId, Long deviceId, String sn, String productCode,
        Long projectId, Long customerId, String name, String status, java.util.List<DeviceOrganizationGrant> organizationGrants) {}
