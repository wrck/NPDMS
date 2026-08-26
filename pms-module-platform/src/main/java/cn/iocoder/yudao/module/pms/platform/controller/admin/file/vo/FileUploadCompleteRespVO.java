package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

public record FileUploadCompleteRespVO(
        Long artifactId,
        Integer versionNo,
        Long referenceId,
        String referenceKey,
        String sha256) {
}
