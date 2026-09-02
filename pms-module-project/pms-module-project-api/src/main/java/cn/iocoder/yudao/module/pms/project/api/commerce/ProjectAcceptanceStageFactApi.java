package cn.iocoder.yudao.module.pms.project.api.commerce;

import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFact;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFactQuery;

/** 项目当前验收阶段及不可变阶段快照事实。 */
public interface ProjectAcceptanceStageFactApi {

    ProjectAcceptanceStageFact lockAndRead(ProjectAcceptanceStageFactQuery query);
}
