package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record GeneratedBusinessFileCommand(
        Long tenantId, Long actorUserId, String operationId,
        Long resultId, Long collectionTaskId, Long questionnaireId,
        Long responseId, Integer expectedTaskVersion,
        String ownerContext, String objectType, String purposeCode,
        Long scopeVersion, String fileName, String contentType, byte[] content) {

    public GeneratedBusinessFileCommand {
        if (tenantId == null || tenantId < 0 || actorUserId == null || actorUserId <= 0
                || operationId == null || operationId.isBlank() || operationId.trim().length() > 128
                || resultId == null || resultId <= 0 || collectionTaskId == null || collectionTaskId <= 0
                || questionnaireId == null || questionnaireId <= 0 || responseId == null || responseId <= 0
                || expectedTaskVersion == null || expectedTaskVersion < 0
                || ownerContext == null || ownerContext.isBlank() || objectType == null || objectType.isBlank()
                || purposeCode == null || purposeCode.isBlank() || scopeVersion == null || scopeVersion < 0
                || fileName == null || fileName.isBlank() || fileName.length() > 256
                || contentType == null || contentType.isBlank() || contentType.length() > 128
                || content == null || content.length == 0) {
            throw new IllegalArgumentException("invalid generated business file command");
        }
        operationId = operationId.trim();
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
