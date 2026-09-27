package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query;

public record DeliverableCurrentSourceClearQuery(Long tenantId, Long deliverableId,
                                                 Long expectedCurrentSourceVersionId,
                                                 Long expectedRootVersion, String updater) {
}
