package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority;

import cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo.CommerceAuthorityImportBatchReqVO;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSourceLifecycleStatus;
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
        CommerceAuthorityImportBatchReqVO request = new CommerceAuthorityImportBatchReqVO(
                "batch-1", "wm-1", sourceTime,
                List.of(new CommerceAuthorityImportBatchReqVO.ContractRecord(
                        "C-1", null, "2", "DPTECH-DEMO", "CT-1", "合同",
                        null, null, null, null, CommerceSourceLifecycleStatus.ACTIVE, sourceTime)),
                List.of(new CommerceAuthorityImportBatchReqVO.SalesOrderRecord(
                        "O-1", null, "2", "DPTECH-DEMO", "SO-1", "NORMAL",
                        null, null, null, null, CommerceSourceLifecycleStatus.ACTIVE, sourceTime)),
                List.of(new CommerceAuthorityImportBatchReqVO.SalesOrderLineRecord(
                        "L-1", null, "2", "O-1", "10", "ITEM-1", "设备", "PRODUCT-1",
                        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "SET", 0,
                        "CONFIRMED", CommerceSourceLifecycleStatus.ACTIVE, sourceTime)), List.of());

        var command = controller.toCommand(0L, "ERP", "op-1", "corr-1", request);

        assertEquals(0L, command.tenantId());
        assertEquals("op-1", command.eventId());
        assertEquals("ERP", command.sourceSystem());
        assertEquals("corr-1", command.correlationId());
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
                .getMethod("create", String.class, String.class, String.class,
                        CommerceAuthorityImportBatchReqVO.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('pms:commerce:authority:write')", permission.value());
    }

    @Test
    void mapsOptionalCallerRowVersionWithoutChangingTrustedIdentityOrBusinessPayload() {
        var controller = new CommerceAuthorityImportController(
                mock(CommerceAuthorityImportApplicationService.class), mock(Environment.class));
        var time = LocalDateTime.of(2026, 8, 29, 12, 0);
        var order = new CommerceAuthorityImportBatchReqVO.SalesOrderRecord(
                "O-1", "V1", "V2", "DPTECH-DEMO", "SO-1", "NORMAL", null, null, null, null,
                CommerceSourceLifecycleStatus.ACTIVE, time, null, null, "Current note", time, time, null, null, null, 7L);
        var request = new CommerceAuthorityImportBatchReqVO("batch-guarded", "wm", time,
                List.of(), List.of(order), List.of(), List.of());

        var command = controller.toCommand(1L, "ERP", "op-guarded", "corr-guarded", request);

        assertEquals(7L, command.salesOrders().getFirst().expectedRowVersion());
        assertEquals("Current note", command.salesOrders().getFirst().orderComment());
        assertEquals(1L, command.tenantId());
        assertEquals("ERP", command.sourceSystem());
    }

    @Test
    void mapsOptionalLineCallerRowVersionWithoutAllowingClientParentCompanyIdentity() {
        var controller = new CommerceAuthorityImportController(
                mock(CommerceAuthorityImportApplicationService.class), mock(Environment.class));
        var time = LocalDateTime.of(2026, 8, 29, 12, 0);
        var line = new CommerceAuthorityImportBatchReqVO.SalesOrderLineRecord(
                "L-1", "V1", "V2", "O-1", "10", "Current Product", "Current Description", null,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "PCS", 0, "CONFIRMED",
                CommerceSourceLifecycleStatus.ACTIVE, time, null, null, null, null, null, null, 7);
        var request = new CommerceAuthorityImportBatchReqVO("batch-line-guarded", "wm", time,
                List.of(), List.of(), List.of(line), List.of());

        var command = controller.toCommand(1L, "ERP", "op-line-guarded", "corr-line-guarded", request);

        assertEquals(7, command.orderLines().getFirst().expectedRowVersion());
        assertEquals("Current Product", command.orderLines().getFirst().productCode());
        assertEquals("O-1", command.orderLines().getFirst().salesOrderSourceKey());
        assertEquals(1L, command.tenantId());assertEquals("ERP", command.sourceSystem());
        assertFalse(Arrays.stream(CommerceAuthorityImportBatchReqVO.SalesOrderLineRecord.class.getRecordComponents())
                .map(RecordComponent::getName).toList().contains("companyCode"));
    }

    @Test
    void shouldRejectEmptyBatchAtControllerValidationBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(
                    new CommerceAuthorityImportBatchReqVO("batch", "wm", LocalDateTime.now(),
                            List.of(), List.of(), List.of(), List.of()));
            assertTrue(violations.stream().anyMatch(value ->
                    value.getMessage().contains("至少包含一条来源记录")));
        }
    }
}
