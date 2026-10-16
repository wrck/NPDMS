package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectBusinessViewsTest {
    static class Note extends BaseProjectBusinessEntity { }
    @RequestMapping("/api/v1/pms/runtime-notes")
    static class Controller extends ProjectBusinessController<ProjectBusinessService<Note>,Note> { }
    @Test void emptyInheritedControllerAutomaticallyProvidesAViewAndPreservesOwnerPermission() {
        var owner=mock(ProjectBusinessService.class);when(owner.definition()).thenReturn(DirectBusinessRuntimeServiceTest.model());
        when(owner.readableDefinition()).thenReturn(DirectBusinessRuntimeServiceTest.model());
        var controller=new Controller();ReflectionTestUtils.setField(controller,"service",owner);
        ObjectProvider<ProjectBusinessController<?,?>> controllers=mock(ObjectProvider.class);
        var proxyFactory=new org.springframework.aop.framework.ProxyFactory(controller);proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((org.aopalliance.intercept.MethodInterceptor)invocation->invocation.proceed());
        var proxied=(Controller)proxyFactory.getProxy();
        when(controllers.orderedStream()).thenAnswer(invocation->Stream.of(proxied));
        var views=new DirectBusinessViews(controllers,mock(BusinessViewAccess.class));
        var registry=new cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewComponentRegistry(views.providers());
        assertEquals("DIRECT_BUSINESS_IT_NOTE",registry.requireComponent("DIRECT_BUSINESS_IT_NOTE","1").componentKey());
        assertEquals("/api/v1/pms/runtime-notes",views.view("IT_NOTE").apiBase());verify(owner).readableDefinition();verify(owner,never()).model();
        when(owner.readableDefinition()).thenThrow(new IllegalStateException("DENIED"));
        assertThrows(IllegalStateException.class,()->views.view("IT_NOTE"));
    }

    @Test void anyInheritedControllerOpensItsOwnPageWithFrozenConfigurationAndTrustedCaller() {
        var owner=mock(ProjectBusinessService.class);when(owner.definition()).thenReturn(DirectBusinessRuntimeServiceTest.model());
        var controller=new Controller();ReflectionTestUtils.setField(controller,"service",owner);
        ObjectProvider<ProjectBusinessController<?,?>> controllers=mock(ObjectProvider.class);
        when(controllers.orderedStream()).thenAnswer(invocation->Stream.of(controller));
        var views=new DirectBusinessViews(controllers,mock(BusinessViewAccess.class));
        var type=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Type("IT","note");
        var context=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext(
                1L,9L,20L,type,"{\"configurationName\":\"delivery\",\"kind\":\"FINAL\"}");
        var request=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessPageQueryApi.Request(20L,11L,"delivery",context.bindingParameters());
        var inspection=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserInspection(Set.of("QUERY"),List.of());
        when(owner.initializePage(request,List.of(11L))).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessPageQueryApi.Read(null,inspection));
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        var authentication=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(9L),null,List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            var result=views.open(context,List.of(11L));
            assertEquals("/api/v1/pms/runtime-notes",result.view().apiBase());assertSame(inspection,result.inspection());
            verify(owner).initializePage(request,List.of(11L));verify(owner,never()).readableDefinition();
            clearInvocations(owner);
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(2L);
            assertThrows(IllegalArgumentException.class,()->views.open(context,List.of(11L)));
            verifyNoInteractions(owner);
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test void configurationPinsDeployedRouteAndContractAcrossSpringProxyInstances() {
        var owner=mock(ProjectBusinessService.class);
        when(owner.definition()).thenReturn(DirectBusinessRuntimeServiceTest.model());
        var controller=new Controller();ReflectionTestUtils.setField(controller,"service",owner);
        ObjectProvider<ProjectBusinessController<?,?>> controllers=mock(ObjectProvider.class);
        when(controllers.orderedStream()).thenAnswer(invocation->Stream.of(controller));
        var access=mock(BusinessViewAccess.class);
        var context=new cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.Context(1L,9L);
        when(access.context()).thenReturn(context);
        var provider=new DirectBusinessViews(controllers,access).providers().getFirst();
        var factory=new org.springframework.aop.framework.ProxyFactory(owner);
        factory.addAdvice((org.aopalliance.intercept.MethodInterceptor)invocation->invocation.proceed());
        ReflectionTestUtils.setField(controller,"service",factory.getProxy());
        assertDoesNotThrow(()->provider.validateConfiguration(context,null,
                cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.ValidationMode.INSPECT));
        var anotherOwner=mock(ProjectBusinessService.class);
        when(anotherOwner.definition()).thenReturn(DirectBusinessRuntimeServiceTest.model());
        ReflectionTestUtils.setField(controller,"service",anotherOwner);
        assertDoesNotThrow(()->provider.validateConfiguration(context,null,
                cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.ValidationMode.INSPECT));
        var model=DirectBusinessRuntimeServiceTest.model();
        when(anotherOwner.definition()).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor(
                "OTHER",model.entityType(),model.stableCode(),model.contractVersion(),model.kind(),model.title(),
                model.authorizationPolicyRef(),model.fields(),model.relations(),model.operations(),model.capabilities(),model.viewCode(),model.scopeBinding()));
        assertThrows(IllegalArgumentException.class,()->provider.validateConfiguration(context,null,
                cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.ValidationMode.INSPECT));
    }
}
