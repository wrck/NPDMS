package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

/** Exact formal-company and contract-number lookup for authorized migration references. */
public record ContractMigrationIdentityQuery(Long tenantId,String companyCode,String contractNo) {}
