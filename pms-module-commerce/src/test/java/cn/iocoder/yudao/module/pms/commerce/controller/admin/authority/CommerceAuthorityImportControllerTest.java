package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority;

import cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo.CommerceAuthorityImportBatchReqVO;
import cn.iocoder.yudao.module.pms.commerce.service.authority.CommerceAuthorityImportApplicationService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CommerceAuthorityImportControllerTest {

    @Test
    void shouldMapTrustedHeaderToEveryRecordWithoutClientIdentityFields() throws Exception {
        CommerceAuthorityImportController controller = new CommerceAuthorityImportController(
                mock(CommerceAuthorityImportApplicationService.class), mock(Environment.class));
        LocalDateTime sourceTime = LocalDateTime.of(2026, 8, 29, 12, 0);
        CommerceAuthorityImportBatchReqVO request = new CommerceAuthorityImportBatchReqVO("batch-1",
                List.of(new CommerceAuthorityImportBatchReqVO.ContractRecord(
                        "C-1", "2", "DPTECH-DEMO", "CT-1", "合同", "ENABLED", sourceTime)),
                List.of(new CommerceAuthorityImportBatchReqVO.SalesOrderRecord(
                        "O-1", "2", "DPTECH-DEMO", "NORMAL", "SO-1", "ENABLED", sourceTime)),
                List.of(new CommerceAuthorityImportBatchReqVO.SalesOrderLineRecord(
                        "L-1", "2", "O-1", "10", "ITEM-1", "设备", "PRODUCT-1",
                        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "SET", 0,
                        "CONFIRMED", "ENABLED", sourceTime)));

        var command = controller.toCommand(0L, "ERP", "op-1", request);

        assertEquals(0L, command.tenantId());
        assertEquals("op-1", command.operationId());
        assertTrue(command.contracts().stream().allMatch(value -> "ERP".equals(value.sourceSystem())));
        assertTrue(command.salesOrders().stream().allMatch(value -> "ERP".equals(value.sourceSystem())));
        assertTrue(command.salesOrderLines().stream().allMatch(value -> "ERP".equals(value.sourceSystem())));
        List<String> requestFields = Arrays.stream(CommerceAuthorityImportBatchReqVO.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertFalse(requestFields.contains("tenantId"));
        assertFalse(requestFields.contains("actorUserId"));
        for (Class<?> type : List.of(
                CommerceAuthorityImportBatchReqVO.ContractRecord.class,
                CommerceAuthorityImportBatchReqVO.SalesOrderRecord.class,
                CommerceAuthorityImportBatchReqVO.SalesOrderLineRecord.class)) {
            assertFalse(Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName).toList().contains("sourceSystem"));
        }
        PreAuthorize permission = CommerceAuthorityImportController.class
                .getMethod("create", String.class, String.class, CommerceAuthorityImportBatchReqVO.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('pms:commerce:authority:write')", permission.value());
    }

    @Test
    void shouldRejectEmptyBatchAtControllerValidationBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(
                    new CommerceAuthorityImportBatchReqVO("batch", List.of(), List.of(), List.of()));
            assertTrue(violations.stream().anyMatch(value ->
                    value.getMessage().contains("至少包含一条来源记录")));
        }
    }
}
