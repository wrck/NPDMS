package cn.iocoder.yudao.module.pms.platform.service.file.command;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;

public record BoundedFileContentValidationCommand(
        byte[] content,
        String expectedFileName,
        long declaredSizeBytes,
        String declaredMediaType,
        String clientSha256,
        FileBusinessObjectPolicyFact policy) {
}
