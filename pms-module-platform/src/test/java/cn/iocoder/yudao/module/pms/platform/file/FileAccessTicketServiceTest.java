package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileAccessGrantMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.FileAccessTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileAccessTicketServiceTest {

    @Test
    void publicAccessTicketStopsBeforeGrantWhenBusinessFileReadIsDenied() {
        FileArtifactApi fileArtifactApi = mock(FileArtifactApi.class);
        FileAccessGrantMapper accessGrantMapper = mock(FileAccessGrantMapper.class);
        FileStorageReceiptApi storageReceiptApi = mock(FileStorageReceiptApi.class);
        OperationAuditApi operationAuditApi = mock(OperationAuditApi.class);
        SecurityFrameworkService security = mock(SecurityFrameworkService.class);
        when(security.hasPermission("pms:file:download")).thenReturn(true);
        when(fileArtifactApi.inspect(any())).thenThrow(exception(FILE_SCOPE_FORBIDDEN));
        FileAccessTicketService service = new FileAccessTicketService(fileArtifactApi,
                mock(FileVersionMapper.class), accessGrantMapper, storageReceiptApi, operationAuditApi,
                security, mock(PlatformTransactionManager.class));
        ReflectionTestUtils.setField(service, "accessTicketTtl", Duration.ofMinutes(2));

        assertThrows(RuntimeException.class, () -> service.create(command()));

        verifyNoInteractions(accessGrantMapper, storageReceiptApi);
        verify(operationAuditApi, never()).record(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), eq("SUCCESS"), anyMap());
        verify(operationAuditApi).record(anyLong(), anyLong(), anyString(), eq("FILE_ACCESS_TICKET_CREATE"),
                eq("FileArtifact"), anyString(), eq("REJECTED"), anyMap());
    }

    private FileAccessTicketService.AccessCommand command() {
        return new FileAccessTicketService.AccessCommand(1L, 21L, 61L, 1,
                FileActionCodes.DOWNLOAD, "SOL", "DYNAMIC_FORM_INSTANCE", "51",
                "FORM_FIELD_ATTACHMENT", "PROJECT_BACKGROUND__ATTACHMENTS");
    }
}
