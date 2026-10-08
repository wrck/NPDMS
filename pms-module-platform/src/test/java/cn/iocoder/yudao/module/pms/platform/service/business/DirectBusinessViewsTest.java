package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews;
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
        when(owner.model()).thenReturn(new BusinessModelViews.ModelDetailVO("IT","note","IT_NOTE","Note",null,List.of(),List.of(),List.of()));
        var controller=new Controller();ReflectionTestUtils.setField(controller,"service",owner);
        ObjectProvider<ProjectBusinessController<?,?>> controllers=mock(ObjectProvider.class);
        var proxyFactory=new org.springframework.aop.framework.ProxyFactory(controller);proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((org.aopalliance.intercept.MethodInterceptor)invocation->invocation.proceed());
        var proxied=(Controller)proxyFactory.getProxy();
        when(controllers.orderedStream()).thenAnswer(invocation->Stream.of(proxied));
        var views=new DirectBusinessViews(controllers,mock(BusinessViewAccess.class));
        var registry=new cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewComponentRegistry(views.providers());
        assertEquals("DIRECT_BUSINESS_IT_NOTE",registry.requireComponent("DIRECT_BUSINESS_IT_NOTE","1").componentKey());
        assertEquals("/api/v1/pms/runtime-notes",views.view("IT_NOTE").apiBase());verify(owner).model();
        when(owner.model()).thenThrow(new IllegalStateException("DENIED"));
        assertThrows(IllegalStateException.class,()->views.view("IT_NOTE"));
    }
}
