package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

public record CutoverChecklistCustomRemoveUpdate(Long tenantId, Long itemId, Integer expectedVersion,
                                                 Long actorId) {
}
