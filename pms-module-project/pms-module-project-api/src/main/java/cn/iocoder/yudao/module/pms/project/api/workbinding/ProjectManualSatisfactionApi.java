package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import java.util.List;

/** PROJ拥有首次手动调查的任务关联和配置冻结，ACC不得直接写项目表。 */
public interface ProjectManualSatisfactionApi {
    Options options(Long projectId, Long actorId);
    ProjectSatisfactionTaskFact freeze(Selection selection);
    record TaskOption(Long id, String name, String stageCode, Long templateId) { }
    record Options(boolean configured, List<TaskOption> tasks) { }
    record Selection(Long projectId, Long projectTaskId, Long templateId, Long revisionId, Long actorId) { }
}
