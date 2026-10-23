package cn.iocoder.yudao;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.authorization.AuthorizationReadScope;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceQueryService;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApiImpl;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApiImpl;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceListAuthorizationReuseTest {
    final UserCompanyDepartmentScopeMapper organizations=mock(UserCompanyDepartmentScopeMapper.class);
    final ProjectTreeScopeService projects=mock(ProjectTreeScopeService.class);
    final DeviceMapper devices=mock(DeviceMapper.class);
    final ContractMapper contracts=mock(ContractMapper.class);
    final OrganizationScopeApiImpl organizationApi=new OrganizationScopeApiImpl();
    final ProjectScopeApiImpl projectApi=new ProjectScopeApiImpl(projects);
    DeviceQueryService service;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        ReflectionTestUtils.setField(organizationApi,"scopeMapper",organizations);
        var commerce=new ContractAccessService(organizationApi,projectApi,contracts,null,null,null,null,null);
        var access=new DeviceAccessScopeService(projectApi,devices,commerce,organizationApi);
        service=new DeviceQueryService(devices,access,null,null);
        var organization=new UserCompanyDepartmentScopeDO();
        organization.setId(1L);organization.setVersion(1);organization.setCompanyId(11L);
        organization.setCompanyCode("C01");organization.setDepartmentId(22L);organization.setDepartmentCode("OFFICE");
        when(organizations.selectActiveListByUserId(any())).thenReturn(List.of(organization));
        when(projects.resolveAllFullProjectIds(any(),any(),any())).thenReturn(Set.of(10L));
        when(devices.selectContractCandidates(any())).thenReturn(Set.of("CONTRACT"));
        when(contracts.selectVisibleDeviceContractNumbers(any())).thenReturn(List.of("CONTRACT","NOT_CANDIDATE"));
        when(devices.selectVisibleDevicePage(any())).thenReturn(PageResult.empty());
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    VisibleDevicePageQuery query() {return VisibleDevicePageQuery.builder().tenantId(1L).pageNo(1).pageSize(10).build();}

    @Test void bothConsumersUseOneOwnerReadPerListAndNextListReloads() {
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            service.getPage(query());
            verify(organizations).selectActiveListByUserId(any());
            verify(projects).resolveAllFullProjectIds(1L,7L,"PROJECT_VIEW");
            var captured=ArgumentCaptor.forClass(VisibleDevicePageQuery.class);
            verify(devices).selectVisibleDevicePage(captured.capture());
            assertEquals(Set.of(10L),captured.getValue().visibleProjectIds());
            assertEquals(Set.of("CONTRACT"),captured.getValue().visibleContractNumbers());
            assertEquals(11L,captured.getValue().organizationGrants().getFirst().companyId());
            var contractQuery=ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.DeviceContractScopeQuery.class);
            verify(contracts).selectVisibleDeviceContractNumbers(contractQuery.capture());
            assertEquals(List.of("C01"),contractQuery.getValue().companyCodes());
            assertEquals(List.of(10L),contractQuery.getValue().projectIds());
            service.getPage(query());
            verify(organizations,times(2)).selectActiveListByUserId(any());
            verify(projects,times(2)).resolveAllFullProjectIds(1L,7L,"PROJECT_VIEW");
        }
    }

    @Test void keysSeparateTenantUserAndActionAndDtoMutationCannotPoisonOtherConsumer() {
        try(var scope=AuthorizationReadScope.open()) {
            var first=organizationApi.getActiveScopes(7L);first.getFirst().setCompanyCode("MUTATED");
            assertEquals("C01",organizationApi.getActiveScopes(7L).getFirst().getCompanyCode());
            organizationApi.getActiveScopes(8L);
            projectApi.resolveAllCurrent(new ProjectAllScopeQuery(1L,7L,"PROJECT_VIEW"));
            projectApi.resolveAllCurrent(new ProjectAllScopeQuery(1L,7L,"PROJECT_VIEW"));
            projectApi.resolveAllCurrent(new ProjectAllScopeQuery(1L,8L,"PROJECT_VIEW"));
            projectApi.resolveAllCurrent(new ProjectAllScopeQuery(1L,7L,"PROJECT_MANAGE"));
            TenantContextHolder.setTenantId(2L);
            organizationApi.getActiveScopes(7L);
            projectApi.resolveAllCurrent(new ProjectAllScopeQuery(2L,7L,"PROJECT_VIEW"));
        }
        verify(organizations,times(3)).selectActiveListByUserId(any());
        verify(projects).resolveAllFullProjectIds(1L,7L,"PROJECT_VIEW");
        verify(projects).resolveAllFullProjectIds(1L,8L,"PROJECT_VIEW");
        verify(projects).resolveAllFullProjectIds(1L,7L,"PROJECT_MANAGE");
        verify(projects).resolveAllFullProjectIds(2L,7L,"PROJECT_VIEW");
    }

    @Test void emptyScopesStayClosedAndReuseEmptyReads() {
        when(organizations.selectActiveListByUserId(any())).thenReturn(List.of());
        when(projects.resolveAllFullProjectIds(any(),any(),any())).thenReturn(Set.of());
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            service.getPage(query());
        }
        verify(organizations).selectActiveListByUserId(any());
        verify(projects).resolveAllFullProjectIds(1L,7L,"PROJECT_VIEW");
        verifyNoInteractions(contracts);
        var captured=ArgumentCaptor.forClass(VisibleDevicePageQuery.class);
        verify(devices).selectVisibleDevicePage(captured.capture());
        assertTrue(captured.getValue().organizationGrants().isEmpty());
        assertTrue(captured.getValue().visibleProjectIds().isEmpty());
        assertTrue(captured.getValue().visibleContractNumbers().isEmpty());
    }

    @Test void failedListDoesNotLeakReadsAndOwnerFailureRetainsRetry() {
        when(devices.selectVisibleDevicePage(any())).thenThrow(new IllegalStateException("page"));
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(IllegalStateException.class,()->service.getPage(query()));
            doReturn(PageResult.empty()).when(devices).selectVisibleDevicePage(any());
            service.getPage(query());
        }
        verify(organizations,times(2)).selectActiveListByUserId(any());
        verify(projects,times(2)).resolveAllFullProjectIds(1L,7L,"PROJECT_VIEW");
        when(projects.resolveAllFullProjectIds(1L,7L,"PROJECT_VIEW"))
                .thenThrow(new IllegalStateException("owner")).thenReturn(Set.of(10L));
        try(var scope=AuthorizationReadScope.open()) {
            var query=new ProjectAllScopeQuery(1L,7L,"PROJECT_VIEW");
            assertThrows(IllegalStateException.class,()->projectApi.resolveAllCurrent(query));
            assertEquals(Set.of(10L),projectApi.resolveAllCurrent(query));
        }
    }
}
