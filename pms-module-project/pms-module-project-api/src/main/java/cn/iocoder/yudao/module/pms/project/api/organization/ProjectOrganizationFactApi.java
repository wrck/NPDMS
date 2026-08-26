package cn.iocoder.yudao.module.pms.project.api.organization;

import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactQuery;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactRevalidationQuery;

/** PROJ项目所属组织只读权威事实。 */
public interface ProjectOrganizationFactApi {

    ProjectOrganizationFact inspect(ProjectOrganizationFactQuery query);

    ProjectOrganizationFact lockAndRevalidate(ProjectOrganizationFactRevalidationQuery query);
}
