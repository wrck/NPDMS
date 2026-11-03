package cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query;

/** 本租户本项目当前有效安装记录引用的站点。 */
public record InstallationProjectSitesQuery(Long tenantId, Long projectId) { }
