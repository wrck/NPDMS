package cn.iocoder.yudao.module.pms.platform.service.file.command;

public record FileUploadCompleted(
        Long artifactId,
        Integer versionNo,
        Long referenceId,
        String referenceKey,
        String sha256) {
}
