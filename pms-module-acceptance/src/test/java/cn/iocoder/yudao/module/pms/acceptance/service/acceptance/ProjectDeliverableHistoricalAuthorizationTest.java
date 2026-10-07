package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementIdentityApi;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class ProjectDeliverableHistoricalAuthorizationTest {
    ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    PermissionApi permissions=mock(PermissionApi.class);
    PlatformDeliveryRequirementIdentityApi platform=mock(PlatformDeliveryRequirementIdentityApi.class);
    ProjectDeliverableRuleApi rules=mock(ProjectDeliverableRuleApi.class);
    ProjectDeliverableAccess access=new ProjectDeliverableAccess(scopes,permissions);
    ProjectDeliverableUploadPolicyValidator validator=new ProjectDeliverableUploadPolicyValidator(platform,rules,access);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        lenient().when(permissions.hasAnyPermissions(7L,"pms:project:query")).thenReturn(true);
    }
    @AfterEach void clear() {TenantContextHolder.clear();}
    @Test void ownerListDoesNotResolveFirstHistoricalPlanCode() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        assertEquals(5L,validator.requireDeliveryAccess(1L,7L,"project_deliverable","42",null,false,false,null));
        verifyNoInteractions(platform,rules);
    }
    @Test void retiredCodeHistoryUsesRealAccProjectReadAuthority() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        assertEquals(5L,validator.requireDeliveryAccess(1L,7L,"project_deliverable","42","~plan:99",false,false,null));
        verify(scopes).resolveCurrent(new ProjectCurrentScopeQuery(1L,7L,42L,ProjectScopeApi.ACTION_VIEW));
        verifyNoInteractions(platform,rules);
    }
    @Test void historicalCodeDoesNotGrantForeignProjectVisibility() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(),Set.of(42L)));
        assertThrows(RuntimeException.class,()->validator.requireDeliveryAccess(1L,7L,"project_deliverable","42","~plan:99",false,false,null));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"READ","DOWNLOAD","PREVIEW"})
    void retiredPlanFileReadsUseOwnerProjectScope(String action) {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        var fact=validator.validateUpload(1L,7L,"project_deliverable","42","~plan:99",action,false,null);
        assertNotNull(fact);
        verify(scopes).resolveCurrent(new ProjectCurrentScopeQuery(1L,7L,42L,ProjectScopeApi.ACTION_VIEW));
        verifyNoInteractions(platform,rules);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"READ","DOWNLOAD","PREVIEW"})
    void retiredPlanFileReadsRejectForeignOwnerScope(String action) {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(),Set.of(42L)));
        assertThrows(RuntimeException.class,()->validator.validateUpload(1L,7L,"project_deliverable","42","~plan:99",action,false,null));
        verifyNoInteractions(platform,rules);
    }
    @Test void missingCurrentIdentityDeniesBeforeProjectAuthorization() {
        when(rules.read(42L,"D1")).thenReturn(new ProjectDeliverableRuleApi.Context(42L,8L,"ACTIVE",7L,"S1","T1",null));
        when(platform.containsTemplateIdentity(42L,"D1")).thenReturn(false);
        assertThrows(RuntimeException.class,()->validator.requireDeliveryAccess(1L,7L,"project_deliverable","42","D1",true,false,null));
        verifyNoInteractions(scopes);verify(platform).containsTemplateIdentity(42L,"D1");
    }
    @Test void lockedStagedIdentityDeniesUsingTheSameCurrentCodeLock() {
        when(rules.lock(42L,"D1")).thenReturn(new ProjectDeliverableRuleApi.Context(42L,8L,"ACTIVE",7L,"S1","T1",null));
        when(platform.lockTemplateIdentity(42L,"D1")).thenReturn(false);
        assertThrows(RuntimeException.class,()->validator.requireDeliveryAccess(1L,7L,"project_deliverable","42","D1",true,true,null));
        verifyNoInteractions(scopes);verify(platform).lockTemplateIdentity(42L,"D1");verify(platform,never()).containsTemplateIdentity(any(),any());
    }
}
