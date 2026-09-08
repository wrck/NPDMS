package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.query;
/** PM-03: trusted tenant and stable template identity lock. */
public record TemplateIdentityQuery(Long tenantId, Long templateId) { }
