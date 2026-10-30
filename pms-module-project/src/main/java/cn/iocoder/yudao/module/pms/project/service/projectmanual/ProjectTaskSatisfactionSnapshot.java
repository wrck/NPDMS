package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.SatisfactionQuestionnaireTemplateApi;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTemplateResolveQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;

/** The same Owner snapshot is frozen for initial creation and newly configured plan tasks. */
public final class ProjectTaskSatisfactionSnapshot {
    private ProjectTaskSatisfactionSnapshot() { }

    public static void freeze(ProjectMasterDO project, ProjectTaskInstanceDO task, SatisfactionQuestionnaireTemplateApi templates) {
        task.setAccSatisfactionTemplateId(null); task.setTemplateRevisionId(null); task.setTemplateVersion(null);
        task.setSatisfactionRuleVersion(null); task.setSatisfactionThreshold(null);
        if (task.getSatisfactionTiming() == null || task.getSatisfactionTiming().isBlank()) return;
        if (!"AFTER_INITIAL_ACCEPTANCE".equals(task.getSatisfactionTiming()))
            throw new IllegalStateException("SATISFACTION_TIMING_OWNER_NOT_AVAILABLE");
        var fact = templates.resolvePublished(new SatisfactionTemplateResolveQuery(project.getTenantId(), project.getProjectType(),
                project.getSigningMethod(), project.getImplementationMode(), "ACCEPTANCE",task.getSatisfactionTiming()));
        if (fact == null || !"FOUND".equals(fact.outcome()) || fact.templateId() == null || fact.templateRevisionId() == null
                || fact.templateVersion() == null || fact.ruleVersion() == null || fact.threshold() == null)
            throw new IllegalStateException("SATISFACTION_TEMPLATE_NOT_UNIQUE");
        task.setAccSatisfactionTemplateId(fact.templateId()); task.setTemplateRevisionId(fact.templateRevisionId());
        task.setTemplateVersion(fact.templateVersion()); task.setSatisfactionRuleVersion(fact.ruleVersion()); task.setSatisfactionThreshold(fact.threshold());
    }
}
