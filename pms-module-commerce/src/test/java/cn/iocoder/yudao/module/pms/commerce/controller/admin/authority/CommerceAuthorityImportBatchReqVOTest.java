package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSourceLifecycleStatus;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo.CommerceAuthorityImportBatchReqVO;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo.CommerceAuthorityImportBatchReqVO.SalesOrderLineRecord;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CommerceAuthorityImportBatchReqVOTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void missingUnitRemainsPendingWithoutInventingAUnit() {
        assertTrue(validator.validate(line(null, "PENDING_AUTHORITY", BigDecimal.TEN)).isEmpty());
    }

    @Test
    void confirmedQuantityStillRequiresAUnit() {
        for (String unit : new String[]{null, "", " "}) {
            assertFalse(validator.validate(line(unit, "CONFIRMED", BigDecimal.TEN)).isEmpty());
        }
    }

    @Test
    void confirmedQuantityWithItsSourceUnitRemainsValid() {
        assertTrue(validator.validate(line("EA", "CONFIRMED", BigDecimal.TEN)).isEmpty());
    }

    @Test
    void pendingStatusDoesNotAllowNegativeQuantity() {
        assertFalse(validator.validate(line(null, "PENDING_AUTHORITY", BigDecimal.ONE.negate())).isEmpty());
    }

    @Test
    void callerRowVersionRejectsNegativeValuesAndAcceptsZeroOrOmission() {
        var time = LocalDateTime.of(2026, 10, 7, 0, 0);
        for (Long version : new Long[]{null, 0L, -1L}) {
            var order = new CommerceAuthorityImportBatchReqVO.SalesOrderRecord(
                    "O-1", "V1", "V2", "DPTECH-DEMO", "SO-1", "NORMAL", null, null, null, null,
                    CommerceSourceLifecycleStatus.ACTIVE, time, null, null, null, null, null, null, null, null, version);
            var invalid = validator.validate(order);
            assertEquals(version != null && version < 0, invalid.stream().anyMatch(
                    violation -> "expectedRowVersion".equals(violation.getPropertyPath().toString())));
            if (version != null && version < 0) {
                var error = assertThrows(cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.class,
                        () -> new cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSalesOrderFact(
                                "O-1", "V1", "V2", "DPTECH-DEMO", "SO-1", "NORMAL", null, null, null, null,
                                CommerceSourceLifecycleStatus.ACTIVE, time, null, null, null, null, null, null, null, null, version));
                assertEquals(cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.Code.INVALID_REQUEST,
                        error.getCode());
            }
        }
    }

    @Test
    void lineCallerRowVersionRejectsNegativeValuesAndAcceptsZeroOrOmission() {
        var time = LocalDateTime.of(2026, 10, 7, 0, 0);
        for (Integer version : new Integer[]{null, 0, -1}) {
            var line = new SalesOrderLineRecord("L-1", "V1", "V2", "O-1", "10", "ITEM-1", null, null,
                    BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "PCS", 0, "CONFIRMED",
                    CommerceSourceLifecycleStatus.ACTIVE, time, null, null, null, null, null, null, version);
            var invalid = validator.validate(line);
            assertEquals(version != null && version < 0, invalid.stream().anyMatch(
                    violation -> "expectedRowVersion".equals(violation.getPropertyPath().toString())));
            if (version != null && version < 0) {
                var error = assertThrows(cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.class,
                        () -> new cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceOrderLineFact(
                                "L-1", "V1", "V2", "O-1", "10", "ITEM-1", null, null,
                                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "PCS", 0, "CONFIRMED",
                                CommerceSourceLifecycleStatus.ACTIVE, time, null, null, null, null, null, null, version));
                assertEquals(cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.Code.INVALID_REQUEST,
                        error.getCode());
            }
        }
    }

    private SalesOrderLineRecord line(String unit, String status, BigDecimal quantity) {
        return new SalesOrderLineRecord("synthetic-line", null, "v1", "synthetic-order", "1",
                "synthetic-product", null, null, quantity, BigDecimal.ZERO, BigDecimal.TEN,
                unit, 0, status, CommerceSourceLifecycleStatus.ACTIVE, LocalDateTime.of(2026, 10, 7, 0, 0));
    }
}
