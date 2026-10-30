package cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto.AcceptanceActivityInitializationCommand;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验收活动初始化（P06R）：deliverable_id 指向平台统一要求实例（projectId+type_code 身份）。 */
class AcceptanceActivityInitializationApiImplTest {

    final AcceptanceActivityMapper activityMapper = mock(AcceptanceActivityMapper.class);
    final PlatformDeliveryRequirementApi platform = mock(PlatformDeliveryRequirementApi.class);

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    static TemplateFrozenView requirement(Long id, Long projectId, String code, String taskCode) {
        return new TemplateFrozenView(id, projectId, code, "验收报告", "S5", taskCode, null, null,
                true, 1, null, "OPEN", "{}", 0);
    }

    @Test
    void freezesTheExplicitCustomTaskAndDeliverablePair() {
        when(platform.lockByIdentity(11L, "CUSTOM_REPORT"))
                .thenReturn(Optional.of(requirement(71L, 11L, "CUSTOM_REPORT", "CUSTOM_ACCEPT")));
        when(activityMapper.insert(any(AcceptanceActivityDO.class))).thenReturn(1);
        var api = new AcceptanceActivityInitializationApiImpl(activityMapper, platform);

        var result = api.initialize(new AcceptanceActivityInitializationCommand(
                7L, 11L, 21L, "CUSTOM_ACCEPT", 31L,
                "PRELIMINARY", "CUSTOM_REPORT", 4));

        assertEquals("INITIALIZED", result.outcome());
        assertEquals(0, result.activityVersion());
        ArgumentCaptor<AcceptanceActivityDO> captor = ArgumentCaptor.forClass(AcceptanceActivityDO.class);
        verify(activityMapper).insert(captor.capture());
        assertEquals(11L, captor.getValue().getProjectId());
        assertEquals(21L, captor.getValue().getProjectTaskId());
        assertEquals(31L, captor.getValue().getExecutionContractId());
        assertEquals(71L, captor.getValue().getDeliverableId());
        assertEquals("PENDING", captor.getValue().getActivityStatus());
    }

    @Test
    void rejectsUndefinedReportTypeBeforeAnyWrite() {
        var api = new AcceptanceActivityInitializationApiImpl(activityMapper, platform);

        var result = api.initialize(new AcceptanceActivityInitializationCommand(
                7L, 11L, 21L, "T-INITIAL-ACCEPT", 31L,
                "UNDEFINED", "D-FINAL-REPORT", 4));

        assertEquals("IDENTITY_MISMATCH", result.outcome());
        verify(activityMapper, never()).insert(any(AcceptanceActivityDO.class));
        verify(platform, never()).lockByIdentity(any(), any());
    }

    @Test
    void foreignOrDifferentlyBoundDeliverableCannotInitializeAnActivity() {
        var api = new AcceptanceActivityInitializationApiImpl(activityMapper, platform);
        var command = new AcceptanceActivityInitializationCommand(7L, 11L, 21L, "CUSTOM", 31L, "FINAL", "REPORT", 4);

        when(platform.lockByIdentity(11L, "REPORT"))
                .thenReturn(Optional.of(requirement(71L, 12L, "REPORT", null)));
        assertEquals("IDENTITY_MISMATCH", api.initialize(command).outcome());

        when(platform.lockByIdentity(11L, "REPORT"))
                .thenReturn(Optional.of(requirement(71L, 11L, "REPORT", "ANOTHER_TASK")));
        assertEquals("IDENTITY_MISMATCH", api.initialize(command).outcome());

        when(platform.lockByIdentity(11L, "REPORT")).thenReturn(Optional.empty());
        assertEquals("IDENTITY_MISMATCH", api.initialize(command).outcome());

        verify(activityMapper, never()).insert(any(AcceptanceActivityDO.class));
    }

    @Test
    void repeatedInitializationMustKeepTheSameDeliverableInstance() {
        var view = requirement(71L, 11L, "REPORT", null);
        when(platform.lockByIdentity(11L, "REPORT")).thenReturn(Optional.of(view));
        var existing = new AcceptanceActivityDO();
        existing.setId(81L);
        existing.setVersion(3L);
        existing.setProjectTaskId(21L);
        existing.setExecutionContractId(31L);
        existing.setDeliverableId(70L);
        when(activityMapper.selectByIdentityForUpdate(any())).thenReturn(existing);
        var api = new AcceptanceActivityInitializationApiImpl(activityMapper, platform);
        var command = new AcceptanceActivityInitializationCommand(7L, 11L, 21L, "CUSTOM", 31L, "FINAL", "REPORT", 4);

        assertEquals("DUPLICATE_OR_PARTIAL", api.initialize(command).outcome());
        existing.setDeliverableId(71L);
        var result = api.initialize(command);
        assertEquals("INITIALIZED", result.outcome());
        assertEquals(81L, result.acceptanceId());
        assertEquals(3L, result.activityVersion());
        verify(activityMapper, never()).insert(any(AcceptanceActivityDO.class));
    }
}
