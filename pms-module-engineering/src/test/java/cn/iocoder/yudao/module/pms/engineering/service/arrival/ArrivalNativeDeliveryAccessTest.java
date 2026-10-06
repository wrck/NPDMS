package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArrivalNativeDeliveryAccessTest {
    ArrivalMapper rows = mock(ArrivalMapper.class);
    PermissionApi permissions = mock(PermissionApi.class);
    ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    ProjectAcceptanceContextApi projects = mock(ProjectAcceptanceContextApi.class);
    ArrivalNativeDeliveryAccess access = new ArrivalNativeDeliveryAccess(rows, permissions, scopes, projects);
    ArrivalDO row;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(17L).setTenantId(7L), new MockHttpServletRequest());
        row = new ArrivalDO(); row.setId(9L); row.setTenantId(7L); row.setProjectId(20L); row.setStatus(0);
        when(rows.selectById(9L)).thenReturn(row); when(rows.selectDeliveryOwnerForUpdate(any())).thenReturn(row);
        when(permissions.hasAnyPermissions(eq(17L), any(String[].class))).thenReturn(true);
        var scope = new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope); when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        var project = new ProjectAcceptanceContextApi.Context(20L, 20L, 0L, 3L, "ACTIVE");
        when(projects.inspect(any())).thenReturn(project); when(projects.lock(any(), any(), any())).thenReturn(project);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    Long require(boolean write, boolean lock) {
        return access.requireDeliveryAccess(7L, 17L, "arrival", "9", ArrivalDocumentSources.SOURCE_CODE, write, lock, 3L);
    }
    @Test void withdrawalUsesActualOwnerUpdateAndManagedScope() {
        assertEquals(3L, require(true, true));
        verify(permissions).hasAnyPermissions(17L, "pms:imp-arrival:update");
        verify(scopes).resolveCurrent(argThat(query -> ProjectScopeApi.ACTION_MANAGE.equals(query.actionCode())));
        verify(projects).lock(any(), eq(0L), eq(3L));
        assertFalse(access.supportsEntityType("training"));
    }
    @Test void signedArrivalRemainsReadableButRejectsWithdrawal() {
        row.setStatus(1); assertEquals(3L, require(false, false));
        assertThrows(BusinessContractException.class, () -> require(true, true));
    }
    @Test void queryPermissionCannotAuthorizeWithdrawal() {
        when(permissions.hasAnyPermissions(17L, "pms:imp-arrival:update")).thenReturn(false);
        assertThrows(BusinessContractException.class, () -> require(true, true));
        assertEquals(3L, require(false, false));
    }
    @Test void placeholderAndChangedScopeRejectUnderLock() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of(20L)));
        assertThrows(BusinessContractException.class, () -> require(true, true));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L, 4L, Set.of(20L), Set.of()));
        assertThrows(BusinessContractException.class, () -> require(true, true));
    }
    @Test void inactiveProjectAndWrongPurposeRejectWithdrawal() {
        when(projects.lock(any(), any(), any())).thenReturn(new ProjectAcceptanceContextApi.Context(20L,20L,0L,3L,"ARCHIVED"));
        assertThrows(BusinessContractException.class, () -> require(true, true));
        assertThrows(BusinessContractException.class, () -> access.requireDeliveryAccess(7L,17L,"arrival","9","RECEIPT",true,true,3L));
    }
    @Test void actualPrincipalAndTenantAreRequired() {
        assertThrows(BusinessContractException.class, () -> access.requireDeliveryAccess(8L,17L,"arrival","9",ArrivalDocumentSources.SOURCE_CODE,true,true,3L));
        assertThrows(BusinessContractException.class, () -> access.requireDeliveryAccess(7L,18L,"arrival","9",ArrivalDocumentSources.SOURCE_CODE,true,true,3L));
        row.setTenantId(8L); assertThrows(BusinessContractException.class, () -> require(true,true));
    }
    @Test void genericFileRouteCannotIntroduceASecondArrivalSlot() {
        assertThrows(BusinessContractException.class, () -> access.validateUpload(7L,17L,"arrival","9",ArrivalDocumentSources.SOURCE_CODE,"UPLOAD",true,3L));
    }
}
