package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 成果归集入口（P06R）：归集目标 = 冻结计划 automaticSources 命中的统一要求实例。 */
class ProjectBusinessResultCollectionServiceTest {

    final ProjectDeliverableRuleApi rules = mock(ProjectDeliverableRuleApi.class);
    final PlatformDeliveryRequirementApi platform = mock(PlatformDeliveryRequirementApi.class);
    final ProjectDeliverableSubmissionService submissions = mock(ProjectDeliverableSubmissionService.class);
    final ProjectBusinessResultCollectionService service =
            new ProjectBusinessResultCollectionService(rules, platform, submissions);
    final BusinessResultSource.Type type =
            new BusinessResultSource.Type("SOL", "REQUIREMENT_ANALYSIS", "REQUIREMENT_ANALYSIS_COMPLETED");

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    private BusinessResultChange change(boolean formation) {
        var result = new BusinessResultSource.Result(7L, 9L, type, "55", "56", "1", "1",
                BusinessResultSource.Validity.CURRENT, LocalDateTime.parse("2026-09-20T12:00:00"));
        var source = new BusinessOperationResultEvent(UUID.randomUUID().toString(), 1, 7L, 9L, "SOL",
                "REQUIREMENT_ANALYSIS", "55", null, 1L, "1", "REQUIREMENT_ANALYSIS_COMPLETED",
                "REQUIREMENT_ANALYSIS_COMPLETE", "owner-command", 11L,
                LocalDateTime.parse("2026-09-20T12:00:00"), "test");
        return new BusinessResultChange(UUID.randomUUID().toString(), 1,
                new BusinessResultChange.Channel(1L, 7L, 9L, type), 8, source,
                formation ? BusinessResultSource.Observation.available(result)
                        : BusinessResultSource.Observation.absent(BusinessResultSource.Status.NOT_FOUND, "DELETED"),
                formation);
    }

    private static TemplateFrozenView requirement(String code) {
        return new TemplateFrozenView(31L, 9L, code, "分析报告", "S1", null, null, null, true, 1,
                null, "OPEN", "{}", 0);
    }

    @Test
    void formationChangesMapToThreePartSourceCodesAndCollectEachTargetedRow() {
        var change = change(true);
        when(rules.documentTargets(9L, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED"))
                .thenReturn(Set.of("D1"));
        when(platform.listByProject(9L)).thenReturn(List.of(requirement("D1"), requirement("D2")));
        service.onBusinessResultChange(change);
        verify(rules).documentTargets(9L, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED");
        verify(submissions).collectBusinessResult(requirement("D1"),
                "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED", change);
        // 未命中冻结目标（D2）的行不归集。
        verify(submissions, never()).collectBusinessResult(eq(requirement("D2")), any(), any());
    }

    @Test
    void revocationsForeignTenantsAndEmptyTargetsNeverTouchSubmissions() {
        service.onBusinessResultChange(change(false));
        verifyNoInteractions(rules, platform, submissions);
        TenantContextHolder.setTenantId(8L);
        assertThrows(IllegalArgumentException.class, () -> service.onBusinessResultChange(change(true)));
        TenantContextHolder.setTenantId(7L);
        when(rules.documentTargets(9L, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED"))
                .thenReturn(Set.of());
        service.onBusinessResultChange(change(true));
        verify(submissions, never()).collectBusinessResult(any(), any(), any());
    }
}
