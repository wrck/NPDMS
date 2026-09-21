package cn.iocoder.yudao.module.infra.controller.admin.file;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.infra.service.file.FileStorageReceiptAccessService;
import org.junit.jupiter.api.Test;
import cn.iocoder.yudao.module.infra.service.file.FileReceiptDownloadService;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStorageReceiptContentControllerTest {

    @Test
    void hidesUnknownTicketAndWritesSafeInlineContentWithoutCaching() throws Exception {
        var service = mock(FileStorageReceiptAccessService.class);
        var controller = new FileStorageReceiptContentController(service, mock(FileReceiptDownloadService.class));
        var missing = new MockHttpServletResponse();
        when(service.read("unknown")).thenReturn(null);

        controller.content("unknown", missing);

        assertEquals(404, missing.getStatus());
        verify(service).read("unknown");

        var response = new MockHttpServletResponse();
        when(service.read("opaque")).thenReturn(new FileStorageReceiptAccessService.ReceiptContent(
                "notes.txt", "text/plain;charset=UTF-8", new byte[] {1, 2, 3}));
        controller.content("opaque", response);

        assertEquals(200, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertEquals("3", response.getHeader("Content-Length"));
        assertTrue(response.getHeader("Content-Disposition").startsWith("inline;"));
        Method method = FileStorageReceiptContentController.class.getMethod("content", String.class, jakarta.servlet.http.HttpServletResponse.class);
        assertFalse(method.getAnnotation(ApiAccessLog.class).enable());
    }

    @Test
    void preservesUnexpiredLegacyDownloadTickets() throws Exception {
        var legacy = mock(FileReceiptDownloadService.class);
        var controller = new FileStorageReceiptContentController(mock(FileStorageReceiptAccessService.class), legacy);
        when(legacy.read("existing-ticket")).thenReturn(new FileReceiptDownloadService.Download("evidence.pdf", new byte[] {1, 2}));
        var response = new MockHttpServletResponse();
        controller.content("existing-ticket", response);
        assertEquals(200, response.getStatus());
        assertArrayEquals(new byte[] {1, 2}, response.getContentAsByteArray());
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
    }

    @Test
    void mapsStorageFailureToServiceUnavailableWithoutRethrowingTheTicket() throws Exception {
        var service = mock(FileStorageReceiptAccessService.class);
        var controller = new FileStorageReceiptContentController(service, mock(FileReceiptDownloadService.class));
        var response = new MockHttpServletResponse();
        when(service.read("opaque")).thenThrow(new IllegalStateException("storage failed"));

        assertDoesNotThrow(() -> controller.content("opaque", response));

        assertEquals(503, response.getStatus());
    }
}
