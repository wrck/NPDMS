package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record FileSecurityScanCommand(
        byte[] validatedContent,
        String fileName,
        String declaredMediaType,
        String detectedMediaType,
        String sha256) {

    public FileSecurityScanCommand {
        if (validatedContent == null || validatedContent.length == 0
                || fileName == null || fileName.isBlank()
                || declaredMediaType == null || declaredMediaType.isBlank()
                || detectedMediaType == null || detectedMediaType.isBlank()
                || sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("invalid file security scan command");
        }
        validatedContent = validatedContent.clone();
    }

    @Override
    public byte[] validatedContent() {
        return validatedContent.clone();
    }
}
