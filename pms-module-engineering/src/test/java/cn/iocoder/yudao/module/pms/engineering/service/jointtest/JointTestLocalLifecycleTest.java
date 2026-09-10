package cn.iocoder.yudao.module.pms.engineering.service.jointtest;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JointTestLocalLifecycleTest {
    private final JointTestMapper mapper = mock(JointTestMapper.class);
    private final JointTestServiceImpl service = new JointTestServiceImpl();
    private JointTestDO row;
    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "jointTestMapper", mapper);
        row = new JointTestDO(); row.setId(1L); row.setProjectId(7L); row.setCode("JT-TEST"); row.setStatus(1); row.setVersion(4);
        when(mapper.selectById(1L)).thenReturn(row);
    }

    @Test void failureRecordsItsReasonWithTheLoadedOptimisticVersion() {
        when(mapper.updateById(any(JointTestDO.class))).thenAnswer(call -> {
            JointTestDO update = call.getArgument(0);
            assertEquals(3, update.getStatus());
            assertEquals(4, update.getVersion());
            assertEquals("connection check failed", update.getExceptionRecord());
            return 1;
        });
        service.fail(1L, " connection check failed ");
    }

    @Test void missingFailureReasonDoesNotWriteAResult() {
        assertThrows(ServiceException.class, () -> service.fail(1L, "   "));
        verify(mapper, never()).updateById(any(JointTestDO.class));
    }

    @Test void lostPassUpdateDoesNotReportSuccess() {
        when(mapper.updateById(any(JointTestDO.class))).thenReturn(0);
        assertEquals(JOINT_TEST_VERSION_NOT_MATCH.getCode(), assertThrows(ServiceException.class, () -> service.pass(1L)).getCode());
    }

    @Test void failedResultCannotBePassedOrDeleted() {
        row.setStatus(3);
        assertEquals(JOINT_TEST_STATUS_INVALID.getCode(), assertThrows(ServiceException.class, () -> service.pass(1L)).getCode());
        assertEquals(JOINT_TEST_STATUS_INVALID.getCode(), assertThrows(ServiceException.class, () -> service.deleteJointTest(1L)).getCode());
        verify(mapper, never()).updateById(any(JointTestDO.class));
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test void editingPreservesTheWorkflowStateAndRejectsAStaleVersion() {
        JointTestSaveReqVO request = new JointTestSaveReqVO();
        request.setId(1L); request.setCode("JT-TEST"); request.setStatus(2); request.setVersion(3);
        assertEquals(JOINT_TEST_VERSION_NOT_MATCH.getCode(), assertThrows(ServiceException.class, () -> service.updateJointTest(request)).getCode());
        request.setVersion(4);
        when(mapper.updateById(any(JointTestDO.class))).thenAnswer(call -> {
            JointTestDO update = call.getArgument(0);
            assertEquals(1, update.getStatus());
            assertEquals(4, update.getVersion());
            return 1;
        });
        service.updateJointTest(request);
    }
}
