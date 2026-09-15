package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCandidate;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchFacts;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_AMBIGUOUS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_CANDIDATE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NO_MATCH;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_SELECTABLE;

/** 统一执行模板匹配前属性判定，并把既有TemplateMatcher结果转换为稳定决策。 */
@Service
public class ProjectAttributeResolutionService {

    @Resource
    private ProjectTemplateService projectTemplateService;

    public TemplateMatchDecision resolveInitial(ProjectMasterDO project, Long selectedRevisionId,
                                                String candidateWatermark) {
        ProjectAttributeSnapshot normalized = TemplateMatchDecisionRules.requireManualCreationAttributes(attributes(project));
        return resolveInitialNormalized(ProjectRuleFields.manualCreationFacts(project).withAttributes(normalized), selectedRevisionId, candidateWatermark);
    }

    public TemplateMatchDecision resolveSourceInitial(ProjectMasterDO project, Long selectedRevisionId,
                                                      String candidateWatermark) {
        return resolveInitialNormalized(ProjectRuleFields.creationFacts(project).withAttributes(normalizeCommon(attributes(project))), selectedRevisionId, candidateWatermark);
    }

    private TemplateMatchDecision resolveInitialNormalized(TemplateMatchFacts facts,
                                                           Long selectedRevisionId,
                                                           String candidateWatermark) {
        TemplateMatchResult match = projectTemplateService.matchPreview(facts);
        if (candidateWatermark == null || !candidateWatermark.equals(match.getCandidateWatermark())) {
            throw exception(PROJECT_TEMPLATE_CANDIDATE_VERSION_CONFLICT);
        }
        if (selectedRevisionId != null) {
            TemplateMatchCandidate selected = match.getCandidates().stream()
                    .filter(candidate -> selectedRevisionId.equals(candidate.getTemplateRevisionId()))
                    .findFirst().orElseThrow(() -> exception(PROJECT_TEMPLATE_NOT_SELECTABLE));
            return decision(match, TemplateMatchDecisionRules.DECISION_EXPLICIT, selected);
        }
        if (match.getOutcome() == TemplateMatchResult.Outcome.NO_MATCH) {
            throw exception(PROJECT_TEMPLATE_NO_MATCH, String.join("；", match.getConflicts()));
        }
        if (match.getOutcome() == TemplateMatchResult.Outcome.MULTI_MATCH) {
            throw exception(PROJECT_TEMPLATE_AMBIGUOUS, String.join("；", match.getConflicts()));
        }
        return decision(match, TemplateMatchDecisionRules.DECISION_AUTO_UNIQUE, match.getMatched());
    }

    public TemplateMatchDecision evaluateImpact(ProjectMasterDO project, ProjectAttributeSnapshot attributes) {
        ProjectAttributeSnapshot normalized = normalizeCommon(attributes);
        TemplateMatchResult match = projectTemplateService.matchPreview(ProjectRuleFields.creationFacts(project).withAttributes(normalized));
        TemplateMatchCandidate matched = match.getOutcome() == TemplateMatchResult.Outcome.MATCHED
                ? match.getMatched() : null;
        return decision(match, null, matched);
    }

    private ProjectAttributeSnapshot attributes(ProjectMasterDO project) {
        return new ProjectAttributeSnapshot(project.getSigningMethod(), project.getProjectCategory(),
                project.getImplementationMode(), project.getMajorProjectLevel());
    }

    private TemplateMatchDecision decision(TemplateMatchResult match, String decisionMode,
                                           TemplateMatchCandidate selected) {
        String matchResult = switch (match.getOutcome()) {
            case MATCHED -> TemplateMatchDecisionRules.MATCH_UNIQUE;
            case NO_MATCH -> TemplateMatchDecisionRules.MATCH_NO_MATCH;
            case MULTI_MATCH -> TemplateMatchDecisionRules.MATCH_MULTIPLE;
        };
        return new TemplateMatchDecision(matchResult, match.getCandidateWatermark(),
                TemplateMatchDecisionRules.MATCHER_VERSION, decisionMode,
                selected == null ? null : selected.getTemplateId(),
                selected == null ? null : selected.getTemplateRevisionId(),
                selected == null ? null : selected.getLatestRevisionNo());
    }

    private ProjectAttributeSnapshot normalizeCommon(ProjectAttributeSnapshot attributes) {
        return TemplateMatchDecisionRules.requireCommonAttributes(attributes);
    }
}
