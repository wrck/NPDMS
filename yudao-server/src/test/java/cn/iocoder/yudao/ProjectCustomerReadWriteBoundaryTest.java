package cn.iocoder.yudao;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.customer.api.CustomerQueryApiImpl;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery;
import cn.iocoder.yudao.module.pms.customer.controller.admin.contact.ProjectCustomerContactController;
import cn.iocoder.yudao.module.pms.customer.controller.admin.contact.ProjectContactPageReqVO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.CustomerContactMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ContactHistoryDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.security.CustomerScopeSliceDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.ProjectContactPageQuery;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.*;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.security.CustomerScopeSliceMapper;
import cn.iocoder.yudao.module.pms.customer.service.contact.*;
import cn.iocoder.yudao.module.pms.customer.service.customer.CustomerApplicationServiceImpl;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.ProjectCustomerExtensionCommand;
import cn.iocoder.yudao.module.pms.customer.service.location.CustomerLocationReferenceService;
import cn.iocoder.yudao.module.pms.customer.service.query.*;
import cn.iocoder.yudao.module.pms.customer.service.security.*;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.contact.*;
import cn.iocoder.yudao.module.pms.project.api.reference.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.system.api.permission.*;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Assembly-level regression: actual CUS and PROJ services, mocked DAL and Owner scope only. */
class ProjectCustomerReadWriteBoundaryTest {
    private final CustomerMasterMapper customers = mock(CustomerMasterMapper.class);
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final RoleApi roleApi = mock(RoleApi.class);
    private final CustomerScopeSliceMapper slices = mock(CustomerScopeSliceMapper.class);
    private final CustomerContactMasterMapper contactMasters = mock(CustomerContactMasterMapper.class);
    private final ProjectCustomerContactMapper relations = mock(ProjectCustomerContactMapper.class);
    private final ContactHistoryMapper history = mock(ContactHistoryMapper.class);
    private final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    private final CustomerQueryService directory = new CustomerQueryService();
    private final CustomerScopeContextService directoryScopes = new CustomerScopeContextService();
    private final CustomerMasterDO customer = new CustomerMasterDO();
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    private ProjectContactContextApiImpl legacy;
    private CustomerQueryApiImpl customerApi;
    private ProjectCustomerContactService contacts;
    private ProjectCustomerOverviewService overview;
    private ProjectCustomerLocationService locations;
    private CustomerApplicationServiceImpl extensions;
    private ProjectCustomerContactController contactController;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(3L).setTenantId(1L), new MockHttpServletRequest());
        var role = new RoleRespDTO(); role.setId(12L); role.setCode("SERVICE_MANAGER");
        when(permissions.getRoleIdListByUserId(3L)).thenReturn(Set.of(12L));
        when(roleApi.getRoleList(Set.of(12L))).thenReturn(List.of(role));
        when(slices.selectEffective(any())).thenReturn(List.of());
        when(permissions.hasAnyPermissions(3L, "pms:customer:query")).thenReturn(true);
        var resolver = new CustomerScopeResolver(); ReflectionTestUtils.setField(resolver, "scopeSliceMapper", slices);
        ReflectionTestUtils.setField(directoryScopes, "scopeResolver", resolver);
        ReflectionTestUtils.setField(directoryScopes, "permissionApi", permissions);
        ReflectionTestUtils.setField(directoryScopes, "roleApi", roleApi);
        ReflectionTestUtils.setField(directory, "customerMasterMapper", customers);
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L, 2L, Set.of(7L), Set.of()));
        when(scope.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(7L, 2L, Set.of(7L), Set.of()));
        var project = new ProjectMasterDO(); project.setId(7L); project.setTenantId(1L);
        project.setCustomerId(null); project.setCustomerCode("CUS-8"); project.setVersion(2L); project.setLifecycleStatus("ACTIVE");
        when(projects.selectById(7L)).thenReturn(project); when(projects.selectByIdForUpdate(7L)).thenReturn(project);
        var member = new ProjectMemberAssignmentDO();member.setTenantId(1L);member.setProjectId(7L);member.setUserId(3L);
        member.setMemberRole("SERVICE_MANAGER");member.setAssignmentType("PRIMARY");
        member.setStatus("ACTIVE");member.setAuthorizationConfirmed(true);
        when(members.selectAuthorizationParticipantFacts(any())).thenReturn(List.of(member));
        when(members.selectAuthorizationParticipantFactsForUpdate(any())).thenReturn(List.of(member));
        customer.setId(8L); customer.setTenantId(1L); customer.setCode("CUS-8"); customer.setName("项目客户");
        customer.setLifecycleStatus("ENABLED"); customer.setContactPhone("13812345678");
        when(customers.selectById(8L)).thenReturn(customer);
        when(customers.selectByTenantIdAndCode(1L, "CUS-8")).thenReturn(customer);
        customerApi = spy(new CustomerQueryApiImpl(customers, directory, directoryScopes, permissions));
        legacy = new ProjectContactContextApiImpl(projects, members, scope, mock(ProjectAncestorQueryApi.class),
                mock(AuthorizationGrantApi.class), customerApi, mock(ProjectTreeScopeService.class),
                new ProjectManagementQualificationApiImpl(new ProjectParticipantFactApiImpl(projects,members),
                    mock(ProjectRegionalManagerFactApi.class),projects,
                    mock(cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectCurrentStageService.class)));
        var business = new ProjectBusinessContextApiImpl(projects, scope, members);
        var masking = new CustomerContactAccessService(); ReflectionTestUtils.setField(masking, "permissionApi", permissions);
        context.registerBean(CustomerMasterMapper.class, () -> customers);
        context.registerBean(PermissionApi.class, () -> permissions);
        context.registerBean(CustomerQueryApi.class, () -> customerApi);
        context.registerBean(ProjectContactContextApi.class, () -> legacy);
        context.registerBean(ProjectBusinessContextApi.class, () -> business);
        context.registerBean(ProjectScopeApi.class, () -> scope);
        context.registerBean(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class,
                () -> mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class));
        context.registerBean(CustomerContactAccessService.class, () -> masking);
        context.registerBean(CustomerResponseService.class, () -> new CustomerResponseService(new CustomerFieldMaskingService()));
        context.registerBean(ProjectCustomerOverviewService.class);
        context.refresh();
        overview = context.getBean(ProjectCustomerOverviewService.class);
        locations = new ProjectCustomerLocationService(overview, mock(CustomerLocationReferenceService.class), mock(AssetLocationApi.class));
        contacts = new ProjectCustomerContactService(legacy, contactMasters, relations, history, commands,
                directory, directoryScopes, mock(ProjectContactRuleWakeup.class));
        // The contact service is manually assembled, so inject the same actual API as Spring would.
        var projectCustomerField = org.springframework.util.ReflectionUtils.findField(contacts.getClass(), "projectCustomers");
        if (projectCustomerField != null) ReflectionTestUtils.setField(contacts, "projectCustomers", customerApi);
        contactController = new ProjectCustomerContactController(contacts, masking, new CustomerFieldMaskingService(), customerApi);
        extensions = new CustomerApplicationServiceImpl();
        ReflectionTestUtils.setField(extensions, "projectScopes", scope);
        ReflectionTestUtils.setField(extensions, "projectCustomers", legacy);
        ReflectionTestUtils.setField(extensions, "customerMasterMapper", customers);
        ReflectionTestUtils.setField(extensions, "platformCommandExecutionApi", commands);
    }

    @AfterEach void clear() {
        context.close(); SecurityContextHolder.clearContext(); TenantContextHolder.clear();
    }

    @Test void overviewReadsBoundCustomerWithNoCustomerDirectorySlices() {
        assertFalse(directoryScopes.resolve(1L, 3L).all());
        assertTrue(directoryScopes.resolve(1L, 3L).slices().isEmpty());
        assertNull(customerApi.getCustomerByCode(new CustomerCodeQuery("CUS-8", 3L)));
        var result = overview.get(new ProjectCustomerOverviewService.Query(1L, 3L, 7L));
        assertEquals(8L, result.getId()); assertEquals("138****5678", result.getContactPhone());
        noWrites();
    }

    @Test void locationsReadTheSameBoundCustomerWithoutGlobalDirectoryScope() {
        var result = locations.get(new ProjectCustomerLocationService.Query(1L, 3L, 7L, null));
        assertEquals(8L, result.customerId()); assertTrue(result.sites().isEmpty());
        assertTrue(result.addresses().isEmpty()); noWrites();
    }

    @Test void legacyInspectLockExtensionAndContactWritesStillRejectEmptyDirectoryScope() {
        assertThrows(ServiceException.class, () -> legacy.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L)));
        assertThrows(ServiceException.class, () -> legacy.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, 2L)));
        assertThrows(ServiceException.class, () -> extensions.updateProjectExtension(new ProjectCustomerExtensionCommand(1L, 7L, "备注", 0L, "TF018")));
        var actor = new CustomerContactMasterService.Actor(1L, 3L);
        var write = new ProjectContactWrite(7L, 21L, null, 2, 1, null, false, 0, false);
        assertThrows(ServiceException.class, () -> contacts.create(actor, write, "TF018"));
        assertThrows(ServiceException.class, () -> contacts.update(actor, write));
        assertThrows(ServiceException.class, () -> contacts.restore(actor, 7L, 21L, 2, 1, 0));
        assertThrows(ServiceException.class, () -> contacts.associateCustomer(actor, 7L, 2, 8L));
        noWrites();
    }

    @Test void disabledExistingCustomerIsReadableButDeletedCustomerIsRejected() {
        customer.setLifecycleStatus("DISABLED");
        assertEquals("DISABLED", overview.get(new ProjectCustomerOverviewService.Query(1L, 3L, 7L)).getLifecycleStatus());
        customer.setLifecycleStatus("DELETED");
        assertThrows(ServiceException.class, () -> overview.get(new ProjectCustomerOverviewService.Query(1L, 3L, 7L)));
        noWrites();
    }

    @Test void primaryContactPreloadReadsEnabledRowsWithoutDirectoryScopeAndMasksContacts() {
        contactRows();
        var result = contactController.page(7L, contactPage(0)).getData();
        assertEquals(1L, result.getTotal());
        assertEquals("138****5678", result.getList().getFirst().getMobile());
        var query = org.mockito.ArgumentCaptor.forClass(ProjectContactPageQuery.class);
        verify(relations).selectPage(query.capture());
        assertEquals(1L, query.getValue().tenantId()); assertEquals(7L, query.getValue().projectId());
        assertEquals(0, query.getValue().status());
        var response = contactController.context(7L).getData();
        assertEquals(8L, response.project().customerId());
        assertFalse(response.project().canManage()); assertFalse(response.project().canViewHistory());
        assertFalse(response.sensitiveRead()); noWrites();
    }

    @Test void contactReadFallbackDoesNotGrantDisabledRowsHistorySourcesOrWrites() {
        contactRows();
        contactController.page(7L, contactPage(1));
        verify(relations).selectPage(argThat(query -> Integer.valueOf(0).equals(query.status())));
        var actor = new CustomerContactMasterService.Actor(1L, 3L);
        assertThrows(ServiceException.class, () -> contacts.sources(actor, 7L, null, new cn.iocoder.yudao.framework.common.pojo.PageParam()));
        assertThrows(ServiceException.class, () -> contacts.history(actor, 7L, new cn.iocoder.yudao.framework.common.pojo.PageParam()));
        legacyInspectLockExtensionAndContactWritesStillRejectEmptyDirectoryScope();
    }

    @Test void contactReadFallbackCannotBorrowSensitivePermissionForRawFields() {
        when(permissions.hasAnyPermissions(3L, "pms:customer:sensitive-read")).thenReturn(true);
        contactRows();
        assertEquals("138****5678", contactController.page(7L, contactPage(0)).getData().getList().getFirst().getMobile());
        assertFalse(contactController.context(7L).getData().sensitiveRead());
        noWrites();
    }

    @Test void contactReadScopeDenialCannotFallBackToCustomerData() {
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L, 2L, Set.of(), Set.of(7L)));
        assertThrows(ServiceException.class, () -> contactController.page(7L, contactPage(0)));
        verify(customerApi, never()).getProjectCustomer(any());
        verifyNoInteractions(customers, relations); noWrites();
    }

    @Test void contactReadRejectsForgedActorAndUnauthenticatedRequestsBeforeOwnerReads() {
        assertThrows(ServiceException.class, () -> contacts.page(new CustomerContactMasterService.Actor(1L, 99L), 7L, 0, null, contactPage(0)));
        SecurityContextHolder.clearContext();
        assertThrows(ServiceException.class, () -> contactController.page(7L, contactPage(0)));
        verifyNoInteractions(projects, customers, relations);
        verify(scope, never()).resolveCurrent(any()); noWrites();
    }

    @Test void contactReadPreservesDirectoryAuthorizedManagerCapabilitiesAndRawFields() {
        var slice = new CustomerScopeSliceDO();
        slice.setDepartmentMode("ALL"); slice.setMarketMode("ALL"); slice.setSystemMode("ALL");
        slice.setExpendMode("ALL"); slice.setIndustryMode("ALL");
        when(slices.selectEffective(any())).thenReturn(List.of(slice));
        when(customers.selectVisibleById(any())).thenReturn(customer);
        contactRows();
        var response = contactController.context(7L).getData();
        assertTrue(response.project().canManage()); assertTrue(response.project().canViewHistory());
        assertEquals("13812345678", contactController.page(7L, contactPage(0)).getData().getList().getFirst().getMobile());
        verify(customerApi, never()).getProjectCustomer(any()); noWrites();
    }

    @Test void contactReadPreservesStoredIdManagerCapabilitiesWithoutDirectorySlices() {
        projects.selectById(7L).setCustomerId(8L);
        var response = contactController.context(7L).getData();
        assertTrue(response.project().canManage()); assertTrue(response.project().canViewHistory());
        verify(customerApi, never()).getProjectCustomer(any()); noWrites();
    }

    @Test void contactUnavailableCodeMatchesTheProjectOwnerStableError() {
        assertEquals(cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CUSTOMER_UNAVAILABLE.getCode(),
                ProjectContactContextApi.CUSTOMER_UNAVAILABLE);
    }

    @Test void contactPageRejectsAlreadyReadDisabledRowsWhenDirectoryScopeExpires() {
        directoryScopeExpires(); contactRows();
        assertThrows(ServiceException.class, () -> contactController.page(7L, contactPage(1)));
        verify(relations).selectPage(argThat(query -> Integer.valueOf(1).equals(query.status())));
        noWrites();
    }

    @Test void contactSourcesRejectTheResponseWhenDirectoryScopeExpires() {
        directoryScopeExpires();
        var source = new CustomerContactMasterDO(); source.setId(31L); source.setCustomerId(8L); source.setMobile("13812345678");
        when(contactMasters.selectAvailableSourceCount(any())).thenReturn(1L);
        when(contactMasters.selectAvailableSourcePage(any())).thenReturn(List.of(source));
        assertThrows(ServiceException.class, () -> contactController.sources(7L, contactPage(0)));
        verify(contactMasters).selectAvailableSourcePage(any());
        verify(customerApi, never()).getProjectCustomer(any()); noWrites();
    }

    @Test void contactHistoryRejectsTheResponseWhenDirectoryScopeExpires() {
        directoryScopeExpires();
        var entry = new ContactHistoryDO(); entry.setId(41L); entry.setTenantId(1L); entry.setProjectId(7L); entry.setProjectRelationId(21L);
        entry.setActionCode("UPDATE"); entry.setBeforeValues("{\"mobile\":\"13812345678\"}");
        when(history.selectProjectPage(any())).thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(entry), 1L));
        assertThrows(ServiceException.class, () -> contactController.history(7L, contactPage(0)));
        verify(history).selectProjectPage(any());
        verify(customerApi, never()).getProjectCustomer(any()); noWrites();
    }

    @Test void contactPageCannotUpgradeInitialFallbackToRawWhenDirectoryScopeReturns() {
        when(slices.selectEffective(any())).thenReturn(List.of(), List.of(allCustomerSlice()));
        when(customers.selectVisibleById(any())).thenReturn(customer);
        when(permissions.hasAnyPermissions(3L, "pms:customer:sensitive-read")).thenReturn(true);
        contactRows();
        var response = contactController.page(7L, contactPage(0)).getData();
        assertEquals("138****5678", response.getList().getFirst().getMobile());
        verify(relations).selectPage(argThat(query -> Integer.valueOf(0).equals(query.status())));
        noWrites();
    }

    private CustomerScopeSliceDO allCustomerSlice() {
        var slice = new CustomerScopeSliceDO();
        slice.setDepartmentMode("ALL"); slice.setMarketMode("ALL"); slice.setSystemMode("ALL");
        slice.setExpendMode("ALL"); slice.setIndustryMode("ALL");
        return slice;
    }

    private void directoryScopeExpires() {
        when(slices.selectEffective(any())).thenReturn(List.of(allCustomerSlice()), List.of());
        when(customers.selectVisibleById(any())).thenReturn(customer);
    }

    private ProjectContactPageReqVO contactPage(int status) {
        var page = new ProjectContactPageReqVO(); page.setPageNo(1); page.setPageSize(1); page.setStatus(status);
        return page;
    }

    private void contactRows() {
        when(relations.selectPage(any(ProjectContactPageQuery.class))).thenAnswer(invocation -> {
            var row = new ProjectCustomerContactDO(); row.setId(21L); row.setTenantId(1L); row.setProjectId(7L);
            var query = invocation.getArgument(0, ProjectContactPageQuery.class);
            row.setCustomerId(8L); row.setStatus(query.status() == null ? 0 : query.status()); row.setMobile("13812345678"); row.setPrimaryFlag(true);
            return new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(row), 1L);
        });
    }

    private void noWrites() {
        verifyNoInteractions(commands);
        verify(contactMasters, never()).insert(any(CustomerContactMasterDO.class));
        verify(contactMasters, never()).updateById(any(CustomerContactMasterDO.class));
        verify(contactMasters, never()).deleteUnreferenced(any());
        verify(history, never()).insert(any(ContactHistoryDO.class));
        verify(relations, never()).insert(any(ProjectCustomerContactDO.class));
        verify(relations, never()).updateById(any(ProjectCustomerContactDO.class));
        verify(relations, never()).deleteByVersion(any());
        verify(relations, never()).restoreByVersion(any());
        verify(customers, never()).insert(any(CustomerMasterDO.class));
        verify(customers, never()).updateById(any(CustomerMasterDO.class));
        verify(customers, never()).updatePlatformFieldsByVersion(any());
        verify(customers, never()).updateLifecycleByVersion(any());
        verify(projects, never()).associateCustomerIfMissing(any());
        verify(projects, never()).updateById(any(ProjectMasterDO.class));
    }
}
