package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

public interface InspectionRulePublicationService {

    DisableResult disable(DisableCommand command);

    record DisableCommand(Long revisionId, Integer expectedVersion, String idempotencyKey, String correlationId) {
    }

    record DisableResult(Long revisionId, String statusCode, Integer version, boolean replayed) {
    }
}
