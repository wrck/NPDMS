package cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalQuantityScopeFact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ArrivalAcceptanceRulesTest {

    @Test
    void acceptsAnEvidenceBackedSubsetOfTheCurrentExpectedScope() {
        ArrivalAcceptanceRules rules = new ArrivalAcceptanceRules();
        ArrivalQuantityScopeFact expected = quantity("10");
        ArrivalQuantityScopeFact accepted = quantity("4");

        assertDoesNotThrow(() -> rules.validateSubmission(
                Set.of(11L, 12L), List.of(expected), Set.of(11L), List.of(accepted), true));
    }

    private static ArrivalQuantityScopeFact quantity(String value) {
        return new ArrivalQuantityScopeFact(20L, "PRODUCT", "MODEL",
                new BigDecimal(value), "UNIT");
    }
}
