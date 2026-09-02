package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssetProductTypeAuditServiceTest {

    @Mock private OperationAuditApi operationAuditApi;

    private AssetProductTypeAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AssetProductTypeAuditService(operationAuditApi);
    }

    @Test
    void shouldExposeOnlyDigestsAndSafeFactsInRejectionDetail() {
        ImportAssetProductTypeCommand command = command();
        AssetProductTypeImportRejectedException rejection =
                AssetProductTypeImportRejectedException.stale(command, time(11));

        Map<String, Object> detail = auditService.rejectionDetail(rejection);

        assertEquals("v2", detail.get("sourceVersion"));
        assertEquals(time(10), detail.get("sourceUpdatedAt"));
        assertEquals(time(11), detail.get("observedSourceUpdatedAt"));
        assertEquals("STALE_SOURCE", detail.get("rejectionCode"));
        assertEquals(0, detail.get("deviceCount"));
        assertEquals(64, String.valueOf(detail.get("sourceKeyDigest")).length());
        assertEquals(64, String.valueOf(detail.get("productTypeCodeDigest")).length());
        assertFalse(detail.containsKey("sourceKey"));
        assertFalse(detail.containsKey("displayName"));
        assertFalse(detail.containsKey("payloadHash"));
        assertFalse(detail.containsKey("password"));
        assertFalse(detail.containsKey("token"));
        assertFalse(detail.containsKey("secret"));
        assertFalse(detail.containsValue("source-secret-key"));
        assertFalse(detail.containsValue("敏感产品名称"));
        assertFalse(detail.containsValue("p".repeat(64)));
    }

    @Test
    void shouldRecordConflictWithDigestsInsteadOfRawSourceKeys() {
        ImportAssetProductTypeCommand command = command();
        AssetProductTypeImportRejectedException rejection =
                AssetProductTypeImportRejectedException.sourceConflict(command, "TYPE-B", time(9));
        ArgumentCaptor<Map<String, ?>> detailCaptor = ArgumentCaptor.forClass(Map.class);

        auditService.recordConflict(
                1L, 9L, rejection, "TYPE-B", "MES", "current-secret-key", "v1", time(9));

        verify(operationAuditApi).record(
                eq(1L), eq(9L), eq("op-1"), eq(AssetProductTypeAuditService.SOURCE_CONFLICT),
                eq("AssetProductTypeSourceMapping"), any(String.class), eq("SOURCE_CONFLICT"),
                detailCaptor.capture());
        Map<String, ?> detail = detailCaptor.getValue();
        assertEquals(64, String.valueOf(detail.get("incomingSourceKeyDigest")).length());
        assertEquals(64, String.valueOf(detail.get("currentSourceKeyDigest")).length());
        assertNotEquals("source-secret-key", detail.get("incomingSourceKeyDigest"));
        assertNotEquals("current-secret-key", detail.get("currentSourceKeyDigest"));
        assertFalse(detail.containsValue("source-secret-key"));
        assertFalse(detail.containsValue("current-secret-key"));
        assertFalse(detail.containsValue("敏感产品名称"));
        assertFalse(detail.containsValue("p".repeat(64)));
        assertTrue(detail.keySet().stream().noneMatch(key -> key.toLowerCase().contains("password")));
    }

    private ImportAssetProductTypeCommand command() {
        return new ImportAssetProductTypeCommand(
                "op-1", "idem-1", "TYPE-A", "敏感产品名称", true,
                "CRM", "source-secret-key", "v2", time(10), "p".repeat(64), List.of());
    }

    private LocalDateTime time(int hour) {
        return LocalDateTime.of(2026, 8, 30, hour, 0);
    }
}
