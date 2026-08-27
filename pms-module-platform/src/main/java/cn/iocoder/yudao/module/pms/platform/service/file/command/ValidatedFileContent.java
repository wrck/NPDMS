package cn.iocoder.yudao.module.pms.platform.service.file.command;

public record ValidatedFileContent(
        byte[] content,
        long sizeBytes,
        String sha256,
        String mediaType,
        String extension,
        String scanStatusCode,
        String scanProviderCode,
        String scanProviderVersion) {
}
