package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigurationLogImportApi;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationLogImportServiceTest {
    final ConfigurationMapper mapper = mock(ConfigurationMapper.class);
    final EngineeringRecordCodeGenerator codes = mock(EngineeringRecordCodeGenerator.class);
    final DeviceConfigurationLogImportApi logs = mock(DeviceConfigurationLogImportApi.class);
    final ProjectDeviceSelectionApi devices = mock(ProjectDeviceSelectionApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final ConfigurationLogImportService service = new ConfigurationLogImportService(mapper, codes, logs, devices, scopes, permissions);
    final byte[] input = "test log".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(7L); user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        when(permissions.hasAnyPermissions(7L, "pms:imp-configuration:create")).thenReturn(true);
        var scope = new ProjectScopeResult(21L, 1L, Set.of(21L), Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope); when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        when(logs.importLog(any())).thenReturn(archived(new DeviceConfigurationLogImportApi.File(100L, 31L, "SN-1", true)));
        when(codes.next(any(), anyString(), any(), any(String[].class))).thenReturn("TEST-PZ-001");
        when(mapper.insert(any(ConfigurationDO.class))).thenAnswer(call -> {
            ConfigurationDO row = call.getArgument(0); row.setId(row.getImportedLogFileId() + 1000); return 1;
        });
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    DeviceConfigurationLogImportApi.Result archived(DeviceConfigurationLogImportApi.File... files) {
        return new DeviceConfigurationLogImportApi.Result(50L, "ARCHIVED", null, List.of(files));
    }
    ConfigurationLogImportService.Imported upload() { return service.importLog(21L, "operation", "test.log", input); }

    @Test void multiDeviceUploadOnlyCreatesPendingRecordsWithoutManualDebugFields() {
        when(logs.importLog(any())).thenReturn(archived(new DeviceConfigurationLogImportApi.File(100L,31L,"SN-1",true),
                new DeviceConfigurationLogImportApi.File(101L,32L,"SN-2",true)));
        assertEquals(List.of(1100L,1101L), upload().configurationIds());
        verify(devices).validateSelection(21L, List.of(31L,32L));
        var rows = org.mockito.ArgumentCaptor.forClass(ConfigurationDO.class);
        verify(mapper,times(2)).insert(rows.capture());
        for (var row : rows.getAllValues()) {
            assertEquals(0,row.getStatus()); assertEquals(0L,row.getVersion()); assertEquals(1L,row.getTenantId());
            assertEquals(50L,row.getImportedLogBatchId()); assertEquals("7",row.getCreator());
            assertNull(row.getDebugResult()); assertNull(row.getDebugTime()); assertNull(row.getDebuggerUserId());
        }
        verify(mapper,never()).updateById(any(ConfigurationDO.class));
    }
    @Test void replayRetainsAnAlreadyCompletedRecord() {
        var row = new ConfigurationDO(); row.setId(70L); row.setProjectId(21L); row.setEquipmentId(31L);
        row.setStatus(2); row.setVersion(8L);
        when(mapper.selectByImportedLogForUpdate(any())).thenReturn(row);
        assertEquals(List.of(70L),upload().configurationIds());
        assertEquals(2,row.getStatus()); assertEquals(8L,row.getVersion());
        verify(mapper,never()).insert(any(ConfigurationDO.class));
    }
    @Test void missingCreatePermissionFailsBeforeArchive() {
        when(permissions.hasAnyPermissions(7L,"pms:imp-configuration:create")).thenReturn(false);
        assertThrows(RuntimeException.class,this::upload); verifyNoInteractions(logs,mapper);
    }
    @Test void emptyScopeFailsBeforeArchive() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(21L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,this::upload); verifyNoInteractions(logs,mapper);
    }
    @Test void staleScopeFailsBeforeArchive() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(21L,2L,Set.of(21L),Set.of()));
        assertThrows(RuntimeException.class,this::upload); verifyNoInteractions(logs,mapper);
    }
    @Test void revokedScopeFailsBeforeArchive() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(21L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,this::upload); verifyNoInteractions(logs,mapper);
    }
    @Test void parserRejectionKeepsReasonAndCreatesNoConfigurations() {
        when(logs.importLog(any())).thenReturn(new DeviceConfigurationLogImportApi.Result(50L,"REJECTED","日志块缺少设备序列号",List.of()));
        assertTrue(assertThrows(RuntimeException.class,this::upload).getMessage().contains("日志块缺少设备序列号"));
        verifyNoInteractions(devices,mapper);
    }
    @Test void foreignSerialOrProviderFailureCreatesNoConfiguration() {
        when(logs.importLog(any())).thenThrow(new IllegalStateException("设备不属于当前项目"));
        assertThrows(IllegalStateException.class,this::upload); verifyNoInteractions(devices,mapper);
    }
    @Test void unavailableFileRejectsBeforeSavingAnyDevice() {
        when(logs.importLog(any())).thenReturn(archived(new DeviceConfigurationLogImportApi.File(100L,31L,"SN-1",true),
                new DeviceConfigurationLogImportApi.File(101L,32L,"SN-2",false)));
        assertThrows(RuntimeException.class,this::upload); verifyNoInteractions(devices,mapper);
    }
    @Test void currentDeviceReassignmentRejectsBeforeSaving() {
        when(devices.validateSelection(21L,List.of(31L))).thenThrow(new IllegalStateException("归属已变化"));
        assertThrows(IllegalStateException.class,this::upload); verifyNoInteractions(mapper);
    }
    @Test void inconsistentExistingLinkIsNotReused() {
        var row = new ConfigurationDO(); row.setProjectId(99L); row.setEquipmentId(31L);
        when(mapper.selectByImportedLogForUpdate(any())).thenReturn(row);
        assertThrows(RuntimeException.class,this::upload); verify(mapper,never()).insert(any(ConfigurationDO.class));
    }
    @Test void uniqueFileRaceReusesWinnerWithoutResettingIt() {
        var row = new ConfigurationDO(); row.setId(70L); row.setProjectId(21L); row.setEquipmentId(31L); row.setStatus(2);
        when(mapper.selectByImportedLogForUpdate(any())).thenReturn(null,row);
        when(mapper.insert(any(ConfigurationDO.class))).thenThrow(new DuplicateKeyException("import file"));
        assertEquals(List.of(70L),upload().configurationIds()); assertEquals(2,row.getStatus());
    }
    @Test void occupiedGeneratedCodeAdvancesWithoutLosingTheSource() {
        when(codes.next(any(),anyString(),any(),any(String[].class))).thenReturn("TEST-PZ-001","TEST-PZ-002");
        when(mapper.insert(any(ConfigurationDO.class))).thenThrow(new DuplicateKeyException("code"))
                .thenAnswer(call->{ ConfigurationDO row=call.getArgument(0); row.setId(70L); return 1; });
        when(mapper.selectCodeIncludingDeletedForUpdate(any())).thenReturn(new ConfigurationDO());
        assertEquals(List.of(70L),upload().configurationIds());
        verify(codes).next(21L,EngineeringRecordCodeGenerator.CONFIGURATION,mapper,"TEST-PZ-001");
    }
    @Test void unknownUniqueFailureIsNotHiddenByRetry() {
        when(mapper.insert(any(ConfigurationDO.class))).thenThrow(new DuplicateKeyException("other key"));
        assertThrows(DuplicateKeyException.class,this::upload); verify(mapper,times(1)).insert(any(ConfigurationDO.class));
    }
    @Test void writeFailureDoesNotReturnSuccessfulImport() {
        when(mapper.insert(any(ConfigurationDO.class))).thenReturn(0);
        assertThrows(RuntimeException.class,this::upload);
    }
}
