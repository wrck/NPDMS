package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SatisfactionAssistedResponseApplicationService {
    private final SatisfactionResponseSubmissionService responseService;
    private final SatisfactionResultDecisionService resultService;

    public Outcome submit(SatisfactionResponseSubmissionService.AssistedCommand command) {
        var response = responseService.submitAssisted(command);
        var result = resultService.decide(new SatisfactionResultDecisionService.Command(command.tenantId(),
                response.taskId(), response.questionnaireId(), response.responseId(),
                "sat-result:" + response.responseId()));
        return new Outcome(response.responseId(), result.resultId(), result.score(), result.threshold(),
                result.passed(), response.replayed(), result.replayed());
    }

    public record Outcome(Long responseId, Long resultId, java.math.BigDecimal score,
                          java.math.BigDecimal threshold, boolean passed,
                          boolean responseReplayed, boolean resultReplayed) {}
}
