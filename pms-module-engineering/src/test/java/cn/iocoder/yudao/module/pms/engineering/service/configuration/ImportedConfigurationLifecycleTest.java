package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigurationLogImportApi;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportedConfigurationLifecycleTest {
    final ConfigurationServiceImpl service = new ConfigurationServiceImpl();
    final ConfigurationMapper mapper = mock(ConfigurationMapper.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final DeviceConfigurationLogImportApi logs = mock(DeviceConfigurationLogImportApi.class);
    ConfigurationDO row;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(7L); user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));
        ReflectionTestUtils.setField(service,"configurationMapper",mapper);
        ReflectionTestUtils.setField(service,"importedLogs",logs);
        ReflectionTestUtils.setField(service,"importedLogScopes",scopes);
        row=new ConfigurationDO(); row.setId(70L); row.setTenantId(1L); row.setProjectId(21L); row.setEquipmentId(31L);
        row.setImportedLogFileId(100L); row.setImportedLogBatchId(50L); row.setStatus(0); row.setVersion(0L);
        when(mapper.selectById(70L)).thenReturn(row); when(mapper.updateById(any(ConfigurationDO.class))).thenReturn(1);
        var scope=new ProjectScopeResult(21L,1L,Set.of(21L),Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope); when(scopes.lockAndRevalidate(any())).thenReturn(scope);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void existingStartAndCompleteRevalidateFileAndKeepSourceReference() {
        service.startConfiguration(70L); assertEquals(1,row.getStatus());
        service.completeConfiguration(70L); assertEquals(2,row.getStatus());
        assertEquals(100L,row.getImportedLogFileId()); assertEquals(50L,row.getImportedLogBatchId());
        verify(logs,times(2)).validateFile(new DeviceConfigurationLogImportApi.FileQuery(1L,7L,21L,31L,100L));
    }
    @Test void unavailableFileCannotStartOrComplete() {
        doThrow(new IllegalStateException("日志不可用")).when(logs).validateFile(any());
        assertThrows(IllegalStateException.class,()->service.startConfiguration(70L)); assertEquals(0,row.getStatus());
        row.setStatus(1);
        assertThrows(IllegalStateException.class,()->service.completeConfiguration(70L)); assertEquals(1,row.getStatus());
        verify(mapper,never()).updateById(any(ConfigurationDO.class));
    }
    @Test void revokedEditScopeCannotStart() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(21L,1L,Set.of(),Set.of()));
        assertThrows(IllegalStateException.class,()->service.startConfiguration(70L)); verifyNoInteractions(logs);
        verify(mapper,never()).updateById(any(ConfigurationDO.class));
    }
    @Test void staleScopeCannotComplete() {
        row.setStatus(1);
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(21L,2L,Set.of(21L),Set.of()));
        assertThrows(IllegalStateException.class,()->service.completeConfiguration(70L)); verifyNoInteractions(logs);
        assertEquals(1,row.getStatus());
    }
    @Test void foreignTenantRecordCannotStart() {
        row.setTenantId(2L);
        assertThrows(IllegalStateException.class,()->service.startConfiguration(70L)); verifyNoInteractions(scopes,logs);
    }
    @Test void manualUpdateAndDeleteCannotRemoveImmutableSourceHistory() {
        var update=new ConfigurationSaveReqVO(); update.setId(70L); update.setProjectId(21L);
        assertThrows(IllegalStateException.class,()->service.updateConfiguration(update));
        assertThrows(IllegalStateException.class,()->service.deleteConfiguration(70L));
        assertEquals(100L,row.getImportedLogFileId()); assertEquals(0L,row.getVersion());
        verify(mapper,never()).updateById(any(ConfigurationDO.class)); verify(mapper,never()).deleteById(anyLong());
    }
}
