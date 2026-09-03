package cn.iocoder.yudao.module.pms.project.api.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateResolveQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionTemplateCandidateRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfactionQuestionnaireTemplateApiImplTest {

    @Mock
    private SatisfactionQuestionnaireTemplateMapper templateMapper;
    private SatisfactionQuestionnaireTemplateApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        api = new SatisfactionQuestionnaireTemplateApiImpl(templateMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsUniqueHighestPriorityPublishedCandidate() {
        when(templateMapper.selectPublishedCandidates(any())).thenReturn(List.of(
                candidate(1L, 11L, 100), candidate(2L, 22L, 50)));

        var fact = api.resolvePublished(query());

        assertEquals("FOUND", fact.outcome());
        assertEquals(1L, fact.templateId());
        assertEquals(11L, fact.templateRevisionId());
    }

    @Test
    void distinguishesNoMatchAndAmbiguousTopPriority() {
        when(templateMapper.selectPublishedCandidates(any())).thenReturn(List.of());
        assertEquals("NO_MATCH", api.resolvePublished(query()).outcome());

        when(templateMapper.selectPublishedCandidates(any())).thenReturn(List.of(
                candidate(1L, 11L, 100), candidate(2L, 22L, 100)));
        assertEquals("AMBIGUOUS", api.resolvePublished(query()).outcome());
    }

    private static SatisfactionTemplateResolveQuery query() {
        return new SatisfactionTemplateResolveQuery(0L, "FACC002_EXACT", "STANDARD", "ON_SITE",
                "ACCEPTANCE", "AFTER_INITIAL_ACCEPTANCE");
    }

    private static SatisfactionTemplateCandidateRecord candidate(long templateId, long revisionId, int priority) {
        return new SatisfactionTemplateCandidateRecord(templateId, revisionId, 1, "RULE-V1",
                new BigDecimal("80.00"), priority);
    }
}
