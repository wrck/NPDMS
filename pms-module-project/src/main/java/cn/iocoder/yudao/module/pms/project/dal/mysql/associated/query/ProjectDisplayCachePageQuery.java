package cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query;

/** sourceProjectCode is derived from a project already checked by the project access Owner. */
public record ProjectDisplayCachePageQuery(Long tenantId,String sourceProjectCode,int offset,int limit) {}
