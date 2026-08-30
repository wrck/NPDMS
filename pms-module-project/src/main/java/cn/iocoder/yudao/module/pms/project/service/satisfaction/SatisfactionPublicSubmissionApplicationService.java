package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Keeps the committed Response when the independent Result transaction fails and is retried. */
@Service
@RequiredArgsConstructor
public class SatisfactionPublicSubmissionApplicationService {
    private final SatisfactionResponseSubmissionService responseService;
    private final SatisfactionResultDecisionService resultService;

    public SubmissionOutcome submit(Command command) {
        SatisfactionResponseSubmissionService.SubmissionResult response = responseService.submit(
                new SatisfactionResponseSubmissionService.Command(command.tenantId(), command.token(),
                        command.requestId(), "PUBLIC_LINK", command.customerContactRef(), null,
                        command.answerSnapshot(), command.files(), "public-link"));
        SatisfactionResultDecisionService.DecisionResult result = resultService.decide(
                new SatisfactionResultDecisionService.Command(command.tenantId(), response.taskId(),
                        response.questionnaireId(), response.responseId(), "sat-result:" + response.responseId()));
        return new SubmissionOutcome(response.responseId(), result.resultId(), result.score(), result.threshold(),
                result.passed(), response.replayed(), result.replayed());
    }

    public record Command(Long tenantId, String token, String requestId, String customerContactRef,
                          String answerSnapshot, List<SatisfactionResponseSubmissionService.FileFact> files) {
    }

    public record SubmissionOutcome(Long responseId, Long resultId, java.math.BigDecimal score,
                                    java.math.BigDecimal threshold, boolean passed,
                                    boolean responseReplayed, boolean resultReplayed) {
    }
}
