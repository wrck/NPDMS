package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCandidate;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_CANDIDATE_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAttributeResolutionServiceTest {

    @Mock
    private ProjectTemplateService projectTemplateService;
    @InjectMocks
    private ProjectAttributeResolutionService service;

    @Test
    void uniqueManualCandidateUsesAutoDecision() {
        TemplateMatchResult result = TemplateMatchResult.matched(candidate(1L, 11L));
        result.setCandidateWatermark("watermark");
        when(projectTemplateService.matchPreview("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null))
                .thenReturn(result);

        var decision = service.resolveInitial(attributes(), null, "watermark");

        assertEquals(TemplateMatchDecisionRules.MATCH_UNIQUE, decision.matchResult());
        assertEquals(TemplateMatchDecisionRules.DECISION_AUTO_UNIQUE, decision.decisionMode());
        assertEquals(11L, decision.matchedTemplateRevisionId());
        assertEquals("watermark", decision.candidateDigest());
    }

    @Test
    void multiCandidateAllowsOnlyCurrentExplicitRevision() {
        TemplateMatchResult result = TemplateMatchResult.multiMatch(
                List.of("冲突"), List.of(candidate(1L, 11L), candidate(2L, 22L)));
        result.setCandidateWatermark("watermark");
        when(projectTemplateService.matchPreview("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null))
                .thenReturn(result);

        var decision = service.resolveInitial(attributes(), 22L, "watermark");

        assertEquals(TemplateMatchDecisionRules.MATCH_MULTIPLE, decision.matchResult());
        assertEquals(TemplateMatchDecisionRules.DECISION_EXPLICIT, decision.decisionMode());
        assertEquals(2L, decision.matchedTemplateId());
    }

    @Test
    void staleCandidateWatermarkIsRejected() {
        TemplateMatchResult result = TemplateMatchResult.matched(candidate(1L, 11L));
        result.setCandidateWatermark("new");
        when(projectTemplateService.matchPreview("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null))
                .thenReturn(result);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveInitial(attributes(), null, "old"));

        assertEquals(PROJECT_TEMPLATE_CANDIDATE_VERSION_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void impactNoMatchDoesNotInventTemplateOrDecisionMode() {
        TemplateMatchResult result = TemplateMatchResult.noMatch("无候选");
        result.setCandidateWatermark("watermark");
        when(projectTemplateService.matchPreview("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null))
                .thenReturn(result);

        var decision = service.evaluateImpact(attributes());

        assertEquals(TemplateMatchDecisionRules.MATCH_NO_MATCH, decision.matchResult());
        assertNull(decision.decisionMode());
        assertNull(decision.matchedTemplateRevisionId());
    }

    @Test
    void sourceInitialMayCarryCrmMajorLevel() {
        ProjectAttributeSnapshot source = new ProjectAttributeSnapshot(
                "DIRECT_SIGN", "ENGINEERING", "DIRECT_SERVICE", "MAJOR_A");
        TemplateMatchResult result = TemplateMatchResult.matched(candidate(1L, 11L));
        result.setCandidateWatermark("watermark");
        when(projectTemplateService.matchPreview("DIRECT_SIGN", "ENGINEERING", "DIRECT_SERVICE", "MAJOR_A"))
                .thenReturn(result);

        var decision = service.resolveSourceInitial(source, null, "watermark");

        assertEquals(11L, decision.matchedTemplateRevisionId());
    }

    private ProjectAttributeSnapshot attributes() {
        return new ProjectAttributeSnapshot("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null);
    }

    private TemplateMatchCandidate candidate(Long templateId, Long revisionId) {
        TemplateMatchCandidate candidate = new TemplateMatchCandidate();
        candidate.setTemplateId(templateId);
        candidate.setTemplateRevisionId(revisionId);
        candidate.setLatestRevisionNo(1);
        candidate.setCode("TPL-" + templateId);
        candidate.setName("模板" + templateId);
        candidate.setMatchPriority(10);
        return candidate;
    }
}
