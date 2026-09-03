package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query;

public record DeliverableCurrentSourceClearQuery(Long tenantId, Long deliverableId,
                                                 Long expectedCurrentSourceVersionId,
                                                 Integer expectedRootVersion, String updater) {
}
