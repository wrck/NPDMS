package cn.iocoder.yudao.module.pms.cutover.service.checklist.port;

/** INT-12/DAC采集消费边界；F-CUT-003只预留契约，不提供生产Provider。 */
public interface CutoverCollectionPort {

    RequestReceipt request(Request request);

    CollectionFact inspect(Inspection inspection);

    enum TechnicalStatus {
        ACCEPTED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    record Request(Long tenantId, Long actorId, Long projectId, Long taskId,
                   Long checklistId, Integer checklistVersion, Long checklistItemId,
                   Integer itemVersion, String stableItemKey, Long deviceId,
                   Long commandTemplateId, String idempotencyKey, String correlationId) {
    }

    record RequestReceipt(Long collectionTaskId, TechnicalStatus technicalStatus) {
    }

    record Inspection(Long tenantId, Long actorId, Long projectId, Long collectionTaskId,
                      Long taskId, Long checklistId, Long checklistItemId,
                      Long deviceId, Long commandTemplateId) {
    }

    record CollectionFact(Long collectionTaskId, TechnicalStatus technicalStatus,
                          Long resultReferenceId, Long resultVersion,
                          String resultSnapshot, String failureCode) {
    }
}
