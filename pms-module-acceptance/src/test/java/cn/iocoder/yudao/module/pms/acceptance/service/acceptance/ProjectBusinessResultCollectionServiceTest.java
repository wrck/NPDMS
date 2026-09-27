package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectBusinessResultCollectionServiceTest {
    final ProjectDeliverableRuleApi rules = mock(ProjectDeliverableRuleApi.class);
    final AccProjectDeliverableMapper deliverables = mock(AccProjectDeliverableMapper.class);
    final ProjectDeliverableSubmissionService submissions = mock(ProjectDeliverableSubmissionService.class);
    final ProjectBusinessResultCollectionService service = new ProjectBusinessResultCollectionService(rules, deliverables, submissions);
    final BusinessResultSource.Type type = new BusinessResultSource.Type("SOL", "REQUIREMENT_ANALYSIS", "REQUIREMENT_ANALYSIS_COMPLETED");

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        when(deliverables.selectDocuments(any())).thenReturn(List.of());
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    private BusinessResultChange change(boolean formation) {
        var result = new BusinessResultSource.Result(7L, 9L, type, "55", "56", "1", "1",
                BusinessResultSource.Validity.CURRENT, LocalDateTime.parse("2026-09-20T12:00:00"));
        var source = new BusinessOperationResultEvent(UUID.randomUUID().toString(), 1, 7L, 9L, "SOL", "REQUIREMENT_ANALYSIS",
                "55", null, 1L, "1", "REQUIREMENT_ANALYSIS_COMPLETED", "REQUIREMENT_ANALYSIS_COMPLETE", "owner-command", 11L,
                LocalDateTime.parse("2026-09-20T12:00:00"), "test");
        return new BusinessResultChange(UUID.randomUUID().toString(), 1, new BusinessResultChange.Channel(1L, 7L, 9L, type),
                8, source, formation ? BusinessResultSource.Observation.available(result)
                        : BusinessResultSource.Observation.absent(BusinessResultSource.Status.NOT_FOUND, "DELETED"), formation);
    }
    @Test void formationChangesMapToThreePartSourceCodesAndCollectEachTargetedRow() {
        var change = change(true);
        var row = new AccProjectDeliverableDO();
        row.setId(31L); row.setTenantId(7L); row.setProjectId(9L); row.setDeliverableCode("D1");
        when(rules.documentTargets(9L, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED")).thenReturn(Set.of("D1"));
        when(deliverables.selectDocuments(any())).thenReturn(List.of(row));
        service.onBusinessResultChange(change);
        verify(rules).documentTargets(9L, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED");
        verify(deliverables).selectDocuments(new AccProjectDeliverableMapper.DocumentScope(7L, 9L, Set.of("D1")));
        verify(submissions).collectBusinessResult(row, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED", change);
        verifyNoMoreInteractions(submissions);
    }
    @Test void revocationsForeignTenantsAndEmptyTargetsNeverTouchSubmissions() {
        service.onBusinessResultChange(change(false));
        verifyNoInteractions(rules, submissions);
        TenantContextHolder.setTenantId(8L);
        assertThrows(IllegalArgumentException.class, () -> service.onBusinessResultChange(change(true)));
        TenantContextHolder.setTenantId(7L);
        when(rules.documentTargets(9L, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED")).thenReturn(Set.of());
        service.onBusinessResultChange(change(true));
        verify(submissions, never()).collectBusinessResult(any(), any(), any());
    }
}
