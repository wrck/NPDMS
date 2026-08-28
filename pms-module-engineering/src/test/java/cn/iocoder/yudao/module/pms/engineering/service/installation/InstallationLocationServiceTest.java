package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationRespDTO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallationLocationServiceTest {

    @Mock private InstallationMapper mapper;
    @Mock private EngineeringLocationFactService locationFactService;
    @Mock private AssetLocationApi assetLocationApi;
    private InstallationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InstallationServiceImpl();
        ReflectionTestUtils.setField(service, "installationMapper", mapper);
        ReflectionTestUtils.setField(service, "locationFactService", locationFactService);
        ReflectionTestUtils.setField(service, "assetLocationApi", assetLocationApi);
        lenient().when(mapper.updateById(any(InstallationDO.class))).thenReturn(1);
    }

    @Test
    void createsStructuredAndFallbackInstallationLocations() {
        when(mapper.insert(any(InstallationDO.class))).thenAnswer(invocation -> {
            InstallationDO value = invocation.getArgument(0);
            value.setId(201L);
            value.setVersion(0);
            return 1;
        });
        when(locationFactService.maintain(anyLong(), eq("INSTALLATION"), eq(201L), eq(0),
                eq("机房A"), any())).thenReturn(new EngineeringLocationFactService.LocationFact(
                11L, 1, 21L, 2, 31L, 3, "RESOLVED", "address", "location"));

        InstallationSaveReqVO structured = request("机房A");
        structured.setStatus(2);
        structured.setLocationMaintenance(emptyMaintenance());
        service.createInstallation(structured);
        verify(mapper).updateById(argThat((InstallationDO value) -> value.getStatus() == 0
                && value.getSiteLocationId().equals(31L)
                && "RESOLVED".equals(value.getLocationResolutionStatus())));

        reset(mapper, locationFactService);
        when(mapper.insert(any(InstallationDO.class))).thenAnswer(invocation -> {
            InstallationDO value = invocation.getArgument(0);
            value.setId(202L);
            value.setVersion(0);
            return 1;
        });
        when(mapper.updateById(any(InstallationDO.class))).thenReturn(1);
        service.createInstallation(request("未结构化地址"));
        verify(mapper).updateById(argThat((InstallationDO value) ->
                "UNRESOLVED".equals(value.getLocationResolutionStatus())));
        verifyNoInteractions(locationFactService);
    }

    @Test
    void completedMoveClosesPreviousIntervalAndEffectsEquipmentLocation() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 23, 9, 30);
        InstallationDO current = installation(200L, 8L, 2, completedAt.minusDays(1));
        InstallationDO next = installation(201L, 8L, 1, completedAt);
        next.setSiteId(21L);
        next.setSiteLocationId(31L);
        next.setSiteLocationVersion(3);
        next.setLocationResolutionStatus("RESOLVED");
        next.setLocationSnapshot("snapshot");
        when(mapper.selectById(201L)).thenReturn(next);
        when(mapper.selectCurrentByEquipmentId(8L)).thenReturn(current);
        when(assetLocationApi.getSiteLocation(31L, 3)).thenReturn(new SiteLocationRespDTO(
                31L, 21L, null, "R1", "机柜", "RACK", "/", 0, 0, 0, 3));

        service.completeInstallation(201L);

        assertEquals(completedAt, current.getEffectiveTo());
        assertEquals(2, next.getStatus());
        assertEquals(completedAt, next.getEffectiveFrom());
        ArgumentCaptor<EquipmentLocationEffectiveCommand> command =
                ArgumentCaptor.forClass(EquipmentLocationEffectiveCommand.class);
        verify(assetLocationApi).effectEquipmentLocation(command.capture());
        assertEquals(31L, command.getValue().siteLocationId());
        assertEquals(201L, command.getValue().installationId());
    }

    @Test
    void letsMybatisManageVersionAndRejectsStaleStatusUpdate() {
        InstallationDO installation = installation(201L, 8L, 0, null);
        installation.setVersion(7);
        when(mapper.selectById(201L)).thenReturn(installation);

        service.startInstallation(201L);

        verify(mapper).updateById(argThat((InstallationDO value) -> value.getStatus() == 1
                && value.getVersion() == 7));

        reset(mapper);
        InstallationDO stale = installation(202L, 8L, 0, null);
        stale.setVersion(7);
        when(mapper.selectById(202L)).thenReturn(stale);
        when(mapper.updateById(any(InstallationDO.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.startInstallation(202L));
    }

    @Test
    void assetFailurePropagatesSoCompletionTransactionCanRollback() {
        InstallationDO installation = installation(201L, 8L, 1, LocalDateTime.now());
        installation.setLocationResolutionStatus("UNRESOLVED");
        when(mapper.selectById(201L)).thenReturn(installation);
        doThrow(new RuntimeException("AST unavailable")).when(assetLocationApi).effectEquipmentLocation(any());

        assertThrows(RuntimeException.class, () -> service.completeInstallation(201L));
        verify(mapper).updateById(argThat((InstallationDO value) -> value.getStatus() == 2));
    }

    @Test
    void rejectsMissingFallbackLocation() {
        when(mapper.insert(any(InstallationDO.class))).thenAnswer(invocation -> {
            InstallationDO value = invocation.getArgument(0);
            value.setId(203L);
            value.setVersion(0);
            return 1;
        });
        assertThrows(ServiceException.class, () -> service.createInstallation(request(" ")));
    }

    @Test
    void rejectsUnresolvedStructuredLocation() {
        when(mapper.insert(any(InstallationDO.class))).thenAnswer(invocation -> {
            InstallationDO value = invocation.getArgument(0);
            value.setId(204L);
            value.setVersion(0);
            return 1;
        });
        when(locationFactService.maintain(anyLong(), eq("INSTALLATION"), eq(204L), eq(0),
                anyString(), any())).thenReturn(new EngineeringLocationFactService.LocationFact(
                null, null, null, null, null, null, "UNRESOLVED", null, null));
        InstallationSaveReqVO request = request("机房A");
        request.setLocationMaintenance(emptyMaintenance());

        assertThrows(ServiceException.class, () -> service.createInstallation(request));
    }

    @Test
    void rejectsOutOfOrderEffectiveTime() {
        LocalDateTime currentFrom = LocalDateTime.of(2026, 8, 23, 10, 0);
        InstallationDO current = installation(200L, 8L, 2, currentFrom);
        current.setEffectiveFrom(currentFrom);
        InstallationDO older = installation(201L, 8L, 1, currentFrom.minusHours(1));
        older.setLocationResolutionStatus("UNRESOLVED");
        when(mapper.selectById(201L)).thenReturn(older);
        when(mapper.selectCurrentByEquipmentId(8L)).thenReturn(current);

        assertThrows(ServiceException.class, () -> service.completeInstallation(201L));
        verify(assetLocationApi, never()).effectEquipmentLocation(any());
    }

    private InstallationSaveReqVO request(String location) {
        InstallationSaveReqVO request = new InstallationSaveReqVO();
        request.setProjectId(1L);
        request.setCode("INS-1");
        request.setEquipmentId(8L);
        request.setInstallLocation(location);
        return request;
    }

    private InstallationDO installation(Long id, Long equipmentId, int status, LocalDateTime time) {
        InstallationDO installation = new InstallationDO();
        installation.setId(id);
        installation.setProjectId(1L);
        installation.setEquipmentId(equipmentId);
        installation.setInstallLocation("机房A");
        installation.setInstallTime(time);
        installation.setStatus(status);
        installation.setVersion(0);
        return installation;
    }

    private LocationMaintenanceCommand emptyMaintenance() {
        return new LocationMaintenanceCommand(null, null, null, null, null, null, null, null);
    }
}
