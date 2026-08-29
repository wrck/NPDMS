package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArrivalAcceptanceApplicationServiceTest {

    @Test
    void createsDraftWithFrozenProjectDeliveryAndDeviceFacts() {
        ArrivalAcceptanceMapper mapper = mock(ArrivalAcceptanceMapper.class);
        ProjectQualificationPort projectPort = mock(ProjectQualificationPort.class);
        DeliveryScopePort deliveryPort = mock(DeliveryScopePort.class);
        DeviceScopeFactPort devicePort = mock(DeviceScopeFactPort.class);
        when(projectPort.inspect(1L, 100L, 8L)).thenReturn(projectFact());
        when(deliveryPort.inspectAssignedScope(100L)).thenReturn(deliveryScope());
        when(devicePort.resolveBySerials(1L, 100L, Set.of("SN-1"))).thenReturn(deviceScope(100L));
        doAnswer(invocation -> {
            ArrivalAcceptanceDO row = invocation.getArgument(0);
            row.setId(900L);
            return 1;
        }).when(mapper).insert(any(ArrivalAcceptanceDO.class));
        ArrivalAcceptanceApplicationService service = new ArrivalAcceptanceApplicationService(
                mapper, projectPort, deliveryPort, devicePort);

        ArrivalAcceptanceDO created = service.createDraft(command());

        assertEquals(900L, created.getId());
        assertEquals("DRAFT", created.getStatus());
        assertEquals(5, created.getProjectVersion());
        assertEquals(6L, created.getProjectParticipantFactVersion());
        assertEquals(7L, created.getProjectScopeVersion());
        assertEquals(8L, created.getDeliveryScopeVersion());
        assertTrue(created.getExpectedScopeSnapshot().contains("SN-1"));
        assertTrue(created.getExpectedScopeSnapshot().contains("MODEL-1"));
        assertTrue(created.getScopeWatermark().contains("\"11\":9"));
        ArgumentCaptor<ArrivalAcceptanceDO> inserted = ArgumentCaptor.forClass(ArrivalAcceptanceDO.class);
        verify(mapper).insert(inserted.capture());
        assertEquals("8", inserted.getValue().getCreator());
    }

    @Test
    void rejectsForeignDeviceBeforeWritingDraft() {
        ArrivalAcceptanceMapper mapper = mock(ArrivalAcceptanceMapper.class);
        ProjectQualificationPort projectPort = mock(ProjectQualificationPort.class);
        DeliveryScopePort deliveryPort = mock(DeliveryScopePort.class);
        DeviceScopeFactPort devicePort = mock(DeviceScopeFactPort.class);
        when(projectPort.inspect(1L, 100L, 8L)).thenReturn(projectFact());
        when(deliveryPort.inspectAssignedScope(100L)).thenReturn(deliveryScope());
        when(devicePort.resolveBySerials(1L, 100L, Set.of("SN-1"))).thenReturn(deviceScope(200L));
        ArrivalAcceptanceApplicationService service = new ArrivalAcceptanceApplicationService(
                mapper, projectPort, deliveryPort, devicePort);

        assertThrows(IllegalStateException.class, () -> service.createDraft(command()));

        verify(mapper, never()).insert(any(ArrivalAcceptanceDO.class));
    }

    private static ArrivalAcceptanceApplicationService.CreateDraftCommand command() {
        return new ArrivalAcceptanceApplicationService.CreateDraftCommand(
                1L, 100L, 8L, "ARRIVAL-001", "LOGISTICS-001",
                LocalDateTime.of(2026, 8, 30, 9, 0), "客户签收人");
    }

    private static ProjectQualificationPort.ProjectQualificationFact projectFact() {
        return new ProjectQualificationPort.ProjectQualificationFact(
                100L, 7L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "ACTIVE", "S4", 5, 6L, 7L);
    }

    private static DeliveryScopePort.AssignedScope deliveryScope() {
        return new DeliveryScopePort.AssignedScope(100L, 8L, List.of(
                new DeliveryScopePort.AssignedLine(20L, new BigDecimal("1"),
                        "台", "PRODUCT-1", "MODEL-1", Set.of("SN-1"))));
    }

    private static DeviceScopeFactPort.DeviceScopeFact deviceScope(Long currentProjectId) {
        return new DeviceScopeFactPort.DeviceScopeFact(100L, List.of(
                new DeviceScopeFactPort.DeviceFact(11L, "SN-1", currentProjectId, 9L)));
    }
}
