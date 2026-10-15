package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionAnnotationDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionFeedbackConfirmationDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionFeedbackQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleRevisionService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectionFeedbackServiceTest {
    private final InspectionAccessService access = mock(InspectionAccessService.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final InspectionFeedbackQueryMapper queries = mock(InspectionFeedbackQueryMapper.class);
    private final InspectionAnnotationMapper annotations = mock(InspectionAnnotationMapper.class);
    private final InspectionFeedbackConfirmationMapper confirmations = mock(InspectionFeedbackConfirmationMapper.class);
    private final InspectionTaskMapper taskMapper = mock(InspectionTaskMapper.class);
    private final InspectionTaskService tasks = mock(InspectionTaskService.class);
    private final InspectionTaskDeviceMapper devices = mock(InspectionTaskDeviceMapper.class);
    private final InspectionRuleFeedbackMapper missing = mock(InspectionRuleFeedbackMapper.class);
    private final InspectionDetectionResultMapper results = mock(InspectionDetectionResultMapper.class);
    private final InspectionRuleQueryService rules = mock(InspectionRuleQueryService.class);
    private final InspectionRuleRevisionService ruleRevisions = mock(InspectionRuleRevisionService.class);
    private final InspectionRuleOptimizationMapper optimizations = mock(InspectionRuleOptimizationMapper.class);
    private final List<InspectionFeedbackConfirmationDO> inserted = new ArrayList<>();
    private InspectionFeedbackService service;
    private InspectionAnnotationDO annotation;
    private InspectionTaskDO task;
    private List<InspectionFeedbackConfirmationDO> snapshotHistory = List.of();
    private List<InspectionFeedbackConfirmationDO> currentHistory = List.of();
    private boolean taskLocked;

    @BeforeEach
    void setup() {
        service = new InspectionFeedbackService(access, permissions, queries, annotations, confirmations,
                taskMapper, tasks, devices, missing, results, rules, ruleRevisions, optimizations);
        annotation = new InspectionAnnotationDO();
        annotation.setId(50L); annotation.setTenantId(1L); annotation.setVersion(1L); annotation.setTaskId(10L);
        annotation.setKind("FALSE_POSITIVE"); annotation.setRevisionNo(3); annotation.setReason("原始误报标注");
        annotation.setAnnotatedBy(8L); annotation.setAnnotatedAt(LocalDateTime.of(2026, 10, 15, 8, 0));
        task = new InspectionTaskDO();
        task.setId(10L); task.setTenantId(1L); task.setVersion(5L); task.setProjectId(100L); task.setStatusCode("CLOSED");
        when(annotations.selectById(50L)).thenAnswer(call -> annotation);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(taskMapper.selectForUpdate(any(InspectionTaskRows.class))).thenAnswer(call -> {
            taskLocked = true; return task;
        });
        when(confirmations.selectForTask(any(InspectionTaskRows.class))).thenAnswer(call -> snapshotHistory);
        // A concurrent revision becomes visible after the task lock is acquired, never through the RR snapshot.
        when(confirmations.selectForTaskForUpdate(any(InspectionTaskRows.class)))
                .thenAnswer(call -> taskLocked ? currentHistory : snapshotHistory);
        when(confirmations.insert(any(InspectionFeedbackConfirmationDO.class))).thenAnswer(call -> {
            InspectionFeedbackConfirmationDO row = call.getArgument(0);
            row.setId(1000L + inserted.size()); inserted.add(row); return 1;
        });
        when(access.visibleProjects(any())).thenReturn(Set.of(100L));
        when(queries.statistics(any(InspectionFeedbackQuery.class))).thenReturn(List.of());
    }

    @Test
    void analysisPermissionAloneCannotReplaceConfirmationPermission() {
        permissionDenied("pms:inspection-feedback:confirm");
    }

    @Test
    void confirmationPermissionAloneCannotReplaceAnalysisPermission() {
        permissionDenied("pms:inspection-feedback:analyze");
    }

    @Test
    void annotationFromAnotherTenantCannotBeConfirmed() {
        annotation.setTenantId(2L);

        assertThrows(InspectionException.class, () -> service.confirm(confirm(0L)));

        assertTrue(inserted.isEmpty());
        verify(taskMapper, never()).selectForUpdate(any());
        assertHistoryNotUpdated();
    }

    @Test
    void annotationCannotUseASourceTaskFromAnotherTenant() {
        task.setTenantId(2L);

        assertThrows(InspectionException.class, () -> service.confirm(confirm(0L)));

        assertTrue(inserted.isEmpty());
        verify(taskMapper, never()).selectForUpdate(any());
        assertHistoryNotUpdated();
    }

    @Test
    void ignoredAndTrackedAnnotationsCannotBecomeFalsePositiveConfirmations() {
        for (String kind : List.of("IGNORE", "TRACK")) {
            annotation.setKind(kind);
            assertThrows(InspectionException.class, () -> service.confirm(confirm(0L)), kind);
        }

        assertTrue(inserted.isEmpty());
        verify(taskMapper, never()).selectForUpdate(any());
        assertHistoryNotUpdated();
    }

    @Test
    void revisionCommittedWhileWaitingForTaskLockRejectsTheOldIfMatchWithoutInserting() {
        var previous = confirmation(70L, 50L, 1);
        var concurrent = confirmation(71L, 50L, 2);
        snapshotHistory = List.of(previous);
        currentHistory = List.of(previous, concurrent);

        var error = assertThrows(InspectionException.class, () -> service.confirm(confirm(1L)));

        assertEquals(InspectionException.Reason.VERSION_CONFLICT, error.getReason());
        assertEquals(2L, error.getCurrentVersion());
        assertTrue(inserted.isEmpty());
        assertEquals(1, previous.getRevisionNo());
        assertEquals(2, concurrent.getRevisionNo());
        assertHistoryNotUpdated();
    }

    @Test
    void noHistoryForThisAnnotationAcceptsIfMatchZeroAndAppendsRevisionOne() {
        // A different annotation's history must not change this annotation's initial confirmation version.
        currentHistory = List.of(confirmation(70L, 51L, 99));

        var response = service.confirm(confirm(0L));

        assertEquals(1, response.version());
        assertEquals(50L, response.annotationId());
        assertEquals(1000L, response.confirmationId());
        assertTrue(response.valid());
        assertEquals("核对来源确认误报", response.reason());
        assertEquals(1, inserted.size());
        var row = inserted.getFirst();
        assertEquals(1L, row.getTenantId());
        assertEquals(10L, row.getTaskId());
        assertEquals(2L, row.getConfirmedBy());
        assertNotNull(row.getConfirmedAt());
        assertHistoryNotUpdated();
    }

    @Test
    void revisedConfirmationAppendsANewVersionAndPreservesThePreviousDecision() {
        var previous = confirmation(70L, 50L, 3);
        var originalAt = previous.getConfirmedAt();
        snapshotHistory = List.of(previous); currentHistory = snapshotHistory;

        var response = service.confirm(new InspectionFeedbackService.Confirm(context(3L), 50L, false, "新证据表明原确认无效"));

        assertEquals(4, response.version());
        assertFalse(response.valid());
        assertEquals(1, inserted.size());
        assertEquals(4, inserted.getFirst().getRevisionNo());
        assertEquals("新证据表明原确认无效", inserted.getFirst().getReason());
        assertNotSame(previous, inserted.getFirst());
        assertEquals(3, previous.getRevisionNo());
        assertTrue(previous.getValidFlag());
        assertEquals("已有确认依据", previous.getReason());
        assertEquals(originalAt, previous.getConfirmedAt());
        assertHistoryNotUpdated();
    }

    @Test
    void archivedSourceAllowsIndependentConfirmationWithoutChangingTaskOrAnnotationHistory() {
        task.setStatusCode("ARCHIVED");
        var originalAnnotatedAt = annotation.getAnnotatedAt();

        var response = service.confirm(confirm(0L));

        assertEquals(1, response.version());
        assertEquals(1, inserted.size());
        assertEquals("ARCHIVED", task.getStatusCode());
        assertEquals(5L, task.getVersion());
        assertEquals("FALSE_POSITIVE", annotation.getKind());
        assertEquals(3, annotation.getRevisionNo());
        assertEquals("原始误报标注", annotation.getReason());
        assertEquals(originalAnnotatedAt, annotation.getAnnotatedAt());
        assertHistoryNotUpdated();
        verifyNoInteractions(tasks);
    }

    @Test
    void analysisPermissionDoesNotAllowFilteringProductModelWithoutDetailPermission() {
        var filter = new InspectionFeedbackService.PageQuery();
        filter.setProductModel("exact-model-filter");
        for (boolean analyze : List.of(false, true)) {
            when(permissions.hasAnyPermissions(2L, "pms:inspection-feedback:analyze")).thenReturn(analyze);

            assertThrows(InspectionException.class,
                    () -> service.page(new InspectionFeedbackService.ScopeQuery(context(0L), filter)));
        }

        verifyNoInteractions(queries);
    }

    @Test
    void detailPermissionAllowsProductFilteringWithoutLiftingTheActorRestriction() {
        when(permissions.hasAnyPermissions(2L, "pms:inspection-feedback:details")).thenReturn(true);
        var filter = new InspectionFeedbackService.PageQuery();
        filter.setProductModel("exact-model-filter");

        var query = service.scope(new InspectionFeedbackService.ScopeQuery(context(0L), filter));

        assertTrue(query.isDetails());
        assertFalse(query.isAdministrator());
        assertEquals(2L, query.getActorId());
        assertEquals(1L, query.getTenantId());
        assertEquals(Set.of(100L), query.getVisibleProjectIds());
        assertEquals("exact-model-filter", query.getProductModel());
    }

    @Test
    void ordinaryQueryPreservesActorAndEmptyProjectScopeAndDoesNotReadAdministratorStatistics() {
        when(access.visibleProjects(any())).thenReturn(Set.of());

        var page = service.page(new InspectionFeedbackService.ScopeQuery(context(0L), new InspectionFeedbackService.PageQuery()));

        var query = ArgumentCaptor.forClass(InspectionFeedbackQuery.class);
        verify(queries).count(query.capture());
        assertEquals(Set.of(), query.getValue().getVisibleProjectIds());
        assertEquals(2L, query.getValue().getActorId());
        assertEquals(1L, query.getValue().getTenantId());
        assertFalse(query.getValue().isAdministrator());
        assertFalse(query.getValue().isDetails());
        assertFalse(page.administrator());
        assertTrue(page.page().getList().isEmpty());
        assertTrue(page.statistics().isEmpty());
        verify(queries, never()).selectPage(any());
        verify(queries, never()).statistics(any());
    }

    @Test
    void analysisQueryStillPassesAnEmptyProjectScopeToBothRowsAndStatistics() {
        when(access.visibleProjects(any())).thenReturn(Set.of());
        when(permissions.hasAnyPermissions(2L, "pms:inspection-feedback:analyze")).thenReturn(true);

        var page = service.page(new InspectionFeedbackService.ScopeQuery(context(0L), new InspectionFeedbackService.PageQuery()));

        var countScope = ArgumentCaptor.forClass(InspectionFeedbackQuery.class);
        var statisticsScope = ArgumentCaptor.forClass(InspectionFeedbackQuery.class);
        verify(queries).count(countScope.capture());
        verify(queries).statistics(statisticsScope.capture());
        assertTrue(page.administrator());
        assertEquals(Set.of(), countScope.getValue().getVisibleProjectIds());
        assertEquals(Set.of(), statisticsScope.getValue().getVisibleProjectIds());
        assertEquals(1L, statisticsScope.getValue().getTenantId());
        assertEquals(2L, statisticsScope.getValue().getActorId());
        assertTrue(page.page().getList().isEmpty());
    }

    private void permissionDenied(String permission) {
        var command = confirm(0L);
        var rejection = new InspectionException(InspectionException.Reason.FORBIDDEN, "缺少独立业务权限");
        doThrow(rejection).when(access).permission(command.context(), permission);

        assertSame(rejection, assertThrows(InspectionException.class, () -> service.confirm(command)));

        assertTrue(inserted.isEmpty());
        verify(annotations, never()).selectById(anyLong());
        assertHistoryNotUpdated();
    }

    private static InspectionFeedbackConfirmationDO confirmation(long id, long annotationId, int revision) {
        var row = new InspectionFeedbackConfirmationDO();
        row.setId(id); row.setTenantId(1L); row.setVersion(1L); row.setTaskId(10L);
        row.setAnnotationId(annotationId); row.setRevisionNo(revision); row.setValidFlag(true);
        row.setReason("已有确认依据"); row.setConfirmedBy(8L); row.setConfirmedAt(LocalDateTime.of(2026, 10, 15, 8, 30));
        return row;
    }

    private static InspectionRequestContext context(Long version) {
        return new InspectionRequestContext(1L, 2L, "feedback-confirm", "test", version);
    }

    private static InspectionFeedbackService.Confirm confirm(Long version) {
        return new InspectionFeedbackService.Confirm(context(version), 50L, true, "  核对来源确认误报  ");
    }

    private void assertHistoryNotUpdated() {
        verify(confirmations, never()).updateById(any(InspectionFeedbackConfirmationDO.class));
        verify(taskMapper, never()).updateById(any(InspectionTaskDO.class));
        verify(annotations, never()).updateById(any(InspectionAnnotationDO.class));
    }
}
