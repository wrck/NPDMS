package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskDeviceMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskEventMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionRuleSelectionMapper;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleRevisionService;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
    @Mock InspectionRuleSelectionMapper selections;
    @Mock InspectionRuleQueryService ruleQueries;
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

    @Test void authorizedProductTypeLookupFailureDoesNotAllowPartialTaskWrite() {
        prepare(10L, LocalDateTime.of(2026, 10, 1, 0, 0));
        var stop = new IllegalStateException("existing type validation");
        when(productTypes.getAuthorizedDeviceProductType(any())).thenThrow(stop);
        assertSame(stop, assertThrows(IllegalStateException.class, () -> service.create(command())));
        verifyNoInteractions(tasks);
        verify(deviceFacts).getVisibleDeviceSummaries(new DeviceSummaryBatchQuery(7L, 13L, Set.of(1L), 10L));
        verify(deviceFacts, never()).getDevice(any());
    }

    @Test void missingPrefetchedDeviceRetainsOriginalOwnerException() {
        prepare(10L, LocalDateTime.of(2026, 10, 1, 0, 0));
        when(deviceFacts.getVisibleDeviceSummaries(any())).thenReturn(List.of());
        var ownerFailure = new IllegalStateException("AST_EQUIPMENT_NOT_EXISTS");
        when(deviceFacts.getDevice(1L)).thenThrow(ownerFailure);
        assertSame(ownerFailure, assertThrows(IllegalStateException.class, () -> service.create(command())));
        var order = inOrder(deviceSelection, deviceFacts);
        order.verify(deviceSelection).validateSelection(10L, List.of(1L));
        order.verify(deviceFacts).getVisibleDeviceSummaries(any());
        order.verify(deviceFacts).getDevice(1L);
        verifyNoInteractions(tasks, productTypes);
    }

    @Test void firstSelectedUnshippedDeviceFailsBeforeLaterMissingDeviceLookup() {
        prepare(10L, null);
        var input = InspectionTaskService.Create.builder().context(command().context()).projectId(10L)
                .deviceIds(List.of(1L, 2L)).background("例行巡检").mode("ONLINE").build();
        when(deviceSelection.validateSelection(10L, List.of(1L, 2L))).thenReturn(List.of(
                new SelectedProjectDevice(1L, "SN-1", "设备", "CODE", "MODEL", "CONTRACT"),
                new SelectedProjectDevice(2L, "SN-2", "设备", "CODE", "MODEL", "CONTRACT")));
        assertEquals("设备尚无发货记录：SN-1", assertThrows(InspectionException.class,
                () -> service.create(input)).getMessage());
        verify(deviceFacts, never()).getDevice(any());
        verifyNoInteractions(tasks, productTypes);
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

    @Test void createsWithoutCustomerUsingUnassociatedSentinelAndBlankName() {
        var task = createWithCustomer(null, null, null, null);
        assertEquals(0L, task.getCustomerId());
        assertNull(task.getCustomerCode());
        assertEquals("", task.getCustomerName());
        assertEquals("PREPARING", task.getStatusCode());
    }

    @Test void manualNameIsOnlyATaskSnapshotAndDoesNotCreateCustomerIdentity() {
        var task = createWithCustomer(null, null, null, "  现场客户名称  ");
        assertEquals(0L, task.getCustomerId());
        assertNull(task.getCustomerCode());
        assertEquals("现场客户名称", task.getCustomerName());
    }

    @Test void projectCustomerNameRemainsAuthoritativeEvenWithoutLocalId() {
        var task = createWithCustomer(null, "CUS-EXTERNAL", "权威项目客户", "客户端另一客户");
        assertEquals(0L, task.getCustomerId());
        assertEquals("CUS-EXTERNAL", task.getCustomerCode());
        assertEquals("权威项目客户", task.getCustomerName());
    }

    @Test void missingNameMayBeFilledWithoutChangingExistingProjectIdentityOrCode() {
        var task = createWithCustomer(30L, "CUS30", "  ", "手填展示名称");
        assertEquals(30L, task.getCustomerId());
        assertEquals("CUS30", task.getCustomerCode());
        assertEquals("手填展示名称", task.getCustomerName());
    }

    @Test void realCustomerIdentityAndNameCannotBeOverriddenByClient() {
        var task = createWithCustomer(30L, "CUS30", "权威客户", "伪造客户");
        assertEquals(30L, task.getCustomerId());
        assertEquals("CUS30", task.getCustomerCode());
        assertEquals("权威客户", task.getCustomerName());
    }

    @Test void missingProductTypeDoesNotBlockCreationOrInventAType() {
        var row = createWithTypes(List.of());
        assertEquals("", row.getProductTypeCode());
        assertEquals("", row.getProductTypeName());
    }

    @Test void unresolvedTypeProjectionDoesNotBlockCreationOrInventAType() {
        var row = createWithTypes(List.of(new AuthorizedDeviceProductTypeResult(1L, null, null, false,
                null, "UNRESOLVED", "NOT_AVAILABLE", null, false)));
        assertEquals("", row.getProductTypeCode());
        assertEquals("", row.getProductTypeName());
    }

    @Test void disabledKnownTypeRemainsARealSnapshotWithoutGatingTaskCreation() {
        var row = createWithTypes(List.of(new AuthorizedDeviceProductTypeResult(1L, "KNOWN", "已知类型", false,
                "1", "RESOLVED", "FRESH", null, false)));
        assertEquals("KNOWN", row.getProductTypeCode());
        assertEquals("已知类型", row.getProductTypeName());
    }

    @Test void missingTypeDoesNotInventApplicabilityForPlatformRules() {
        var task = new InspectionTaskDO(); task.setId(100L); task.setTenantId(7L); task.setProjectId(10L); task.setStatusCode("PREPARING");
        when(access.task(any())).thenReturn(task);
        var device = new cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDeviceDO();
        device.setDeviceId(1L); device.setProductTypeCode(""); device.setProductTypeName("");
        when(devices.selectForTask(any())).thenReturn(List.of(device));
        when(productTypes.getAuthorizedDeviceProductType(any())).thenReturn(List.of());
        var rule = new InspectionRuleQueryService.RuleView(); rule.setRevisionId(50L); rule.setStatusCode("PUBLISHED");
        rule.setProductTypes(List.of(new InspectionRuleRevisionService.ProductTypeDraft("SWITCH", "交换机")));
        when(ruleQueries.lockPublished(any())).thenReturn(List.of(rule));
        assertEquals("设备当前产品类型不可用，请先完善设备资料", assertThrows(InspectionException.class,
                () -> service.savePreparation(InspectionTaskService.Preparation.builder().context(command().context()).taskId(100L)
                        .taskName("巡检").background("例行巡检").mode("ONLINE")
                        .rules(List.of(new InspectionTaskService.RuleChoice(1L,50L))).build())).getMessage());
        verifyNoInteractions(tasks, selections);
    }

    private cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDeviceDO createWithTypes(
            List<AuthorizedDeviceProductTypeResult> types) {
        prepare(10L, LocalDateTime.of(2026, 10, 1, 0, 0));
        when(productTypes.getAuthorizedDeviceProductType(any())).thenReturn(types);
        when(tasks.insert(any(InspectionTaskDO.class))).thenAnswer(call -> {
            InspectionTaskDO task = call.getArgument(0); task.setId(100L); return 1;
        });
        service.create(command());
        var saved = org.mockito.ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDeviceDO.class);
        verify(devices).insert(saved.capture());
        return saved.getValue();
    }

    private InspectionTaskDO createWithCustomer(Long customerId, String customerCode, String customerName, String manualName) {
        prepare(10L, LocalDateTime.of(2026, 10, 1, 0, 0));
        when(access.project(any())).thenReturn(ProjectBusinessContextApi.Context.builder()
                .tenantId(7L).projectId(10L).projectName("项目").customerId(customerId)
                .customerCode(customerCode).customerName(customerName).build());
        when(productTypes.getAuthorizedDeviceProductType(any())).thenReturn(List.of(
                new AuthorizedDeviceProductTypeResult(1L, "SWITCH", "交换机", true, "1", "RESOLVED", "SYNCED", null, false)));
        when(tasks.insert(any(InspectionTaskDO.class))).thenAnswer(call -> {
            InspectionTaskDO row = call.getArgument(0); row.setId(100L); return 1;
        });
        var input = command();
        service.create(InspectionTaskService.Create.builder().context(input.context()).projectId(input.projectId())
                .deviceIds(input.deviceIds()).background(input.background()).mode(input.mode()).customerName(manualName).build());
        var saved = org.mockito.ArgumentCaptor.forClass(InspectionTaskDO.class);
        verify(tasks).insert(saved.capture());
        return saved.getValue();
    }

    private void prepare(Long projectId, LocalDateTime shippedAt) {
        when(access.project(any())).thenReturn(ProjectBusinessContextApi.Context.builder()
                .tenantId(7L).projectId(10L).projectName("项目").customerId(30L).customerName("客户").build());
        lenient().when(deviceSelection.validateSelection(10L, List.of(1L))).thenReturn(List.of(
                new SelectedProjectDevice(1L, "SN-1", "设备", "CODE", "MODEL", "CONTRACT")));
        when(deviceFacts.getVisibleDeviceSummaries(any())).thenReturn(List.of(new DeviceSummaryDTO(1L, 7L, "SN-1", "CODE", "MODEL",
                "设备", shippedAt, null, "CONTRACT", null, projectId, 1L, 30L, 1L,
                null, null, null, null, null, null, null, null, null, null)));
    }

    private InspectionTaskService.Create command() {
        return InspectionTaskService.Create.builder().context(InspectionRequestContext.builder()
                .tenantId(7L).actorId(13L).build()).projectId(10L).deviceIds(List.of(1L))
                .background("例行巡检").mode("ONLINE").build();
    }
}
