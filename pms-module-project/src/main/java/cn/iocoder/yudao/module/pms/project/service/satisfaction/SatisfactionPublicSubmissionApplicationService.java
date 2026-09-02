package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFileFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadCompleteCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitialized;

/** Keeps the committed Response when the independent Result transaction fails and is retried. */
@Service
@RequiredArgsConstructor
public class SatisfactionPublicSubmissionApplicationService {
    private final SatisfactionResponseSubmissionService responseService;
    private final SatisfactionResultDecisionService resultService;
    private final SatisfactionResponseReservationService reservationService;
    private final FileArtifactApi fileArtifactApi;

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public BusinessGrantUploadInitialized initializeFile(InitializeFileCommand command) {
        var reservation = reservationService.reserveFromToken(command.tenantId(), command.token(),
                command.requestId());
        return fileArtifactApi.initializeBusinessGrantUpload(new BusinessGrantUploadInitializeCommand(
                command.tenantId(), reservation.grantId(), reservation.grantVersion(),
                reservation.questionnaireId(), command.requestId(), reservation.responseId(),
                command.policyKey(), command.operationId(), command.fileName(), command.categoryCode(),
                command.declaredSizeBytes(), command.declaredMediaType(), command.clientSha256()));
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public BusinessGrantFileFact completeFile(CompleteFileCommand command) {
        var reservation = reservationService.reserveFromToken(command.tenantId(), command.token(),
                command.requestId());
        if (!reservation.responseId().equals(command.responseId())) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_RESERVATION_CONFLICT");
        }
        return fileArtifactApi.completeBusinessGrantUpload(new BusinessGrantUploadCompleteCommand(
                command.tenantId(), reservation.grantId(), reservation.grantVersion(),
                reservation.questionnaireId(), command.requestId(), command.responseId(),
                command.policyKey(), command.operationId(), command.fileSlotKey(), command.fileSequence(),
                command.artifactId(), command.sessionId(), command.content(), command.clientSha256()));
    }

    public SubmissionOutcome submit(Command command) {
        SatisfactionResponseSubmissionService.SubmissionResult response = responseService.submit(
                new SatisfactionResponseSubmissionService.Command(command.tenantId(), command.token(),
                        command.requestId(), command.responseId(), "PUBLIC_LINK", command.customerContactRef(), null,
                        command.answerSnapshot(), command.files(), "public-link"));
        SatisfactionResultDecisionService.DecisionResult result = resultService.decide(
                new SatisfactionResultDecisionService.Command(command.tenantId(), response.taskId(),
                        response.questionnaireId(), response.responseId(), "sat-result:" + response.responseId()));
        return new SubmissionOutcome(response.responseId(), result.resultId(), result.score(), result.threshold(),
                result.passed(), response.replayed(), result.replayed());
    }

    public record Command(Long tenantId, String token, String requestId, Long responseId, String customerContactRef,
                          String answerSnapshot, List<SatisfactionResponseSubmissionService.FileFact> files) {
    }

    public record InitializeFileCommand(Long tenantId, String token, String requestId, String policyKey,
                                        String operationId, String fileName, String categoryCode,
                                        Long declaredSizeBytes, String declaredMediaType, String clientSha256) {
    }

    public record CompleteFileCommand(Long tenantId, String token, String requestId, Long responseId,
                                      String policyKey, String operationId, String fileSlotKey,
                                      Integer fileSequence, Long artifactId, Long sessionId,
                                      byte[] content, String clientSha256) {
    }

    public record SubmissionOutcome(Long responseId, Long resultId, java.math.BigDecimal score,
                                    java.math.BigDecimal threshold, boolean passed,
                                    boolean responseReplayed, boolean resultReplayed) {
    }
}
