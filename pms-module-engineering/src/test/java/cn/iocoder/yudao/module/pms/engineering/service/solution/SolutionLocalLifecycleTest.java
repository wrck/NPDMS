package cn.iocoder.yudao.module.pms.engineering.service.solution;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SolutionLocalLifecycleTest {
    private final SolutionMapper mapper = mock(SolutionMapper.class);
    private final SolutionServiceImpl service = new SolutionServiceImpl();
    private SolutionDO row;
    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "solutionMapper", mapper);
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
    }

    @Test void majorReviewCannotBeSimulatedAsApproved() {
        row.setStatus(2); row.setReviewLevel(1);
        SolutionApproveReqVO approval = new SolutionApproveReqVO(); approval.setId(1L); approval.setVersion(6);
        assertEquals(SOLUTION_REVIEW_NOT_CONNECTED.getCode(), assertThrows(ServiceException.class, () -> service.approveSolution(approval)).getCode());
        assertEquals(2, row.getStatus());
        assertNull(row.getBaselineVersion());
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
        SolutionSaveReqVO request = new SolutionSaveReqVO(); request.setProjectId(7L); request.setCode("NEW");
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
