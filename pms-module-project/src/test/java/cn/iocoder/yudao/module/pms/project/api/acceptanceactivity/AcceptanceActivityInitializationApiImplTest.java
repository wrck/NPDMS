package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity;

import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityInitializationCommand;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptanceActivityInitializationApiImplTest {

    @Mock AcceptanceActivityMapper activityMapper;
    @Mock AccProjectDeliverableMapper deliverableMapper;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void initializesOnlyTheExactPreliminaryTaskAndDeliverablePair() {
        AccProjectDeliverableDO deliverable = new AccProjectDeliverableDO();
        deliverable.setId(71L);
        deliverable.setProjectId(11L);
        deliverable.setDeliverableCode("D-INITIAL-REPORT");
        when(deliverableMapper.selectByProjectAndCodeForUpdate(any())).thenReturn(deliverable);
        when(activityMapper.insert(any(AcceptanceActivityDO.class))).thenReturn(1);
        var api = new AcceptanceActivityInitializationApiImpl(activityMapper, deliverableMapper);

        var result = api.initialize(new AcceptanceActivityInitializationCommand(
                7L, 11L, 21L, "T-INITIAL-ACCEPT", 31L,
                "PRELIMINARY", "D-INITIAL-REPORT", 4));

        assertEquals("INITIALIZED", result.outcome());
        assertEquals(0, result.activityVersion());
        ArgumentCaptor<AcceptanceActivityDO> captor = ArgumentCaptor.forClass(AcceptanceActivityDO.class);
        verify(activityMapper).insert(captor.capture());
        assertEquals(11L, captor.getValue().getProjectId());
        assertEquals(21L, captor.getValue().getProjectTaskId());
        assertEquals(31L, captor.getValue().getExecutionContractId());
        assertEquals("PENDING", captor.getValue().getActivityStatus());
    }

    @Test
    void rejectsCrossedTaskAndReportTypeBeforeAnyWrite() {
        var api = new AcceptanceActivityInitializationApiImpl(activityMapper, deliverableMapper);

        var result = api.initialize(new AcceptanceActivityInitializationCommand(
                7L, 11L, 21L, "T-INITIAL-ACCEPT", 31L,
                "FINAL", "D-FINAL-REPORT", 4));

        assertEquals("IDENTITY_MISMATCH", result.outcome());
        verify(activityMapper, never()).insert(any(AcceptanceActivityDO.class));
        verify(deliverableMapper, never()).selectByProjectAndCodeForUpdate(any());
    }
}
