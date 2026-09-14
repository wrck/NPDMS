package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskIdentityQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskProjectQuery;

/** PROJ冻结节点WorkBinding权威事实；业务Owner不读取PROJ业务表。 */
public interface ProjectWorkBindingFactApi {

    ProjectWorkBindingFact inspect(ProjectWorkBindingFactQuery query);
    ProjectWorkBindingFact inspectTask(cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTaskFactQuery query);

    ProjectWorkBindingFact lockAndRevalidate(ProjectWorkBindingFactRevalidationQuery query);

    ProjectWorkBindingFact inspectStage(cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingStageFactQuery query);

    /** 锁定绑定事实与版本，不授予办理权限；业务写入仍须通过 ProjectNodeExecutionApi/ProjectBusinessExecutionApi。 */
    ProjectWorkBindingFact lockAndRevalidateStage(cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingStageFactRevalidationQuery query);

    ProjectSatisfactionTaskFact lockCurrentSatisfactionTask(ProjectSatisfactionTaskIdentityQuery query);

    ProjectSatisfactionTaskFact lockCurrentSatisfactionTaskByProject(ProjectSatisfactionTaskProjectQuery query);

    ProjectSatisfactionTaskFact lockAndRevalidateSatisfactionTask(ProjectSatisfactionTaskFactQuery query);

}
