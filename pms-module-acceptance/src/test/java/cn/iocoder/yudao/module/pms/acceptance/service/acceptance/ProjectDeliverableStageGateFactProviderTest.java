package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectDeliverableStageGateFactProviderTest {

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void cachedAcceptedStatusCannotReplaceCurrentSourceAndRuleValidation() {
        AccProjectDeliverableMapper mapper = mock(AccProjectDeliverableMapper.class);
        AccProjectDeliverableDO row = new AccProjectDeliverableDO();
        row.setId(31L);
        row.setStatus("ACCEPTED");
        row.setVersion(5L);
        when(mapper.selectGateFactForUpdate(any())).thenReturn(row);

        var rules = mock(cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi.class);
        var submissions = mock(ProjectDeliverableSubmissionService.class);
        org.springframework.beans.factory.ObjectProvider<ProjectDeliverableSubmissionService> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(submissions);
        when(submissions.revalidate(row)).thenReturn(new ProjectDeliverableSubmissionService.Evaluation(false, "FILE_EVIDENCE_UNAVAILABLE", "{}"));
        var fact = new ProjectDeliverableStageGateFactProvider(mapper, rules, provider).lockAndRevalidate(
                new ProjectStageGateFactQuery(7L, 9L, "S0", 21L, "G-01", 0,
                        22L, 0, "DELIVERABLE", "D-01", null, null));

        assertEquals(ProjectStageGateOutcome.UNSATISFIED, fact.outcome());
        assertEquals("31", fact.ownerObjectKey());
    }
}
