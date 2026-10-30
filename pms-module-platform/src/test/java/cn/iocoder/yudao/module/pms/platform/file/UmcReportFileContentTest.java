package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanResult;
import cn.iocoder.yudao.module.pms.platform.service.file.BoundedMultipartReader;
import cn.iocoder.yudao.module.pms.platform.service.file.FileContentPolicyService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.BoundedFileContentValidationCommand;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UmcReportFileContentTest {
    private static final String WORDML = "application/vnd.ms-wordml";
    private static final byte[] DOC = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<?mso-application progid=\"Word.Document\"?>"
            + "<w:wordDocument xmlns:w=\"http://schemas.microsoft.com/office/word/2003/wordml\">"
            + "<w:body><w:p><w:r><w:t>UMC report</w:t></w:r></w:p></w:body></w:wordDocument>").getBytes(StandardCharsets.UTF_8);

    @Test void savesWord2003DocWithoutChangingBytesAndStillScans() {
        var scans = new AtomicInteger();
        var files = new FileContentPolicyService(new BoundedMultipartReader(), List.of(scan -> {
            scans.incrementAndGet(); return new FileSecurityScanResult("PASSED", "TEST", "1", null);
        }), true);
        var result = files.validateBounded(command(DOC, "umc.doc", WORDML, policy("INSPECTION_REPORT", Set.of(WORDML))));
        assertArrayEquals(DOC, result.content());
        assertEquals(WORDML, result.mediaType());
        assertEquals("doc", result.extension());
        assertEquals(1, scans.get());
    }

    @Test void rejectsMislabeledDocAndOwnerWithoutWordmlPermission() {
        var files = new FileContentPolicyService(new BoundedMultipartReader(), List.of(), false);
        assertThrows(ServiceException.class, () -> files.validateBounded(command(DOC, "umc.doc", "application/msword",
                policy("INSPECTION_REPORT", Set.of("application/msword")))));
        assertThrows(ServiceException.class, () -> files.validateBounded(command(DOC, "umc.doc", WORDML,
                policy("OTHER", Set.of(WORDML)))));
        assertThrows(ServiceException.class, () -> files.validateBounded(command("<html>login</html>".getBytes(StandardCharsets.UTF_8),
                "umc.doc", WORDML, policy("INSPECTION_REPORT", Set.of(WORDML)))));
        assertThrows(ServiceException.class, () -> files.validateBounded(command(DOC, "umc.docx", WORDML,
                policy("INSPECTION_REPORT", Set.of(WORDML)))));
    }
    private static FileBusinessObjectPolicyFact policy(String category, Set<String> media) {
        return new FileBusinessObjectPolicyFact(true, 1L, "MUTABLE", "MULTIPLE", Set.of(category), media, 1_048_576L, "INTERNAL");
    }
    private static BoundedFileContentValidationCommand command(byte[] bytes, String name, String media, FileBusinessObjectPolicyFact policy) {
        return new BoundedFileContentValidationCommand(bytes, name, bytes.length, media, null, policy);
    }
}
