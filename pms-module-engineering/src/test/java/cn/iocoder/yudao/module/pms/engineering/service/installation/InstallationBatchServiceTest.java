package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationBatchCreateReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstallationBatchServiceTest {
    final cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents events = mock(cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents.class);
    final InstallationMapper mapper = mock(InstallationMapper.class);
    final ProjectDeviceSelectionApi devices = mock(ProjectDeviceSelectionApi.class);
    final EngineeringLocationFactService locations = mock(EngineeringLocationFactService.class);
    final cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi asset = mock(cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi.class);
    final java.util.Map<Long, InstallationDO> savedRows = new java.util.HashMap<>();
    JdbcTemplate jdbc;
    InstallationService service;
    InstallationBatchCreateReqVO request;

    @BeforeEach void setup() {
        var user = new cn.iocoder.yudao.framework.security.core.LoginUser();
        user.setId(7L); user.setTenantId(0L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, List.of()));
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        dataSource.setUrl(dataSource.getUrl() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table installation_test(id bigint primary key, device_id bigint)");
        jdbc.execute("create table location_test(id bigint primary key)");
        jdbc.execute("create table device_projection_test(id bigint primary key, installation_id bigint)");
        doAnswer(call -> {
            EquipmentLocationEffectiveCommand command = call.getArgument(0);
            jdbc.update("insert into device_projection_test values (?, ?)", command.equipmentId(), command.installationId());
            return null;
        }).when(asset).effectEquipmentLocation(any());
        var target = new InstallationServiceImpl();
        var codes = mock(EngineeringRecordCodeGenerator.class);
        ReflectionTestUtils.setField(target, "installationMapper", mapper);
        ReflectionTestUtils.setField(target, "completionEvents", events);
        ReflectionTestUtils.setField(target, "deviceSelectionApi", devices);
        ReflectionTestUtils.setField(target, "locationFactService", locations);
        ReflectionTestUtils.setField(target, "recordCodeGenerator", codes);
        ReflectionTestUtils.setField(target, "assetLocationApi", asset);
        when(mapper.selectById(anyLong())).thenAnswer(call -> savedRows.get(call.getArgument(0)));
        when(asset.getSiteLocation(anyLong(), anyLong())).thenReturn(new SiteLocationRespDTO(22L, 21L, null, "ROOM", "room", "ROOM", null, 0, 0, 0, 0L));
        when(codes.next(any(), anyString(), any())).thenReturn("INS-TEST");
        AtomicLong sequence = new AtomicLong(100);
        when(mapper.insert(any(InstallationDO.class))).thenAnswer(call -> {
            InstallationDO row = call.getArgument(0);
            row.setId(sequence.incrementAndGet());
            savedRows.put(row.getId(), row);
            jdbc.update("insert into installation_test values (?, ?)", row.getId(), row.getEquipmentId());
            return 1;
        });
        when(mapper.updateById(any(InstallationDO.class))).thenReturn(1);
        when(locations.maintain(anyLong(), anyString(), anyLong(), anyLong(), anyString(), any())).thenAnswer(call -> {
            LocationMaintenanceCommand command = call.getArgument(5);
            if (command.site().id() == null) jdbc.update("insert into location_test values (21)");
            return new EngineeringLocationFactService.LocationFact(20L, 0L, 21L, 0L, 22L, 0L, "RESOLVED", "address", "site / room");
        });
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        service = (InstallationService) factory.getProxy();
        request = new InstallationBatchCreateReqVO();
        request.setProjectId(1L);
        request.setEquipmentIds(List.of(11L, 12L, 13L));
        request.setInstallLocation("site / room");
        request.setLocationMaintenance(new LocationMaintenanceCommand(1L, null, new SiteInput(null, null, null, "site", null, null), null, "site / room", null, null, null, List.of(new SiteLocationInput(null, null, null, null, "room", null, 0))));
    }

    @org.junit.jupiter.api.AfterEach void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test void ignoresForgedInstallerAndTimeAndUsesAuthenticatedSaveMetadata() {
        request.setInstallerUserId(999L);
        request.setInstallTime(java.time.LocalDateTime.of(2000,1,1,0,0));
        var before=java.time.LocalDateTime.now();
        service.createInstallationBatch(request);
        var after=java.time.LocalDateTime.now();
        assertTrue(savedRows.values().stream().allMatch(row -> row.getInstallerUserId().equals(7L)
                && !row.getInstallTime().isBefore(before) && !row.getInstallTime().isAfter(after)));
        assertEquals(1,savedRows.values().stream().map(InstallationDO::getInstallTime).distinct().count());
        verify(events,times(3)).changed(eq(1L),eq("Installation"),anyLong(),eq(7L),anyString());
    }

    @Test void anonymousCannotCreateOrChangeInstallationLocations() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var error=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.createInstallationBatch(request));
        assertEquals(401,error.getCode());
        verifyNoInteractions(devices,mapper,locations,asset);
    }

    @Test void savesThreeRecordsWithOneLocationAndImmutableReferences() {
        assertEquals(3, service.createInstallationBatch(request).size());
        assertEquals(3, jdbc.queryForObject("select count(*) from installation_test", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from location_test", Integer.class));
        assertTrue(savedRows.values().stream().allMatch(row -> row.getStatus() == 2 && row.getEffectiveFrom() != null));
        verify(asset, times(3)).effectEquipmentLocation(any());
        var calls = org.mockito.ArgumentCaptor.forClass(LocationMaintenanceCommand.class);
        verify(locations, times(3)).maintain(eq(1L), eq("INSTALLATION"), anyLong(), eq(0L), eq("site / room"), calls.capture());
        for (var command : calls.getAllValues().subList(1, 3)) {
            assertEquals(21L, command.site().id());
            assertEquals(22L, command.siteLocation().id());
            assertNull(command.address());
            assertTrue(command.extraSiteLocations().isEmpty());
        }
    }
    @Test void secondRecordFailureRollsBackAllRecordsAndTheNewLocation() {
        when(mapper.updateById(any(InstallationDO.class))).thenAnswer(call -> {
            InstallationDO row = call.getArgument(0);
            if (row.getEquipmentId().equals(12L)) throw new IllegalStateException("second record failed");
            return 1;
        });
        assertThrows(IllegalStateException.class, () -> service.createInstallationBatch(request));
        assertEquals(0, jdbc.queryForObject("select count(*) from installation_test", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from location_test", Integer.class));
    }
    @Test void locationEffectFailureRollsBackInstallationAndDeviceProjection() {
        doAnswer(call -> {
            EquipmentLocationEffectiveCommand command = call.getArgument(0);
            jdbc.update("insert into device_projection_test values (?, ?)", command.equipmentId(), command.installationId());
            if (command.equipmentId().equals(12L)) throw new IllegalStateException("projection failed");
            return null;
        }).when(asset).effectEquipmentLocation(any());
        assertThrows(IllegalStateException.class, () -> service.createInstallationBatch(request));
        assertEquals(0, jdbc.queryForObject("select count(*) from installation_test", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from location_test", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from device_projection_test", Integer.class));
    }
    @Test void resultPublicationFailureRollsBackRecordsLocationsAndProjections() {
        doThrow(new IllegalStateException("result publication failed")).when(events).changed(anyLong(),eq("Installation"),anyLong(),anyLong(),anyString());
        assertThrows(IllegalStateException.class, () -> service.createInstallationBatch(request));
        assertEquals(0, jdbc.queryForObject("select count(*) from installation_test", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from location_test", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from device_projection_test", Integer.class));
    }
    @Test void invalidDeviceSetIsRejectedBeforeAnyWrites() {
        doThrow(new IllegalArgumentException("invalid selection")).when(devices).validateSelection(anyLong(), anyList());
        assertThrows(IllegalArgumentException.class, () -> service.createInstallationBatch(request));
        verifyNoInteractions(mapper, locations);
    }
    @Test void manualTextDoesNotCreateStructuredLocation() {
        request.setLocationMaintenance(null);
        assertEquals(3, service.createInstallationBatch(request).size());
        verifyNoInteractions(locations);
        assertEquals(0, jdbc.queryForObject("select count(*) from location_test", Integer.class));
    }
}
