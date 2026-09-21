package cn.iocoder.yudao.module.infra.controller.admin.file;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FileControllerTest {

    @Test
    void blocksReceiptDirectoryAfterDecodingAndKeepsOrdinaryFilesReadable() throws Exception {
        FileService service = mock(FileService.class);
        FileController controller = new FileController();
        ReflectionTestUtils.setField(controller, "fileService", service);

        for (String protectedPath : new String[] {"pms-storage-receipts/op-501", "PMS-STORAGE-RECEIPTS%2Fop-501"}) {
            var request = new MockHttpServletRequest("GET", "/admin-api/infra/file/51/get/" + protectedPath);
            request.setRequestURI("/admin-api/infra/file/51/get/" + protectedPath);
            var response = new MockHttpServletResponse();

            controller.getFileContent(request, response, 51L);

            assertEquals(404, response.getStatus());
        }
        verifyNoInteractions(service);

        String collationVariant = "pms-stórage-receipts/op-501";
        when(service.getFileByConfigIdAndPath(51L, collationVariant)).thenReturn(new FileDO()
                .setPath("pms-storage-receipts/op-501").setName("receipt.pdf"));
        var variantRequest = new MockHttpServletRequest("GET", "/admin-api/infra/file/51/get/" + collationVariant);
        variantRequest.setRequestURI("/admin-api/infra/file/51/get/" + collationVariant);
        var variantResponse = new MockHttpServletResponse();

        controller.getFileContent(variantRequest, variantResponse, 51L);

        assertEquals(404, variantResponse.getStatus());
        verify(service).getFileByConfigIdAndPath(51L, collationVariant);
        verify(service, never()).getFileContent(51L, collationVariant);

        byte[] content = new byte[] {1, 2, 3};
        when(service.getFileByConfigIdAndPath(51L, "ordinary/receipt.txt"))
                .thenReturn(new FileDO().setName("receipt.txt"));
        when(service.getFileContent(51L, "ordinary/receipt.txt")).thenReturn(content);
        var ordinaryRequest = new MockHttpServletRequest("GET", "/admin-api/infra/file/51/get/ordinary/receipt.txt");
        ordinaryRequest.setRequestURI("/admin-api/infra/file/51/get/ordinary/receipt.txt");
        var ordinaryResponse = new MockHttpServletResponse();

        controller.getFileContent(ordinaryRequest, ordinaryResponse, 51L);

        assertEquals(200, ordinaryResponse.getStatus());
        verify(service).getFileContent(51L, "ordinary/receipt.txt");
        verify(service).getFileByConfigIdAndPath(51L, "ordinary/receipt.txt");
    }
}
