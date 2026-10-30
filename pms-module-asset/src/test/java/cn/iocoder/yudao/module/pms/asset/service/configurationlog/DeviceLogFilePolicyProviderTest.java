package cn.iocoder.yudao.module.pms.asset.service.configurationlog;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogFileDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceLogFileMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.NativeGeneratedFilePolicyQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.*;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class DeviceLogFilePolicyProviderTest {
    final DeviceLogFileMapper files=mock(DeviceLogFileMapper.class);
    final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    DeviceLogFilePolicyProvider provider;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        ObjectProvider<ProjectScopeApi> access=mock(ObjectProvider.class);when(access.getObject()).thenReturn(scopes);
        provider=new DeviceLogFilePolicyProvider(files,access,permissions);
        var row=new DeviceLogFileDO();row.setId(900L);row.setTenantId(1L);row.setProjectId(10L);row.setSourceType("MANUAL_UPLOAD");
        when(files.selectById(900L)).thenReturn(row);when(files.lockFile(any())).thenReturn(row);
        when(permissions.hasAnyPermissions(eq(7L),anyString())).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    NativeGeneratedFilePolicyQuery command(Long version) {
        return new NativeGeneratedFilePolicyQuery(1L,7L,"AST","DeviceConfigurationLogFile",900L,1L,"DEVICE_CONFIGURATION_LOG",version);
    }
    @Test void initialNativeGenerationResolvesScopeBeforeMandatoryVersionLock() {
        assertTrue(provider.lockAndRevalidateNativeGeneratedFile(command(null)).allowed());
        verify(scopes).lockAndRevalidate(argThat(q -> Long.valueOf(1).equals(q.expectedScopeVersion())));
    }
    @Test void scopeVersionChangeDeniesBeforeFileLock() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L,2L,Set.of(10L),Set.of()));
        assertFalse(provider.lockAndRevalidateNativeGeneratedFile(command(1L)).allowed());
        verify(files,never()).lockFile(any());
    }
    @Test void missingProjectScopeDeniesNativeGeneration() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(),Set.of()));
        assertFalse(provider.lockAndRevalidateNativeGeneratedFile(command(null)).allowed());
        verify(scopes,never()).lockAndRevalidate(any());
    }
    @Test void missingImportPermissionDeniesBeforeProjectScope() {
        when(permissions.hasAnyPermissions(eq(7L),anyString())).thenReturn(false);
        assertFalse(provider.lockAndRevalidateNativeGeneratedFile(command(null)).allowed());
        verifyNoInteractions(scopes);
    }
}
