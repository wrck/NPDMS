package cn.iocoder.yudao.module.pms.project.api.stagegate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;

import java.util.Set;

/** 阶段门禁引用的Owner事实提供者；实现只能声明固定providerKey。 */
public interface ProjectStageGateFactProviderApi {

    String PROVIDER_PROJ_TASK = "PROJ_TASK";
    String PROVIDER_PROJ_MILESTONE = "PROJ_MILESTONE";
    String PROVIDER_PROJ_STATE = "PROJ_STATE";
    String PROVIDER_ACC_DELIVERABLE = "ACC_DELIVERABLE";
    String PROVIDER_BPM_APPROVAL = "BPM_APPROVAL";
    String PROVIDER_BPM_PROCESS = "BPM_PROCESS";

    Set<String> providerKeys();

    ProjectStageGateFact lockAndRevalidate(ProjectStageGateFactQuery query);
}
