package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

public interface InspectionRulePublicationService {

    ReviewResult recordSecurityReview(ReviewCommand command);

    PublishResult publish(PublishCommand command);

    DisableResult disable(DisableCommand command);

    record ReviewCommand(
            Long revisionId,
            Integer expectedVersion,
            String conclusionCode,
            String idempotencyKey,
            String correlationId) {
    }

    record ReviewResult(
            String reviewReference,
            Long revisionId,
            String contentDigest,
            String conclusionCode,
            java.time.LocalDateTime reviewedAt,
            boolean replayed) {
    }

    record PublishCommand(Long revisionId, Integer expectedVersion, String idempotencyKey, String correlationId) {
    }

    record PublishResult(
            Long revisionId,
            String statusCode,
            Integer version,
            Long disabledRevisionId,
            String contentDigest,
            String reviewReference,
            boolean replayed) {
    }

    record DisableCommand(Long revisionId, Integer expectedVersion, String idempotencyKey, String correlationId) {
    }

    record DisableResult(Long revisionId, String statusCode, Integer version, boolean replayed) {
    }
}
