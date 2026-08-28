package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.util.Set;

/** 创建人基础查看范围查询。candidateProjectIds为null时查询租户内全部创建项目。 */
public record CreatedProjectScopeQuery(Long tenantId, String creatorId, Set<Long> candidateProjectIds) {
}
