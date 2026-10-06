package cn.iocoder.yudao.module.pms.platform.api.file.dto;
public record NativeGeneratedFilePolicyQuery(Long tenantId,Long actorUserId,String ownerContext,
        String objectType,Long objectId,Long expectedOwnerVersion,String purposeCode,Long expectedScopeVersion) {}
