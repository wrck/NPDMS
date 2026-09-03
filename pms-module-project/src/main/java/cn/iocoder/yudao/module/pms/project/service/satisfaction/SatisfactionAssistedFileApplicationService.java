package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SatisfactionAssistedFileApplicationService {
    private final SatisfactionAssistedResponseReservationService reservationService;
    private final FileArtifactApi fileArtifactApi;

    @Transactional(rollbackFor = Exception.class)
    public SatisfactionAssistedResponseReservationService.Reservation reserve(
            Long tenantId, Long actorUserId, Long taskId, String requestId) {
        return reservationService.reserve(tenantId, actorUserId, taskId, requestId);
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticatedAssistedUploadInitialized initialize(InitializeCommand command) {
        var reservation = reservationService.reserve(command.tenantId(), command.actorUserId(),
                command.taskId(), command.requestId());
        if (!reservation.responseId().equals(command.responseId())) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_RESERVATION_CONFLICT");
        }
        return fileArtifactApi.initializeAuthenticatedAssistedUpload(
                new AuthenticatedAssistedUploadInitializeCommand(command.tenantId(), command.taskId(),
                        reservation.questionnaireId(), command.requestId(), reservation.responseId(),
                        command.policyKey(), command.operationId(), command.fileName(), command.categoryCode(),
                        command.declaredSizeBytes(), command.declaredMediaType(), command.clientSha256()));
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticatedAssistedFileFact complete(CompleteCommand command) {
        var reservation = reservationService.reserve(command.tenantId(), command.actorUserId(),
                command.taskId(), command.requestId());
        if (!reservation.responseId().equals(command.responseId())) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_RESERVATION_CONFLICT");
        }
        return fileArtifactApi.completeAuthenticatedAssistedUpload(
                new AuthenticatedAssistedUploadCompleteCommand(command.tenantId(), command.taskId(),
                        reservation.questionnaireId(), command.requestId(), reservation.responseId(),
                        command.policyKey(), command.operationId(), command.fileSlotKey(), command.fileSequence(),
                        command.artifactId(), command.sessionId(), command.content(), command.clientSha256()));
    }

    public record InitializeCommand(Long tenantId, Long actorUserId, Long taskId, String requestId,
                                    Long responseId, String policyKey, String operationId, String fileName,
                                    String categoryCode, Long declaredSizeBytes, String declaredMediaType,
                                    String clientSha256) {}
    public record CompleteCommand(Long tenantId, Long actorUserId, Long taskId, String requestId,
                                  Long responseId, String policyKey, String operationId, String fileSlotKey,
                                  Integer fileSequence, Long artifactId, Long sessionId, byte[] content,
                                  String clientSha256) {
        public CompleteCommand { content = content == null ? null : content.clone(); }
        @Override public byte[] content() { return content == null ? null : content.clone(); }
    }
}
