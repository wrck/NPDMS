package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** 是否已有服务经理任命历史，用于区分首次初始化与后续维护。 */
public record ProjectServiceManagerHistoryQuery(Long tenantId, Long projectId) { }
