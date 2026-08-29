package cn.iocoder.yudao.module.pms.project.api.commerce;

import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFactQuery;

/** 分配发生时目标项目所属SYSTEM办事处权威事实。 */
public interface ProjectOfficeFactApi {

    ProjectOfficeFact resolve(ProjectOfficeFactQuery query);

    ProjectOfficeFact lockAndRevalidate(ProjectOfficeFactQuery query);
}
