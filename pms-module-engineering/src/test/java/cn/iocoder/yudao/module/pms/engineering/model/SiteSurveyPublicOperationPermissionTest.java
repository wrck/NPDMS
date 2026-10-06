package cn.iocoder.yudao.module.pms.engineering.model;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessOwnerPermissionPolicy;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.PermissionBusinessAccessGuard;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisBusinessPermissionPolicy;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SiteSurveyPublicOperationPermissionTest {
    final EntityActor actor = new EntityActor(1L,1001L,"ordinary-user");
    final Set<String> grants = new HashSet<>();
    PermissionApi permissions;
    PermissionCommonApi common;
    boolean routeGranted;
    PermissionBusinessAccessGuard guard;
    SiteSurveyBusinessPermissionPolicy surveyPolicy;
    RequirementAnalysisBusinessPermissionPolicy requirementPolicy;
    BusinessModelDescriptor descriptor;
    @BeforeEach void setup() {
        login(actor.tenantId(), actor.userId());
        permissions=mock(PermissionApi.class); common=mock(PermissionCommonApi.class); routeGranted=true;
        when(permissions.hasAnyPermissions(eq(1001L),any(String[].class))).thenAnswer(call ->
                Arrays.stream((String[])call.getRawArguments()[1]).anyMatch(grants::contains));
        when(common.hasAnyPermissions(1001L,"pms:business-model:operate")).thenAnswer(call -> routeGranted);
        surveyPolicy=new SiteSurveyBusinessPermissionPolicy(permissions);
        requirementPolicy=new RequirementAnalysisBusinessPermissionPolicy(permissions);
        @SuppressWarnings("unchecked") ObjectProvider<BusinessOwnerPermissionPolicy> policies=mock(ObjectProvider.class);
        when(policies.stream()).thenAnswer(call -> Stream.of(surveyPolicy,requirementPolicy));
        guard=new PermissionBusinessAccessGuard(common,policies);
        descriptor=new BusinessModelDescriptor("SOL","siteSurvey","SOL_SITE_SURVEY",1,BusinessModelKind.AGGREGATE_ROOT,
                "工勘","pms:sol-site-survey:query",List.of(),List.of(),SiteSurveyBusinessApplicationService.operations(),List.of(),"sol_site_survey");
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }
    private void login(long tenant, long user) {
        TenantContextHolder.setTenantId(tenant);
        var principal = new LoginUser(); principal.setId(user); principal.setTenantId(tenant); principal.setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    @Test void missingOrMismatchedAuthenticatedIdentityDeniesBeforeOwnerPermissionChecks() {
        grants.addAll(Set.of("pms:sol-site-survey:query", "pms:sol-site-survey:create"));
        SecurityContextHolder.clearContext();
        assertIdentityDenied();
        login(1L, 1002L);
        assertIdentityDenied();
        login(2L, 1001L);
        TenantContextHolder.setTenantId(1L);
        assertIdentityDenied();
        login(1L, 1001L);
        TenantContextHolder.setTenantId(2L);
        assertIdentityDenied();
        verifyNoInteractions(permissions, common);
    }
    private void assertIdentityDenied() {
        assertEquals("ACCESS_DENIED", assertThrows(BusinessContractException.class,
                () -> guard.requireReadable(descriptor, actor, "detail")).getErrorCode());
        assertEquals("ACCESS_DENIED", assertThrows(BusinessContractException.class,
                () -> guard.requireWritable(descriptor, actor, "operation:create")).getErrorCode());
    }
    @Test void queryPermissionNeverMakesAllOwnerOperationsExecutable() {
        grants.add("pms:sol-site-survey:query");
        assertDoesNotThrow(() -> guard.requireReadable(descriptor,actor,"detail"));
        descriptor.operations().forEach(op -> assertThrows(BusinessContractException.class,
                () -> guard.requireWritable(descriptor,actor,"operation:"+op.code())));
    }
    @Test void eachOperationReflectsExactOwnerPermissionAndPublicRoutePermission() {
        grants.add("pms:sol-site-survey:create");
        assertDoesNotThrow(() -> guard.requireWritable(descriptor,actor,"operation:create"));
        assertThrows(BusinessContractException.class,()->guard.requireWritable(descriptor,actor,"operation:save"));
        grants.add("pms:sol-site-survey:update");
        for(String code:List.of("save","confirm","reject","archive"))
            assertDoesNotThrow(() -> guard.requireWritable(descriptor,actor,"operation:"+code));
        assertThrows(BusinessContractException.class,()->guard.requireWritable(descriptor,actor,"operation:delete"));
        grants.add("pms:sol-site-survey:delete");
        assertDoesNotThrow(() -> guard.requireWritable(descriptor,actor,"operation:delete"));
        grants.remove("pms:sol-site-survey:update");
        assertThrows(BusinessContractException.class,()->guard.requireWritable(descriptor,actor,"operation:confirm"));
        routeGranted=false;
        assertThrows(BusinessContractException.class,()->guard.requireWritable(descriptor,actor,"operation:create"));
    }
    @Test void requirementManagerReadContractIsPreservedWithoutInventingItsQueryGrant() {
        grants.add("pms:requirement-analysis:manage");
        assertTrue(requirementPolicy.readable(actor));
        for(String code:List.of("create","save","complete","copy")) assertTrue(requirementPolicy.executable(actor,code,1));
        assertFalse(requirementPolicy.executable(actor,"approve",1));
        assertFalse(requirementPolicy.executable(actor,"save",2));
        assertFalse(surveyPolicy.readable(actor));
    }
    @Test void observerAndUnknownOperationsCannotAcquireInteractiveWritePermission() {
        grants.addAll(Set.of("pms:sol-site-survey:query","pms:sol-site-survey:update"));
        assertThrows(BusinessContractException.class,()->guard.requireWritable(descriptor,actor,"operation:approve"));
        assertFalse(surveyPolicy.executable(actor,"save",2));
        var observer=new EntityActor(1L,0L,EntityActor.SYSTEM_OBSERVER);
        assertDoesNotThrow(()->guard.requireReadable(descriptor,observer,"fact"));
        assertThrows(BusinessContractException.class,()->guard.requireWritable(descriptor,observer,"operation:save"));
    }
}
