package cn.iocoder.yudao.module.pms.cutover.service.dashboard;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateRow;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardActionFactPort;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CutoverDashboardQueryServiceTest {
    @Mock private CutoverDashboardCandidateMapper candidateMapper;
    @Mock private CutoverProjectScopePort projectScopePort;

    @Test
    void aggregatesFourOverlappingMetricsWithOneControlledFactBatch() {
        List<CutoverDashboardCandidateRow> rows = List.of(
                row(1L, "NEW_PLATFORM", "P2", "GRADE_CONFIRMING", null),
                row(2L, "NEW_PLATFORM", "P5", "APPROVING", "PENDING"),
                row(3L, "NEW_PLATFORM", "P4", "PLAN_DRAFTING", "REJECTED"),
                row(4L, "NEW_PLATFORM", "P6", "ARCHIVED", "APPROVED"),
                row(5L, "LEGACY_FORWARD", "P4", "PLAN_DRAFTING", "REJECTED"));
        RecordingControlledPort facts = new RecordingControlledPort(List.of(
                new CutoverDashboardActionFacts(1L,
                        ActionFacts.p2p3("DRAFT", true, null, true, true)),
                new CutoverDashboardActionFacts(2L,
                        ActionFacts.p5("PENDING", "PENDING", null, 9L, true)),
                new CutoverDashboardActionFacts(3L,
                        ActionFacts.p4("SUBMITTED", 1, "REJECTED", true, true, true, true))));
        when(projectScopePort.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of(101L));
        when(candidateMapper.selectBatch(any())).thenReturn(rows);

        CutoverDashboardKpiView result = service(facts).inspect(1L, 9L, allPermissions());

        assertThat(result.todoCount()).isEqualTo(3);
        assertThat(result.archivedCount()).isEqualTo(1);
        assertThat(result.approvingCount()).isEqualTo(1);
        assertThat(result.rejectedPendingModificationCount()).isEqualTo(2);
        assertThat(result.generatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 1, 0));
        assertThat(facts.calls).isEqualTo(1);
        assertThat(facts.lastQuery.candidates()).extracting(CutoverDashboardActionFactPort.CandidateNeed::taskId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void returnsAllZeroWithoutTaskOrOwnerFactQueryForEmptyVisibleScope() {
        when(projectScopePort.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of());
        RecordingControlledPort facts = new RecordingControlledPort(List.of());

        CutoverDashboardKpiView result = service(facts).inspect(1L, 9L, allPermissions());

        assertThat(result.todoCount()).isZero();
        assertThat(result.archivedCount()).isZero();
        assertThat(result.approvingCount()).isZero();
        assertThat(result.rejectedPendingModificationCount()).isZero();
        verify(candidateMapper, never()).selectBatch(any());
        assertThat(facts.calls).isZero();
    }

    private CutoverDashboardQueryService service(CutoverDashboardActionFactPort facts) {
        return new CutoverDashboardQueryService(candidateMapper, projectScopePort, facts,
                Clock.fixed(Instant.parse("2026-09-02T01:00:00Z"), ZoneOffset.UTC));
    }

    private static PermissionFacts allPermissions() {
        return new PermissionFacts(true, true, true, true, true, true, true,
                true, true, true, true, true, true, true);
    }

    private static CutoverDashboardCandidateRow row(Long id, String origin, String stage,
                                                     String status, String approvalStatus) {
        CutoverDashboardCandidateRow row = new CutoverDashboardCandidateRow();
        row.setTaskId(id);
        row.setProjectId(101L);
        row.setTaskOrigin(origin);
        row.setCurrentStage(stage);
        row.setTaskStatus(status);
        row.setOwnerUserId(9L);
        row.setManualGrade("A");
        row.setTaskVersion(2);
        row.setStageFactId(id * 100);
        row.setStageFactVersion(3);
        row.setCurrentApprovalStatus(approvalStatus);
        return row;
    }

    private static final class RecordingControlledPort implements CutoverDashboardActionFactPort {
        private final ControlledCutoverDashboardActionFactPort delegate;
        private int calls;
        private BatchQuery lastQuery;

        private RecordingControlledPort(List<CutoverDashboardActionFacts> facts) {
            delegate = new ControlledCutoverDashboardActionFactPort(facts);
        }

        @Override
        public List<CutoverDashboardActionFacts> inspectBatch(BatchQuery query) {
            calls++;
            lastQuery = query;
            return delegate.inspectBatch(query);
        }
    }
}
