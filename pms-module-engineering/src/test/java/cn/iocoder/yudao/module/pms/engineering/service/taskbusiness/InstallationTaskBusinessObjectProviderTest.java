package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationFact;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationBusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstallationTaskBusinessObjectProviderTest {
    final InstallationMapper records = mock(InstallationMapper.class);
    final DeviceQueryApi devices = mock(DeviceQueryApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final InstallationTaskBusinessObjectProvider provider = new InstallationTaskBusinessObjectProvider(
            new InstallationBusinessResultSource(records, devices), scopes, permissions, mock(ProjectNodeExecutionApi.class));
    final Context context = new Context(1L,3L,9L,20L,"test");
    final InstallationDO record = new InstallationDO();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); var login = new LoginUser();login.setId(3L);login.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(login,new MockHttpServletRequest());
        when(permissions.hasAnyPermissions(3L,"pms:imp-installation:query")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
        record.setId(31L);record.setTenantId(1L);record.setProjectId(9L);record.setEquipmentId(7L);record.setVersion(2L);
        record.setStatus(2);record.setInstallLocation("机房");record.setEffectiveFrom(LocalDateTime.now());
        when(records.selectById(31L)).thenReturn(record);
        when(records.selectProjectAddressRecordsForUpdate(any())).thenReturn(List.of(record));
        when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of(new ProjectDeviceInstallationFact(7L,"A",null)));
    }
    @AfterEach void clear(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    @Test void linkedInstallationReadStartsOwnerTransactionThroughTheActualAggregateEntry() {
        var transactions = new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected boolean isExistingTransaction(Object transaction) {
                return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
            }
            @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) { }
            @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) { }
            @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) { }
        };
        var advice = new org.springframework.transaction.interceptor.TransactionInterceptor(
                transactions, new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        var resultProxy = new org.springframework.aop.framework.ProxyFactory(new InstallationBusinessResultSource(records, devices));
        resultProxy.setProxyTargetClass(true);
        resultProxy.addAdvice(advice);
        var owner = new InstallationTaskBusinessObjectProvider((InstallationBusinessResultSource) resultProxy.getProxy(),
                scopes, permissions, mock(ProjectNodeExecutionApi.class));
        var ownerProxy = new org.springframework.aop.framework.ProxyFactory(owner);
        ownerProxy.setProxyTargetClass(true);
        ownerProxy.addAdvice(advice);
        var proxy = (InstallationTaskBusinessObjectProvider) ownerProxy.getProxy();
        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        var inspection = proxy.inspectContextAndObjects(context, List.of("31"));
        assertEquals(Set.of("QUERY"), inspection.allowedActions());
        assertTrue(inspection.objects().getFirst().completionFacts().get("PROJECT_INSTALLATION_COMPLETED"));
        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        when(permissions.hasAnyPermissions(3L, "pms:imp-installation:query")).thenReturn(false);
        assertThrows(SecurityException.class, () -> proxy.inspectContextAndObjects(context, List.of("31")));
    }
    @Test void currentDeviceScopeAndEffectiveOwnerRecordsDetermineAggregateCompletion() {
        assertTrue(provider.inspect(context,"31").completionFacts().get("PROJECT_INSTALLATION_COMPLETED"));
        String version = provider.inspect(context,"31").factVersion();
        when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of(new ProjectDeviceInstallationFact(7L,"A",null),new ProjectDeviceInstallationFact(8L,"B",null)));
        assertFalse(provider.inspect(context,"31").completionFacts().get("PROJECT_INSTALLATION_COMPLETED"));
        assertThrows(IllegalArgumentException.class,()->provider.lockAndRevalidate(context,"31",version));
        when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of());
        assertFalse(provider.inspect(context,"31").completionFacts().get("PROJECT_INSTALLATION_COMPLETED"));
    }
    @Test void partialExpiredAndUnfinishedInstallationNeverQualify() {
        record.setStatus(1);assertFalse(provider.inspect(context,"31").completionFacts().get("PROJECT_INSTALLATION_COMPLETED"));
        record.setStatus(2);record.setEffectiveTo(LocalDateTime.now());assertFalse(provider.inspect(context,"31").completionFacts().get("PROJECT_INSTALLATION_COMPLETED"));
        record.setEffectiveTo(null);record.setInstallLocation(" ");assertFalse(provider.inspect(context,"31").completionFacts().get("PROJECT_INSTALLATION_COMPLETED"));
        record.setProjectId(10L);assertThrows(IllegalArgumentException.class,()->provider.inspect(context,"31"));
    }
    @Test void aNewEffectiveInstallationProducesANewAssociationInsteadOfReusingPriorRoundIdentity() {
        var context=new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.AssociationContext(1L,9L,"PROJECT_INSTALLATION","{}");
        assertEquals("31",provider.associationCandidates(context,null,100).getFirst().objectId());
        var next=new InstallationDO();next.setId(32L);next.setTenantId(1L);next.setProjectId(9L);next.setEquipmentId(7L);next.setVersion(1L);
        next.setStatus(2);next.setInstallLocation("新位置");next.setEffectiveFrom(LocalDateTime.now());
        when(records.selectById(32L)).thenReturn(next);when(records.selectProjectAddressRecordsForUpdate(any())).thenReturn(List.of(next,record));
        assertEquals("32",provider.associationCandidates(context,null,100).getFirst().objectId());
        assertFalse(provider.inspect(this.context,"31").completionFacts().get("PROJECT_INSTALLATION_COMPLETED"));
        assertTrue(provider.associationCandidates(context,"32",100).isEmpty());
    }
}
