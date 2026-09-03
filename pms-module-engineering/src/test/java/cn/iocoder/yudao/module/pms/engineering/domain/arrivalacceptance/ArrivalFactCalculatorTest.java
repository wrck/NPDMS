package cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalQuantityScopeFact;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalFactCalculator.CalculationInput;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalFactCalculator.DeviceContribution;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalFactCalculator.DeviceExemption;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalFactCalculator.QuantityContribution;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalFactCalculator.QuantityExemption;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArrivalFactCalculatorTest {

    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 8, 30, 8, 0);
    private final ArrivalFactCalculator calculator = new ArrivalFactCalculator();

    @Test
    void combinesMultipleConfirmedBatchesAndExplicitExemptionsIntoAcceptedFact() {
        var result = calculator.calculate(new CalculationInput(
                Set.of(11L, 12L), List.of(quantity("10")),
                List.of(new DeviceContribution(30L, 11L)),
                List.of(new QuantityContribution(20L, quantity("6"))),
                List.of(new DeviceExemption(40L, 12L, "approved reason", "accepted risk",
                        7L, CHECKED_AT.minusHours(1), 90L, 1, CHECKED_AT.plusDays(1))),
                List.of(new QuantityExemption(40L, quantity("4"), "approved reason", "accepted risk",
                        7L, CHECKED_AT.minusHours(1), 90L, 1, CHECKED_AT.plusDays(1))),
                CHECKED_AT));

        assertEquals(ArrivalAcceptanceFact.DECISION_ACCEPTED, result.decision());
        assertEquals(List.of(20L, 30L, 40L), result.sourceAcceptanceIds());
        assertEquals(Set.of(11L), result.acceptedDeviceIds());
        assertEquals(Set.of(12L), result.exemptedDeviceIds());
        assertEquals(List.of(quantity("6")), result.acceptedQuantityScopes());
        assertEquals(List.of(quantity("4")), result.exemptedQuantityScopes());
        assertEquals(List.of(), result.unmetQuantityScopes());
    }

    @Test
    void reportsTheRemainingCurrentScopeWithoutPromotingTheProject() {
        var result = calculator.calculate(new CalculationInput(
                Set.of(11L, 12L), List.of(quantity("10")),
                List.of(new DeviceContribution(20L, 11L)),
                List.of(new QuantityContribution(20L, quantity("3"))),
                List.of(), List.of(), CHECKED_AT));

        assertEquals(ArrivalAcceptanceFact.DECISION_NOT_ACCEPTED, result.decision());
        assertEquals(Set.of(12L), result.unmetDeviceIds());
        assertEquals(List.of(quantity("7")), result.unmetQuantityScopes());
    }

    private static ArrivalQuantityScopeFact quantity(String value) {
        return new ArrivalQuantityScopeFact(20L, "PRODUCT", "MODEL",
                new BigDecimal(value), "UNIT");
    }
}
