package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi.Organization;
import java.util.List;
import java.util.Set;

public record OrganizationProjectScopeQuery(Long tenantId, List<Organization> organizations,
                                           Set<Long> candidateProjectIds, boolean activeOnly) { }
