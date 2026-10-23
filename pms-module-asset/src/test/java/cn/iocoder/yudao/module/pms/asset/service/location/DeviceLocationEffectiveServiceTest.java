package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.DeviceLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.DeviceLocationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.query.DeviceLocationProjectionUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_LOCATION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceLocationEffectiveServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceLocationMapper locationMapper;
    @Mock private SiteMapper siteMapper;
    @Mock private SiteLocationTreeService treeService;
    private DeviceLocationEffectiveService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new DeviceLocationEffectiveService(deviceMapper, locationMapper, siteMapper, treeService);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCloseCurrentFactAndProjectNewLocation() {
        DeviceDO device = device();
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device);
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(null);
        when(deviceMapper.updateLocationProjection(any(DeviceLocationProjectionUpdate.class))).thenReturn(1);

        assertTrue(service.effect(command()));

        verify(locationMapper).closeCurrent(1L, "SN-8", command().effectiveFrom());
        verify(locationMapper).insert(argThat((DeviceLocationDO value) ->
                "SN-8".equals(value.getDeviceSn()) && Long.valueOf(201L).equals(value.getInstallationId())
                        && command().locationSnapshot().equals(value.getLocationSnapshot())));
        verify(deviceMapper).updateLocationProjection(argThat((DeviceLocationProjectionUpdate value) ->
                Long.valueOf(21L).equals(value.siteId())
                        && Long.valueOf(31L).equals(value.siteLocationId())
                        && "RESOLVED".equals(value.resolutionStatus())
                        && command().locationText().equals(JsonUtils.parseTree(value.locationSnapshot()).get("fallbackLocation").asText())
                        && JsonUtils.parseTree(value.locationSnapshot()).get("site").get("id").asLong() == 21L));
    }

    @Test
    void shouldProjectManualTextInSnapshotWithoutInventingSiteReferences() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device());
        when(deviceMapper.updateLocationProjection(any())).thenReturn(1);
        var manual = new EquipmentLocationEffectiveCommand(8L,201L,null,null,"手工完整安装地址","UNRESOLVED",null,LocalDateTime.now());
        assertTrue(service.effect(manual));
        verify(locationMapper).insert(argThat((DeviceLocationDO history) -> history.getLocationSnapshot() == null));
        verify(deviceMapper).updateLocationProjection(argThat(update ->
                "手工完整安装地址".equals(JsonUtils.parseTree(update.locationSnapshot()).get("fallbackLocation").asText()) && update.siteId() == null && update.siteLocationId() == null));
    }

    @Test
    void shouldReplaySameInstallationWithoutSecondWrite() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device());
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(location("SN-8", 21L, 31L));

        assertTrue(service.effect(command()));

        verify(locationMapper, never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper, never()).updateLocationProjection(any(DeviceLocationProjectionUpdate.class));
    }

    @Test
    void shouldRejectSameInstallationWithDifferentRequest() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device());
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(location("SN-8", 22L, 31L));

        ServiceException error = assertThrows(ServiceException.class, () -> service.effect(command()));

        assertEquals(AST_EQUIPMENT_LOCATION_CONFLICT.getCode(), error.getCode());
        verify(locationMapper, never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper, never()).updateLocationProjection(any(DeviceLocationProjectionUpdate.class));
    }

    @Test
    void shouldRejectInstallationAlreadyUsedByAnotherDevice() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device());
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(location("SN-9", 21L, 31L));

        ServiceException error = assertThrows(ServiceException.class, () -> service.effect(command()));

        assertEquals(AST_EQUIPMENT_LOCATION_CONFLICT.getCode(), error.getCode());
        verify(locationMapper, never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper, never()).updateLocationProjection(any(DeviceLocationProjectionUpdate.class));
    }

    @Test
    void shouldRejectStaleDeviceVersionWhenProjectionCasFails() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device());
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(null);
        when(deviceMapper.updateLocationProjection(any(DeviceLocationProjectionUpdate.class))).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class, () -> service.effect(command()));

        assertEquals(AST_EQUIPMENT_LOCATION_CONFLICT.getCode(), error.getCode());
    }

    private DeviceDO device() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        device.setVersion(2L);
        return device;
    }

    @Test void historicalFillCannotCloseOrReplaceAnotherCurrentLocation() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L,8L)).thenReturn(device());
        var current=location("SN-8",null,null);current.setInstallationId(999L);
        when(locationMapper.selectCurrent(any())).thenReturn(current);
        var input=new EquipmentLocationEffectiveCommand(8L,201L,null,null,"原地址","UNRESOLVED","snapshot",command().effectiveFrom());
        assertThrows(ServiceException.class,()->service.effectIfAbsentOrThrow(input));
        verify(locationMapper,never()).closeCurrent(any(),any(),any());
        verify(locationMapper,never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper,never()).updateLocationProjection(any());
    }

    @Test void historicalFillUsesNormalProjectionOwnerOnlyWhenEmpty() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L,8L)).thenReturn(device());
        when(deviceMapper.updateLocationProjection(any())).thenReturn(1);
        var input=new EquipmentLocationEffectiveCommand(8L,201L,null,null,"原地址","UNRESOLVED","snapshot",command().effectiveFrom());
        service.effectIfAbsentOrThrow(input);
        verify(locationMapper).insert(argThat((DeviceLocationDO value)->value.getInstallationId().equals(201L)
                && value.getEffectiveFrom().equals(input.effectiveFrom())));
        verify(deviceMapper).updateLocationProjection(any());
    }

    @Test void historicalFillReplaysItsOwnCurrentLocationWithoutWrites() {
        var device=device();var current=location("SN-8",null,null);current.setId(300L);current.setResolutionStatus("UNRESOLVED");
        current.setLocationSnapshot("snapshot");
        device.setLocationRecordId(300L);device.setLocationSnapshot(current.getLocationSnapshot());device.setLocationEffectiveFrom(current.getEffectiveFrom());
        when(deviceMapper.selectByTenantAndIdForUpdate(1L,8L)).thenReturn(device);
        when(locationMapper.selectCurrent(any())).thenReturn(current);
        when(locationMapper.selectByTenantAndInstallation(1L,201L)).thenReturn(current);
        var input=new EquipmentLocationEffectiveCommand(8L,201L,null,null,"原地址","UNRESOLVED","snapshot",command().effectiveFrom());
        service.effectIfAbsentOrThrow(input);
        verify(locationMapper,never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper,never()).updateLocationProjection(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"{}", "原始非JSON快照"})
    void historicalFillReplaysItsOwnDerivedDisplayProjectionWithoutWrites(String rawSnapshot) {
        var device=device();var current=location("SN-8",null,null);
        current.setId(300L);current.setResolutionStatus("UNRESOLVED");current.setLocationSnapshot(rawSnapshot);
        device.setLocationRecordId(300L);device.setLocationSnapshot("{\"fallbackLocation\":\"原地址\"}");
        device.setLocationEffectiveFrom(current.getEffectiveFrom());
        when(deviceMapper.selectByTenantAndIdForUpdate(1L,8L)).thenReturn(device);
        when(locationMapper.selectCurrent(any())).thenReturn(current);
        when(locationMapper.selectByTenantAndInstallation(1L,201L)).thenReturn(current);
        var input=new EquipmentLocationEffectiveCommand(8L,201L,null,null,"原地址","UNRESOLVED",rawSnapshot,command().effectiveFrom());
        service.effectIfAbsentOrThrow(input);
        verify(locationMapper,never()).closeCurrent(any(),any(),any());
        verify(locationMapper,never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper,never()).updateLocationProjection(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"fallbackLocation\":\"被改写地址\"}",
            "{\"fallbackLocation\":\"原地址\",\"extra\":true}"})
    void historicalFillCannotReplayATamperedDisplayProjection(String projection) {
        var device=device();var current=location("SN-8",null,null);
        current.setId(300L);current.setResolutionStatus("UNRESOLVED");current.setLocationSnapshot("{}");
        device.setLocationRecordId(300L);device.setLocationSnapshot(projection);
        device.setLocationEffectiveFrom(current.getEffectiveFrom());
        when(deviceMapper.selectByTenantAndIdForUpdate(1L,8L)).thenReturn(device);
        when(locationMapper.selectCurrent(any())).thenReturn(current);
        var input=new EquipmentLocationEffectiveCommand(8L,201L,null,null,"原地址","UNRESOLVED","{}",command().effectiveFrom());
        assertThrows(ServiceException.class,()->service.effectIfAbsentOrThrow(input));
        verify(locationMapper,never()).closeCurrent(any(),any(),any());
        verify(locationMapper,never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper,never()).updateLocationProjection(any());
    }

    @Test void historicalFillReplaysEquivalentFullJsonProjectionWithoutChangingRawHistory() {
        var device=device();var current=location("SN-8",null,null);
        current.setId(300L);current.setResolutionStatus("UNRESOLVED");current.setLocationSnapshot("{\"room\":\"机房\"}");
        device.setLocationRecordId(300L);device.setLocationSnapshot("{ \"fallbackLocation\": \"原地址\", \"room\": \"机房\" }");
        device.setLocationEffectiveFrom(current.getEffectiveFrom());
        when(deviceMapper.selectByTenantAndIdForUpdate(1L,8L)).thenReturn(device);
        when(locationMapper.selectCurrent(any())).thenReturn(current);
        when(locationMapper.selectByTenantAndInstallation(1L,201L)).thenReturn(current);
        var input=new EquipmentLocationEffectiveCommand(8L,201L,null,null,"原地址","UNRESOLVED",current.getLocationSnapshot(),command().effectiveFrom());
        service.effectIfAbsentOrThrow(input);
        assertEquals("{\"room\":\"机房\"}",current.getLocationSnapshot());
        verify(locationMapper,never()).closeCurrent(any(),any(),any());
        verify(locationMapper,never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper,never()).updateLocationProjection(any());
    }

    @Test void historicalFillCannotChangeRawHistoryFormattingOnReplay() {
        var device=device();var current=location("SN-8",null,null);
        current.setId(300L);current.setResolutionStatus("UNRESOLVED");current.setLocationSnapshot("{}");
        device.setLocationRecordId(300L);device.setLocationSnapshot("{\"fallbackLocation\":\"原地址\"}");
        device.setLocationEffectiveFrom(current.getEffectiveFrom());
        when(deviceMapper.selectByTenantAndIdForUpdate(1L,8L)).thenReturn(device);
        when(locationMapper.selectCurrent(any())).thenReturn(current);
        var input=new EquipmentLocationEffectiveCommand(8L,201L,null,null,"原地址","UNRESOLVED"," { } ",command().effectiveFrom());
        assertThrows(ServiceException.class,()->service.effectIfAbsentOrThrow(input));
        verify(locationMapper,never()).closeCurrent(any(),any(),any());
        verify(locationMapper,never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper,never()).updateLocationProjection(any());
    }

    @Test void historicalFillCannotReplayAnAlreadyEndedLocationAsCurrent() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L,8L)).thenReturn(device());
        var ended=location("SN-8",null,null);ended.setEffectiveTo(command().effectiveFrom().plusDays(1));
        when(locationMapper.selectByTenantAndInstallation(1L,201L)).thenReturn(ended);
        var input=new EquipmentLocationEffectiveCommand(8L,201L,null,null,"原地址","UNRESOLVED","snapshot",command().effectiveFrom());
        assertThrows(ServiceException.class,()->service.effectIfAbsentOrThrow(input));
        verify(locationMapper,never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper,never()).updateLocationProjection(any());
    }

    @Test void historicalFillCannotOverwriteAnUnlinkedExistingProjection() {
        var device=device();device.setLocationSnapshot("existing projection");
        when(deviceMapper.selectByTenantAndIdForUpdate(1L,8L)).thenReturn(device);
        var input=new EquipmentLocationEffectiveCommand(8L,201L,null,null,"原地址","UNRESOLVED","snapshot",command().effectiveFrom());
        assertThrows(ServiceException.class,()->service.effectIfAbsentOrThrow(input));
        verify(locationMapper,never()).insert(any(DeviceLocationDO.class));verify(deviceMapper,never()).updateLocationProjection(any());
    }

    private DeviceLocationDO location(String deviceSn, Long siteId, Long siteLocationId) {
        DeviceLocationDO location = new DeviceLocationDO();
        location.setTenantId(1L);
        location.setDeviceSn(deviceSn);
        location.setSiteId(siteId);
        location.setSiteLocationId(siteLocationId);
        location.setResolutionStatus("RESOLVED");
        location.setLocationSnapshot(command().locationSnapshot());
        location.setEffectiveFrom(command().effectiveFrom());
        location.setInstallationId(201L);
        return location;
    }

    private EquipmentLocationEffectiveCommand command() {
        return new EquipmentLocationEffectiveCommand(
                8L, 201L, 21L, 31L, "机柜A", "RESOLVED", "{\"site\":{\"id\":21},\"fallbackLocation\":\"旧文本\"}",
                LocalDateTime.of(2026, 8, 27, 15, 30));
    }
}
