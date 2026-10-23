package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.acceptance.vo.AcceptanceSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.AcceptanceOwnerLockQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyAcceptanceServiceSafetyTest {
    @Mock AcceptanceMapper acceptanceMapper;
    @Mock LegacyAcceptanceAttachmentRegistration attachments;
    @Mock AcceptanceRecordCodeGenerator recordCodeGenerator;
    @Mock NativeAcceptanceProjectAccess projectAccess;
    @Mock DeliverableChecklistMapper deliverableChecklistMapper;
    @InjectMocks AcceptanceServiceImpl service;

    @BeforeEach void setTenant() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void createCannotForgeACompletedOrSubmittedState(int status) {
        var request = request();
        request.setStatus(status);
        assertThrows(ServiceException.class, () -> service.createAcceptance(request));
        verify(acceptanceMapper, never()).insert(any(AcceptanceDO.class));
    }

    @Test
    void draftUpdateReportsAnOptimisticLockConflict() {
        when(acceptanceMapper.selectOwnerForUpdate(new AcceptanceOwnerLockQuery(7L, 8L))).thenReturn(draft());
        var request = request(); request.setId(8L); request.setVersion(1);
        assertThrows(ServiceException.class, () -> service.updateAcceptance(request));
    }

    @Test
    void submitUsesTheObservedVersionAndRejectsLostUpdate() {
        when(acceptanceMapper.selectOwnerForUpdate(new AcceptanceOwnerLockQuery(7L, 8L))).thenReturn(draft());
        assertThrows(ServiceException.class, () -> service.submitAcceptance(8L));
        verify(acceptanceMapper).updateById(argThat((AcceptanceDO row) ->
                Long.valueOf(2L).equals(row.getVersion()) && Integer.valueOf(1).equals(row.getStatus())));
    }

    @Test void movingTheAcceptanceCannotLeaveCrossProjectChecklistReferences() {
        when(acceptanceMapper.selectOwnerForUpdate(any())).thenReturn(draft());
        var checklist = new DeliverableChecklistDO(); checklist.setProjectId(9L);
        when(deliverableChecklistMapper.selectByAcceptance(any())).thenReturn(java.util.List.of(checklist));
        var request = request(); request.setId(8L); request.setVersion(2); request.setProjectId(10L);
        assertThrows(ServiceException.class, () -> service.updateAcceptance(request));
        verify(acceptanceMapper, never()).updateById(any(AcceptanceDO.class));
    }

    private static AcceptanceSaveReqVO request() {
        var request = new AcceptanceSaveReqVO();
        request.setProjectId(9L); request.setName("历史验收安全回归");
        request.setAcceptanceType("PRELIMINARY");
        return request;
    }

    private static AcceptanceDO draft() {
        var row = new AcceptanceDO(); row.setId(8L); row.setTenantId(7L);
        row.setProjectId(9L); row.setStatus(0); row.setVersion(2L);
        return row;
    }
}
