package cn.iocoder.yudao.module.pms.platform.api.file.dto;
public record BusinessGrantGeneratedFilePolicyQuery(Long tenantId,String ownerContext,String objectType,Long objectId,
 Long expectedOwnerVersion,Long grantId,Long issuanceVersion,String purposeCode,Long expectedScopeVersion) {}
