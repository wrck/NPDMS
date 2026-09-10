package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalEditableDeleteQuery;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalSaveReqVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArrivalStatusUpdateTest {
    private final ArrivalMapper mapper = mock(ArrivalMapper.class);
    private final ArrivalServiceImpl service = new ArrivalServiceImpl();
    private ArrivalDO row;

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "arrivalMapper", mapper);
        row = new ArrivalDO(); row.setId(1L); row.setStatus(0); row.setVersion(6);
        when(mapper.selectById(1L)).thenReturn(row);
    }

    @Test void signUsesTheLoadedVersionRatherThanIncrementingTheLockPredicate() {
        when(mapper.updateById(any(ArrivalDO.class))).thenAnswer(invocation -> {
            ArrivalDO update = invocation.getArgument(0);
            assertEquals(6, update.getVersion());
            assertEquals(1, update.getStatus());
            return 1;
        });
        service.signArrival(1L);
        verify(mapper).updateById(row);
    }

    @Test void abnormalTransitionAlsoKeepsTheExpectedVersion() {
        when(mapper.updateById(any(ArrivalDO.class))).thenAnswer(invocation -> {
            ArrivalDO update = invocation.getArgument(0);
            assertEquals(6, update.getVersion());
            assertEquals(2, update.getStatus());
            return 1;
        });
        service.markAbnormal(1L);
    }

    @Test void concurrentUpdateIsReportedInsteadOfReturningFalseSuccess() {
        when(mapper.updateById(any(ArrivalDO.class))).thenReturn(0);
        ServiceException error = assertThrows(ServiceException.class, () -> service.signArrival(1L));
        assertEquals(ARRIVAL_VERSION_NOT_MATCH.getCode(), error.getCode());
    }

    @Test void signedRecordCannotBeSignedAgain() {
        row.setStatus(1);
        ServiceException error = assertThrows(ServiceException.class, () -> service.signArrival(1L));
        assertEquals(ARRIVAL_STATUS_INVALID.getCode(), error.getCode());
        verify(mapper, never()).updateById(any(ArrivalDO.class));
    }

    @Test void signedRecordAndEvidenceCannotBeOverwrittenOrDeleted() {
        row.setStatus(1);
        row.setAttachmentUrl("original-receipt");
        ArrivalSaveReqVO request = new ArrivalSaveReqVO(); request.setId(1L); request.setStatus(0); request.setAttachmentUrl("replacement");
        assertEquals(ARRIVAL_STATUS_INVALID.getCode(), assertThrows(ServiceException.class, () -> service.updateArrival(request)).getCode());
        assertEquals(ARRIVAL_STATUS_INVALID.getCode(), assertThrows(ServiceException.class, () -> service.deleteArrival(1L)).getCode());
        assertEquals("original-receipt", row.getAttachmentUrl());
        verify(mapper, never()).updateById(any(ArrivalDO.class));
        verify(mapper, never()).deleteEditable(any());
    }

    @Test void createAlwaysStartsPendingWithoutImportingAStatusOrLockVersion() {
        ArrivalSaveReqVO request = new ArrivalSaveReqVO(); request.setProjectId(7L); request.setCode("ARR-NEW"); request.setStatus(1); request.setVersion(80);
        when(mapper.insert(any(ArrivalDO.class))).thenAnswer(call -> {
            ArrivalDO inserted = call.getArgument(0);
            assertEquals(0, inserted.getStatus()); assertEquals(0, inserted.getVersion());
            inserted.setId(2L); return 1;
        });
        assertEquals(2L, service.createArrival(request));
    }

    @Test void editingPreservesPendingOrAbnormalStatusAndUsesTheLoadedLockVersion() {
        for (int status : new int[]{0, 2}) {
            row.setStatus(status);
            ArrivalSaveReqVO request = new ArrivalSaveReqVO(); request.setId(1L); request.setStatus(1); request.setRemark("updated");
            when(mapper.updateById(any(ArrivalDO.class))).thenAnswer(call -> {
                ArrivalDO update = call.getArgument(0);
                assertEquals(status, update.getStatus()); assertEquals(6, update.getVersion());
                assertEquals("updated", update.getRemark()); return 1;
            });
            service.updateArrival(request);
        }
    }

    @Test void staleEditAndConcurrentEditBothFailWithoutReportingSuccess() {
        ArrivalSaveReqVO request = new ArrivalSaveReqVO(); request.setId(1L); request.setVersion(5);
        assertEquals(ARRIVAL_VERSION_NOT_MATCH.getCode(), assertThrows(ServiceException.class, () -> service.updateArrival(request)).getCode());
        verify(mapper, never()).updateById(any(ArrivalDO.class));
        request.setVersion(6);
        when(mapper.updateById(any(ArrivalDO.class))).thenReturn(0);
        assertEquals(ARRIVAL_VERSION_NOT_MATCH.getCode(), assertThrows(ServiceException.class, () -> service.updateArrival(request)).getCode());
    }

    @Test void deleteUsesAnEditableVersionPredicateAndReportsAConcurrentChange() {
        ArrivalEditableDeleteQuery query = new ArrivalEditableDeleteQuery(1L, 6);
        when(mapper.deleteEditable(query)).thenReturn(0);
        assertEquals(ARRIVAL_VERSION_NOT_MATCH.getCode(), assertThrows(ServiceException.class, () -> service.deleteArrival(1L)).getCode());
        verify(mapper, never()).deleteById(anyLong());
        when(mapper.deleteEditable(query)).thenReturn(1);
        service.deleteArrival(1L);
    }
}
