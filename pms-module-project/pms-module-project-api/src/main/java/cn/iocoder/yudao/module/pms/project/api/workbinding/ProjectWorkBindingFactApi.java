package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskIdentityQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskProjectQuery;

/** PROJ冻结任务WorkBinding只读权威事实。 */
public interface ProjectWorkBindingFactApi {

    ProjectWorkBindingFact inspect(ProjectWorkBindingFactQuery query);

    ProjectWorkBindingFact lockAndRevalidate(ProjectWorkBindingFactRevalidationQuery query);

    ProjectSatisfactionTaskFact lockCurrentSatisfactionTask(ProjectSatisfactionTaskIdentityQuery query);

    ProjectSatisfactionTaskFact lockCurrentSatisfactionTaskByProject(ProjectSatisfactionTaskProjectQuery query);

    ProjectSatisfactionTaskFact lockAndRevalidateSatisfactionTask(ProjectSatisfactionTaskFactQuery query);

}
