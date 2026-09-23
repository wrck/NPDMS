package cn.iocoder.yudao.module.pms.engineering.service.solution;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable.DeliverableMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SolutionLocalLifecycleTest {
    private final SolutionMapper mapper = mock(SolutionMapper.class);
    private final DeliverableMapper deliverableMapper = mock(DeliverableMapper.class);
    private final EngineeringRecordCodeGenerator recordCodeGenerator = mock(EngineeringRecordCodeGenerator.class);
    private final SolutionServiceImpl service = new SolutionServiceImpl();
    private final cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview.SolutionReviewMapper tiered = mock(cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview.SolutionReviewMapper.class);
    private SolutionDO row;
    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "reviewPolicies", mock(cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionReviewPolicyService.class));
        ReflectionTestUtils.setField(service, "tieredReviewMapper", tiered);
        ReflectionTestUtils.setField(service, "completionEvents", mock(cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents.class));
        ReflectionTestUtils.setField(service, "solutionMapper", mapper);
        ReflectionTestUtils.setField(service, "deliverableMapper", deliverableMapper);
        doReturn("PROJ-FA-001").when(recordCodeGenerator).next(any(), eq(EngineeringRecordCodeGenerator.SOLUTION), any(), any(), any());
        doReturn("PROJ-JF-001").when(recordCodeGenerator).next(any(), eq(EngineeringRecordCodeGenerator.DELIVERABLE), any(), any(), any());
        ReflectionTestUtils.setField(service, "recordCodeGenerator", recordCodeGenerator);
        when(deliverableMapper.selectByProjectAndSource(anyLong(), anyString(), anyLong())).thenReturn(null);
        row = new SolutionDO(); row.setId(1L); row.setProjectId(7L); row.setCode("SOL-TEST"); row.setStatus(0); row.setReviewLevel(0); row.setVersion(6);
        when(mapper.selectById(1L)).thenReturn(row);
    }

    @Test void ordinaryApprovalFreezesTheVersionThatIsActuallyWritten() {
        int[] storedVersion = {6};
        when(mapper.updateById(any(SolutionDO.class))).thenAnswer(call -> {
            SolutionDO update = call.getArgument(0);
            assertEquals(storedVersion[0], update.getVersion());
            update.setVersion(++storedVersion[0]);
            return 1;
        });
        service.submitSolution(1L);
        service.startReview(1L);
        SolutionApproveReqVO approval = new SolutionApproveReqVO(); approval.setId(1L); approval.setVersion(8); approval.setApprovalOpinion("本地审核通过");
        service.approveSolution(approval);
        assertEquals(3, row.getStatus());
        assertEquals(9, row.getVersion());
        assertEquals(9, row.getBaselineVersion());
        assertNotNull(row.getApprovedTime());
        // 批准方案自动归集交付件（4.1→6.4）
        ArgumentCaptor<DeliverableDO> archived = ArgumentCaptor.forClass(DeliverableDO.class);
        verify(deliverableMapper).insert(archived.capture());
        assertEquals(7L, archived.getValue().getProjectId());
        assertEquals("SOLUTION", archived.getValue().getSourceType());
        assertEquals(1L, archived.getValue().getSourceId());
        // 交付件编码改由系统生成器按项目编码生成
        assertEquals("PROJ-JF-001", archived.getValue().getCode());
        assertEquals(1, archived.getValue().getStatus());
    }

    @Test void repeatedApprovalDoesNotDuplicateTheArchivedDeliverable() {
        row.setStatus(2);
        when(mapper.updateById(any(SolutionDO.class))).thenReturn(1);
        when(deliverableMapper.selectByProjectAndSource(7L, "SOLUTION", 1L))
                .thenReturn(new DeliverableDO());
        service.approveSolution(new SolutionApproveReqVO() {{ setId(1L); }});
        verify(deliverableMapper, never()).insert(any(DeliverableDO.class));
    }

    @Test void majorReviewCannotBeSimulatedAsApproved() {
        row.setStatus(2); row.setReviewLevel(1);
        SolutionApproveReqVO approval = new SolutionApproveReqVO(); approval.setId(1L); approval.setVersion(6);
        assertEquals(SOLUTION_REVIEW_NOT_CONNECTED.getCode(), assertThrows(ServiceException.class, () -> service.approveSolution(approval)).getCode());
        assertEquals(2, row.getStatus());
        assertNull(row.getBaselineVersion());
        verify(mapper, never()).updateById(any(SolutionDO.class));
    }

    @Test void configuredProjectCannotUseLegacyOrdinaryCommandsToSkipReview() {
        row.setSolutionType("IMPLEMENTATION"); when(tiered.source(any())).thenReturn(row);
        var policies = mock(cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionReviewPolicyService.class);
        ReflectionTestUtils.setField(service, "reviewPolicies", policies);
        doThrow(new IllegalArgumentException("需要复审")).when(policies).freeze(row, 0);
        assertThrows(IllegalArgumentException.class, () -> service.submitSolution(1L));
        assertEquals(0, row.getStatus());
        row.setStatus(2);
        doThrow(new IllegalArgumentException("缺少判定")).when(policies).requireOrdinaryApproval(row);
        assertThrows(IllegalArgumentException.class, () -> service.approveSolution(new SolutionApproveReqVO() {{ setId(1L); }}));
        assertEquals(2, row.getStatus()); assertNull(row.getBaselineVersion());
        verify(mapper, never()).updateById(any(SolutionDO.class)); verifyNoInteractions(deliverableMapper);
    }

    @Test void originalActionsCannotBypassAnActiveTieredProcess() {
        row.setStatus(2); row.setReviewLevel(1); row.setSolutionType("IMPLEMENTATION");
        when(tiered.source(any())).thenReturn(row);
        var review = new cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview.SolutionReviewDO();
        review.setStatus("RUNNING"); when(tiered.bySolution(any())).thenReturn(review);
        var rejection = new SolutionApproveReqVO(); rejection.setId(1L); rejection.setVersion(6);
        assertThrows(IllegalStateException.class, () -> service.rejectSolution(rejection));
        assertThrows(IllegalStateException.class, () -> service.withdrawSolution(1L));
        assertThrows(IllegalStateException.class, () -> service.terminateSolution(1L));
        verify(mapper, never()).updateById(any(SolutionDO.class));
    }

    @Test void approvedBaselineCannotBeEditedOrDeleted() {
        row.setStatus(3); row.setBaselineVersion(6);
        SolutionSaveReqVO request = new SolutionSaveReqVO(); request.setId(1L);
        assertEquals(SOLUTION_STATUS_INVALID.getCode(), assertThrows(ServiceException.class, () -> service.updateSolution(request)).getCode());
        assertEquals(SOLUTION_STATUS_INVALID.getCode(), assertThrows(ServiceException.class, () -> service.deleteSolution(1L)).getCode());
        verify(mapper, never()).updateById(any(SolutionDO.class));
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test void lostStatusUpdateReturnsAConflict() {
        when(mapper.updateById(any(SolutionDO.class))).thenReturn(0);
        assertEquals(SOLUTION_VERSION_NOT_MATCH.getCode(), assertThrows(ServiceException.class, () -> service.submitSolution(1L)).getCode());
    }

    @Test void newDraftCannotImportApprovalMetadataFromTheCaller() {
        SolutionSaveReqVO request = new SolutionSaveReqVO(); request.setProjectId(7L);
        request.setStatus(3); request.setVersion(50); request.setBaselineVersion(50); request.setApprovedBy(99L); request.setApprovalOpinion("not authoritative");
        when(mapper.insert(any(SolutionDO.class))).thenAnswer(call -> {
            SolutionDO inserted = call.getArgument(0);
            assertEquals(0, inserted.getStatus()); assertEquals(0, inserted.getVersion());
            assertNull(inserted.getBaselineVersion()); assertNull(inserted.getApprovedBy()); assertNull(inserted.getApprovalOpinion());
            inserted.setId(2L); return 1;
        });
        assertEquals(2L, service.createSolution(request));
    }
}
