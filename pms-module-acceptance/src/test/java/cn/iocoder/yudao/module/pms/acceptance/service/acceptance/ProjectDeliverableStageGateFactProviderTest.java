package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一交付件门禁事实（P06R）：身份与状态取自平台 plt_delivery_requirement，
 * 满足性来自统一承接重验（材料失效撤回、判定回填），缓存状态不能替代重验。
 */
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
    void cachedSatisfiedStatusCannotReplaceCurrentSourceAndRuleValidation() {
        var rules = mock(ProjectDeliverableRuleApi.class);
        var platform = mock(PlatformDeliveryRequirementApi.class);
        var view = new TemplateFrozenView(31L, 9L, "D-01", "需求分析报告", "S0", null, null, null,
                true, 1, null, "SATISFIED", "{}", 5);
        when(platform.lockByIdentity(9L, "D-01")).thenReturn(Optional.of(view));
        when(platform.findById(31L)).thenReturn(Optional.of(view));
        var submissions = mock(ProjectDeliverableSubmissionService.class);
        org.springframework.beans.factory.ObjectProvider<ProjectDeliverableSubmissionService> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(submissions);
        when(submissions.revalidate(view)).thenReturn(
                new ProjectDeliverableSubmissionService.Evaluation(false, "FILE_EVIDENCE_UNAVAILABLE", "{}"));

        var fact = new ProjectDeliverableStageGateFactProvider(rules, platform, provider).lockAndRevalidate(
                new ProjectStageGateFactQuery(7L, 9L, "S0", 21L, "D-01", 0,
                        22L, 0, "DELIVERABLE", "D-01", null, null));

        assertEquals(ProjectStageGateOutcome.UNSATISFIED, fact.outcome());
        assertEquals("31", fact.ownerObjectKey());
        assertEquals("ACCEPTED", fact.ownerBusinessVersion());
        assertEquals("FILE_EVIDENCE_UNAVAILABLE", fact.unmetCode());
        verify(rules).lock(9L, "D-01");
    }

    @Test
    void missingRequirementFailsWithDependencyUnavailable() {
        var rules = mock(ProjectDeliverableRuleApi.class);
        var platform = mock(PlatformDeliveryRequirementApi.class);
        when(platform.lockByIdentity(9L, "D-404")).thenReturn(Optional.empty());
        var fact = new ProjectDeliverableStageGateFactProvider(rules, platform, null).lockAndRevalidate(
                new ProjectStageGateFactQuery(7L, 9L, "S0", 21L, "D-404", 0,
                        22L, 0, "DELIVERABLE", "D-404", null, null));
        assertEquals(ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE, fact.outcome());
        assertEquals("DELIVERABLE_NOT_FOUND", fact.unmetCode());
    }

    @Test
    void satisfiedGateReportsAccStatusAndPlatformVersion() {
        var rules = mock(ProjectDeliverableRuleApi.class);
        var platform = mock(PlatformDeliveryRequirementApi.class);
        var view = new TemplateFrozenView(31L, 9L, "D-01", "需求分析报告", "S0", null, null, null,
                true, 1, null, "CONFIRMED", "{}", 6);
        when(platform.lockByIdentity(9L, "D-01")).thenReturn(Optional.of(view));
        when(platform.findById(31L)).thenReturn(Optional.of(view));
        var submissions = mock(ProjectDeliverableSubmissionService.class);
        org.springframework.beans.factory.ObjectProvider<ProjectDeliverableSubmissionService> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(submissions);
        when(submissions.revalidate(view)).thenReturn(
                new ProjectDeliverableSubmissionService.Evaluation(true, "DELIVERABLE_RULE_SATISFIED", "{}"));
        var instance = new ProjectDeliverableStageGateFactProvider(rules, platform, provider);
        assertEquals(Set.of(ProjectStageGateFactProviderApi.PROVIDER_ACC_DELIVERABLE), instance.providerKeys());
        var fact = instance.lockAndRevalidate(new ProjectStageGateFactQuery(7L, 9L, "S0", 21L, "D-01", 0,
                22L, 0, "DELIVERABLE", "D-01", null, null));
        assertEquals(ProjectStageGateOutcome.SATISFIED, fact.outcome());
        assertEquals("CONFIRMED", fact.ownerBusinessVersion());
        assertEquals("6", fact.factVersion());
    }
}
