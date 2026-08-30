package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfactionPublicSubmissionApplicationServiceTest {
    @Mock SatisfactionResponseSubmissionService responseService;
    @Mock SatisfactionResultDecisionService resultService;

    @Test
    void commitsResponseThenUsesStableResponseIdentityForDecision() {
        when(responseService.submit(any())).thenReturn(new SatisfactionResponseSubmissionService.SubmissionResult(
                12L, 11L, 10L, false, new BigDecimal("5.0"), new BigDecimal("4.00"), true, "RULE-1"));
        when(resultService.decide(any())).thenReturn(new SatisfactionResultDecisionService.DecisionResult(
                "sat-result:12", 7L, 20L, 21L, 10L, 1, 11L, 30L, 12L, 12L, 1,
                "SAT-10", "ACC", "AcceptanceActivity", "100", 1L, new BigDecimal("5.0"),
                new BigDecimal("4.00"), true, "RULE-1", "EFFECTIVE", 99L, null, false));
        var service = new SatisfactionPublicSubmissionApplicationService(responseService, resultService);

        var result = service.submit(new SatisfactionPublicSubmissionApplicationService.Command(
                7L, "token", "request-1", "customer", "{}", List.of()));

        ArgumentCaptor<SatisfactionResultDecisionService.Command> decision =
                ArgumentCaptor.forClass(SatisfactionResultDecisionService.Command.class);
        verify(resultService).decide(decision.capture());
        assertEquals("sat-result:12", decision.getValue().operationId());
        assertEquals(12L, result.resultId());
    }
}
