package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query;

/** 在项目满意度任务事实锁内查找已存在首轮，供手动和自动发起共同去重。 */
public record SatisfactionFirstTaskQuery(Long tenantId, Long projectId) {
}
