package cn.iocoder.yudao.module.pms.platform.service.file.command;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import org.springframework.web.multipart.MultipartFile;

public record FileContentValidationCommand(
        MultipartFile file,
        String expectedFileName,
        long declaredSizeBytes,
        String declaredMediaType,
        String clientSha256,
        FileBusinessObjectPolicyFact policy) {
}
