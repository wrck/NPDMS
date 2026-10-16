package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceLogArchiveService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceLogArchiveControllerTest {
    final DeviceLogArchiveService service=mock(DeviceLogArchiveService.class);
    final DeviceLogArchiveController controller=new DeviceLogArchiveController(service,org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceArchivedLogParsingService.class));
    @Test void rawLogBodyIsNotReadByExistingAccessLogBodyExtractor() throws Exception {
        byte[] bytes="SN: SN001\npassword transient-value\n".getBytes(StandardCharsets.UTF_8);
        var request=new MockHttpServletRequest();request.setContentType("application/octet-stream");request.setContent(bytes);
        assertNull(ServletUtils.getBody(request));
        controller.upload(new DeviceLogArchiveController.UploadRequest(10L,"operation","device.log",null),request);
        verify(service).upload(argThat(command -> command.projectId().equals(10L) && command.operationId().equals("operation") && command.fileName().equals("device.log") && java.util.Arrays.equals(command.bytes(), bytes)));
    }
    @Test void oversizedChunkedInputStopsBeforeDacOrArchive() {
        var request=new MockHttpServletRequest() {
            @Override public long getContentLengthLong() {return -1;}
        };
        request.setContent(new byte[20*1024*1024+1]);
        assertThrows(IllegalArgumentException.class,()->controller.upload(new DeviceLogArchiveController.UploadRequest(10L,"operation","device.log",null),request));
        verifyNoInteractions(service);
    }
}
