package cn.iocoder.yudao.module.pms.platform.api.file.dto;

/** Authenticated business generation only; no public upload or anonymous grant semantics. */
public record NativeGeneratedFileCommand(Long tenantId, Long actorUserId, String operationId,
        String ownerContext, String objectType, Long objectId, Long expectedOwnerVersion,
        String purposeCode, String categoryCode, String fileName, String mediaType, byte[] content) {
    public NativeGeneratedFileCommand { content=content==null?null:content.clone(); }
    @Override public byte[] content() { return content==null?null:content.clone(); }
}
