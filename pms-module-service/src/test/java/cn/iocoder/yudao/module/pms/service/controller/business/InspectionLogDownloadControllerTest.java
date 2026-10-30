package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionLogDownloadService;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectionLogDownloadControllerTest {
    @Test void returnsUtf8TextAttachmentWithSafeFilenameAndNoStoreWithoutLoggingAFileBody() {
        var service = mock(InspectionLogDownloadService.class);
        var context = new InspectionRequestContext(1L, 2L, null, "download", null);
        byte[] text = "# 脱敏日志文本副本\r\n[stdout]\r\n".getBytes(StandardCharsets.UTF_8);
        when(service.download(new InspectionLogDownloadService.Query(context, 11L, 21L)))
                .thenReturn(new InspectionLogDownloadService.Download("inspection-11-21-设备甲-redacted.txt", text));
        try (var identity = mockStatic(InspectionRequestContext.class)) {
            identity.when(InspectionRequestContext::current).thenReturn(context);
            var result = new InspectionLogDownloadController(service).download(11L, 21L);
            assertEquals("text/plain;charset=UTF-8", result.getHeaders().getContentType().toString());
            assertEquals("no-store", result.getHeaders().getCacheControl());
            assertEquals("nosniff", result.getHeaders().getFirst("X-Content-Type-Options"));
            var disposition = ContentDisposition.parse(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
            assertEquals("attachment", disposition.getType()); assertEquals("inspection-11-21-设备甲-redacted.txt", disposition.getFilename());
            assertArrayEquals(text, result.getBody());
        }
    }
}
