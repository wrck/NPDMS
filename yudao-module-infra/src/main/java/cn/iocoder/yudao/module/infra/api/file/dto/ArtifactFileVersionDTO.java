package cn.iocoder.yudao.module.infra.api.file.dto;

public record ArtifactFileVersionDTO(
        Long fileArtifactId,
        Long fileVersionId,
        String name,
        String contentType,
        long size,
        String contentSha256,
        String storageKey,
        String scanStatus,
        String accessScope) {
}