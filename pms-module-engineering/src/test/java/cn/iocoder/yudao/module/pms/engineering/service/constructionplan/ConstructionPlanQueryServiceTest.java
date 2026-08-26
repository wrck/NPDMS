package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanChangePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRevisionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangePageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionListQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConstructionPlanQueryServiceTest {

    @Mock ConstructionPlanMapper planMapper;
    @Mock ConstructionPlanRevisionMapper revisionMapper;
    @Mock ConstructionPlanChangeMapper changeMapper;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;
    private ConstructionPlanQueryService service;

    @BeforeEach
    void setUp() {
        service = new ConstructionPlanQueryService(planMapper, revisionMapper, changeMapper,
                permissionApi, projectScopeApi, participantFactApi);
    }

    @Test
    void projectLookupReturnsEmptyBusinessResultInsideViewScope() {
        stubViewScope();
        when(planMapper.selectByProjectId(0L, 100L)).thenReturn(null);

        assertNull(service.getByProjectId(100L, actor()));
    }

    @Test
    void detailReturnsCurrentRevisionAndServerActions() {
        stubViewScope();
        ConstructionPlanDO plan = plan();
        when(planMapper.selectById(any())).thenReturn(plan);
        when(revisionMapper.selectById(any())).thenReturn(revision(701L, 1));
        when(permissionApi.hasAnyPermissions(9L,
                ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(false);

        var response = service.getById(501L, actor());

        assertEquals(701L, response.getCurrentRevision().getRevisionId());
        assertTrue(response.getCurrentRevision().getCurrent());
        assertEquals("PENDING_RECALCULATION", response.getPlanRecalculationStatus());
        assertTrue(response.getAllowedActions().isEmpty());
    }

    @Test
    void detailReturnsPendingChangeCandidateRevision() {
        stubViewScope();
        ConstructionPlanDO plan = plan();
        plan.setPendingChangeId(801L);
        when(planMapper.selectById(any())).thenReturn(plan);
        when(revisionMapper.selectById(any())).thenReturn(
                revision(701L, 1), revision(702L, 2));
        when(changeMapper.selectById(any())).thenReturn(change(801L,
                LocalDateTime.of(2026, 8, 26, 13, 0)));

        var response = service.getById(501L, actor());

        assertEquals(801L, response.getPendingChangeSummary().getChangeId());
        assertEquals(702L, response.getPendingChangeSummary().getCandidateRevision().getRevisionId());
        assertEquals("customer-delay", response.getPendingChangeSummary().getCustomerEvidenceReferenceKey());
    }

    @Test
    void revisionPageUsesStableCursorAndOneExtraRow() {
        stubViewScope();
        when(planMapper.selectById(any())).thenReturn(plan());
        when(revisionMapper.selectPage(new ConstructionPlanRevisionPageQuery(
                0L, 501L, 3, 703L, 3)))
                .thenReturn(List.of(revision(702L, 2), revision(701L, 1), revision(700L, 0)));
        ConstructionPlanRevisionPageReqVO request = new ConstructionPlanRevisionPageReqVO();
        request.setCursor("3:703");
        request.setPageSize(2);

        var page = service.getRevisions(501L, request, actor());

        assertEquals(2, page.getItems().size());
        assertTrue(page.getHasMore());
        assertEquals("1:701", page.getNextCursor());
        assertFalse(page.getItems().get(0).getCurrent());
        assertTrue(page.getItems().get(1).getCurrent());
    }

    @Test
    void changePageUsesStableTimestampAndIdCursor() {
        stubViewScope();
        when(planMapper.selectById(any())).thenReturn(plan());
        LocalDateTime cursorTime = LocalDateTime.of(2026, 8, 26, 13, 0);
        when(changeMapper.selectPage(new ConstructionPlanChangePageQuery(
                0L, 501L, cursorTime, 703L, 3)))
                .thenReturn(List.of(change(702L, cursorTime.minusMinutes(1)),
                        change(701L, cursorTime.minusMinutes(2)),
                        change(700L, cursorTime.minusMinutes(3))));
        when(revisionMapper.selectListByIds(new ConstructionPlanRevisionListQuery(
                0L, 501L, Set.of(702L)))).thenReturn(List.of(revision(702L, 2)));
        ConstructionPlanChangePageReqVO request = new ConstructionPlanChangePageReqVO();
        request.setCursor(cursorTime + "|703");
        request.setPageSize(2);

        var page = service.getChanges(501L, request, actor());

        assertEquals(2, page.getItems().size());
        assertTrue(page.getHasMore());
        assertEquals(cursorTime.minusMinutes(2) + "|701", page.getNextCursor());
        assertEquals(702L, page.getItems().get(0).getCandidateRevision().getRevisionId());
    }

    @Test
    void changeDetailIncludesItsCandidateRevision() {
        stubViewScope();
        when(planMapper.selectById(any())).thenReturn(plan());
        when(changeMapper.selectById(any())).thenReturn(change(801L,
                LocalDateTime.of(2026, 8, 26, 13, 0)));
        when(revisionMapper.selectById(any())).thenReturn(revision(702L, 2));

        var response = service.getChange(501L, 801L, actor());

        assertEquals(801L, response.getChangeId());
        assertEquals(702L, response.getCandidateRevision().getRevisionId());
        assertEquals("customer-delay", response.getCustomerEvidenceReferenceKey());
        assertFalse(response.getCandidateRevision().getCurrent());
    }

    private void stubViewScope() {
        when(permissionApi.hasAnyPermissions(9L,
                ConstructionPlanQueryService.PERMISSION_QUERY,
                ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                0L, 9L, 100L, ProjectScopeApi.ACTION_VIEW)))
                .thenReturn(new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of()));
    }

    private ConstructionPlanDO plan() {
        ConstructionPlanDO plan = new ConstructionPlanDO();
        plan.setId(501L);
        plan.setProjectId(100L);
        plan.setCurrentDurationRevisionId(701L);
        plan.setPlanRecalculationStatusCode("PENDING_RECALCULATION");
        plan.setPlanRecalculationSourceRevisionId(701L);
        plan.setVersion(1);
        plan.setTenantId(0L);
        return plan;
    }

    private ConstructionPlanRevisionDO revision(Long id, Integer revisionNo) {
        ConstructionPlanRevisionDO row = new ConstructionPlanRevisionDO();
        row.setId(id);
        row.setPlanId(501L);
        row.setRevisionNo(revisionNo);
        row.setCalculationBasisCode("DATE_RANGE");
        row.setStartDate(LocalDate.of(2026, 9, 1));
        row.setEndDate(LocalDate.of(2026, 9, 5));
        row.setDurationDays(5);
        row.setCreatedBy(9L);
        row.setCreatedAt(LocalDateTime.of(2026, 8, 26, 12, revisionNo));
        row.setVersion(0);
        row.setTenantId(0L);
        return row;
    }

    private ConstructionPlanChangeDO change(Long id, LocalDateTime createdAt) {
        ConstructionPlanChangeDO row = new ConstructionPlanChangeDO();
        row.setId(id);
        row.setPlanId(501L);
        row.setBaseRevisionId(701L);
        row.setCandidateRevisionId(702L);
        row.setStatusCode("DRAFT");
        row.setReasonTypeCode("CUSTOMER_DELAY");
        row.setCustomerEvidenceRequired(false);
        row.setCustomerEvidenceReferenceKey("customer-delay");
        row.setApplicantUserId(9L);
        row.setCreatedAt(createdAt);
        row.setVersion(0);
        row.setTenantId(0L);
        return row;
    }

    private ConstructionPlanQueryService.Actor actor() {
        return new ConstructionPlanQueryService.Actor(0L, 9L);
    }
}
