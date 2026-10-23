package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskDeviceMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskEventMapper;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionTaskShipmentValidationTest {
    @Mock InspectionAccessService access;
    @Mock ProjectDeviceSelectionApi deviceSelection;
    @Mock DeviceQueryApi deviceFacts;
    @Mock InspectionAssetProductTypeApi productTypes;
    @Mock InspectionTaskMapper tasks;
    @Mock InspectionTaskDeviceMapper devices;
    @Mock InspectionTaskEventMapper events;
    @InjectMocks InspectionTaskService service;

    @Test void rejectsUnshippedDeviceBeforeAnyTaskWrite() {
        prepare(10L, null);
        assertEquals("设备尚无发货记录：SN-1", assertThrows(InspectionException.class,
                () -> service.create(command())).getMessage());
        verifyNoInteractions(tasks, productTypes);
    }

    @Test void rejectsContractDeviceCurrentlyAssignedToAnotherProject() {
        prepare(20L, LocalDateTime.of(2026, 10, 1, 0, 0));
        assertEquals("设备不属于当前项目，请重新选择设备", assertThrows(InspectionException.class,
                () -> service.create(command())).getMessage());
        verifyNoInteractions(tasks, productTypes);
    }

    @Test void shippedProjectDeviceContinuesToExistingProductTypeValidation() {
        prepare(10L, LocalDateTime.of(2026, 10, 1, 0, 0));
        var stop = new IllegalStateException("existing type validation");
        when(productTypes.getAuthorizedDeviceProductType(any())).thenThrow(stop);
        assertSame(stop, assertThrows(IllegalStateException.class, () -> service.create(command())));
        verifyNoInteractions(tasks);
    }

    @Test void createsNameFromAuthorizedProjectAndActualGeneratedNumberWithoutClientName() {
        prepare(10L, LocalDateTime.of(2026, 10, 1, 0, 0));
        when(productTypes.getAuthorizedDeviceProductType(any())).thenReturn(List.of(
                new AuthorizedDeviceProductTypeResult(1L, "SWITCH", "交换机", true, "1", "RESOLVED", "SYNCED", null, false)));
        when(tasks.insert(any(InspectionTaskDO.class))).thenAnswer(call -> {
            InspectionTaskDO task = call.getArgument(0); task.setId(100L);
            assertEquals("项目-巡检-" + task.getTaskCode(), task.getTaskName());
            assertTrue(task.getTaskCode().startsWith("INS-"));
            return 1;
        });
        var result = service.create(command());
        assertEquals("项目-巡检-" + result.getTaskCode(), result.getTaskName());
        verify(devices).insert(any(cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDeviceDO.class));
    }

    private void prepare(Long projectId, LocalDateTime shippedAt) {
        when(access.project(any())).thenReturn(ProjectBusinessContextApi.Context.builder()
                .tenantId(7L).projectId(10L).projectName("项目").customerId(30L).customerName("客户").build());
        when(deviceSelection.validateSelection(10L, List.of(1L))).thenReturn(List.of(
                new SelectedProjectDevice(1L, "SN-1", "设备", "CODE", "MODEL", "CONTRACT")));
        when(deviceFacts.getDevice(1L)).thenReturn(new DeviceSummaryDTO(1L, 7L, "SN-1", "CODE", "MODEL",
                "设备", shippedAt, null, "CONTRACT", null, projectId, 1L, 30L, 1L,
                null, null, null, null, null, null, null));
    }

    private InspectionTaskService.Create command() {
        return InspectionTaskService.Create.builder().context(InspectionRequestContext.builder()
                .tenantId(7L).actorId(13L).build()).projectId(10L).deviceIds(List.of(1L))
                .background("例行巡检").mode("ONLINE").build();
    }
}
