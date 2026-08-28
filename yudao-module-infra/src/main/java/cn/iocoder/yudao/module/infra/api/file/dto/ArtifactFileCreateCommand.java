package cn.iocoder.yudao.module.infra.api.file.dto;

public record ArtifactFileCreateCommand(
        Long tenantId,
        String sourceSystem,
        String sourceArtifactKey,
        String idempotencyKey,
        String name,
        String contentType,
        long declaredSize,
        String declaredSha256,
        String directory,
        String accessScope) {
}