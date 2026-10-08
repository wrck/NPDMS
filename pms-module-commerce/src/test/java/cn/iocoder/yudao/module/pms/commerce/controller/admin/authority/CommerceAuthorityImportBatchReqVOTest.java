package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSourceLifecycleStatus;
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

    private SalesOrderLineRecord line(String unit, String status, BigDecimal quantity) {
        return new SalesOrderLineRecord("synthetic-line", null, "v1", "synthetic-order", "1",
                "synthetic-product", null, null, quantity, BigDecimal.ZERO, BigDecimal.TEN,
                unit, 0, status, CommerceSourceLifecycleStatus.ACTIVE, LocalDateTime.of(2026, 10, 7, 0, 0));
    }
}
