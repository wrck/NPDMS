package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;
/** 首次工期的项目当前PM存在前置；不从负责人投影回退。 */
public record DurationCurrentProjectManagerQuery(Long tenantId, Long projectId) { }
