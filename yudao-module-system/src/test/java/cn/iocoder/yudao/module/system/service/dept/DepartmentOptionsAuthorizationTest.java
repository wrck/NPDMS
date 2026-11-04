package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionAnnotationInterceptor;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.dept.DepartmentOptionsController;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DepartmentOptionsReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DepartmentOptionsAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class SecurityConfiguration {
        @Bean DeptMapper mapper() { return mock(DeptMapper.class); }
        @Bean EnabledDepartmentOptionsService service(DeptMapper mapper) {
            var proxy = new ProxyFactory(new EnabledDepartmentOptionsService(mapper));
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(new DataPermissionAnnotationInterceptor());
            return (EnabledDepartmentOptionsService) proxy.getProxy();
        }
        @Bean DepartmentOptionsController controller(EnabledDepartmentOptionsService service) {
            return new DepartmentOptionsController(service);
        }
    }

    @Test void authenticatedUsersNeedNoMenuGrantAndScopeBypassDoesNotLeak() {
        try (var context = new AnnotationConfigApplicationContext(SecurityConfiguration.class)) {
            var mapper = context.getBean(DeptMapper.class);
            var controller = context.getBean(DepartmentOptionsController.class);
            var request = new DepartmentOptionsReqVO();
            TenantContextHolder.setTenantId(1L);
            when(mapper.selectEnabledDepartmentOptions(any())).thenAnswer(call -> {
                assertFalse(DataPermissionContextHolder.get().enable());
                assertEquals(1L, TenantContextHolder.getTenantId());
                var row = new DeptDO();
                row.setId(16L); row.setCode("1601"); row.setName("办事处");
                return List.of(row);
            });
            SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                    "test", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
            assertThrows(AccessDeniedException.class, () -> controller.getOptions(request));
            verifyNoInteractions(mapper);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("user", null, List.of()));
            assertEquals("1601", controller.getOptions(request).getData().getFirst().getCode());
            assertNull(DataPermissionContextHolder.get());
            request.setCodePrefixes(List.of("*"));
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> controller.getOptions(request));
            assertNull(DataPermissionContextHolder.get());
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
            DataPermissionContextHolder.clear();
        }
    }
}
