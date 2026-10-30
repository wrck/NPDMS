package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops.DeviceOpsCallbackReceiptDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.deviceops.DeviceOpsCallbackReceiptMapper;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionLogIngestionApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackResultDTO;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DacCallbackServiceTest {
    @Test void failedReceiptUpdateCannotAcknowledgeIngestedResult() {
        var receipts = mock(DeviceOpsCallbackReceiptMapper.class);
        var ingestion = mock(CollectionLogIngestionApi.class);
        when(receipts.insert(any(DeviceOpsCallbackReceiptDO.class))).thenAnswer(call -> {
            ((DeviceOpsCallbackReceiptDO) call.getArgument(0)).setId(17L);
            return 1;
        });
        when(ingestion.ingest(any(), any())).thenReturn(new CollectionCallbackResultDTO(
                "callback", "task", "RESULT_AVAILABLE", "CALLBACK_RECEIVED", 1L, 99L, null, false));
        var service = new DacCallbackService(receipts, ingestion);
        var metadata = new DacCallbackMetadata(7L, "callback", "task", "dac", "SUCCEEDED", 1, 1, 3, "hash", null, null);
        assertThrows(IllegalStateException.class, () -> service.receive(metadata, "same", new ByteArrayInputStream(new byte[0])));
        verify(ingestion).ingest(any(), any());
        when(receipts.updateById(any(DeviceOpsCallbackReceiptDO.class))).thenReturn(1);
        assertEquals("ACKNOWLEDGED", service.receive(metadata, "same", new ByteArrayInputStream(new byte[0])).status());
    }

    @Test void failedReceiptInsertCannotIngestOrAcknowledge() {
        var receipts = mock(DeviceOpsCallbackReceiptMapper.class);
        var ingestion = mock(CollectionLogIngestionApi.class);
        var service = new DacCallbackService(receipts, ingestion);
        var metadata = new DacCallbackMetadata(7L, "callback", "task", "dac", "SUCCEEDED", 1, 1, 3, "hash", null, null);
        assertThrows(IllegalStateException.class, () -> service.receive(metadata, "same", new ByteArrayInputStream(new byte[0])));
        verifyNoInteractions(ingestion);
    }

    @Test void duplicateReturnsOriginalReceiptAndNeverStoresAnotherFile() {
        var receipts = mock(DeviceOpsCallbackReceiptMapper.class);
        var ingestion = mock(CollectionLogIngestionApi.class);
        var existing = new DeviceOpsCallbackReceiptDO();
        existing.setId(17L); existing.setEvidenceDigest("same"); existing.setResponseJson("{}");
        when(receipts.find(7L, "callback")).thenReturn(existing);
        var metadata = new DacCallbackMetadata(7L, "callback", "task", "dac", "SUCCEEDED", 1, 1, 3, "hash", null, null);
        var service = new DacCallbackService(receipts, ingestion);
        assertEquals(17L, service.receive(metadata, "same", new ByteArrayInputStream(new byte[0])).receiptId());
        assertThrows(IllegalStateException.class, () -> service.receive(metadata, "changed", new ByteArrayInputStream(new byte[0])));
        verifyNoInteractions(ingestion);
    }
}
