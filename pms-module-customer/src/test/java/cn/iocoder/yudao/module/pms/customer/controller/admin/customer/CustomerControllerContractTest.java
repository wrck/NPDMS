package cn.iocoder.yudao.module.pms.customer.controller.admin.customer;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.util.ReflectionTestUtils.invokeMethod;

class CustomerControllerContractTest {

    @Test
    void usesUniqueBeanNameAlongsideLegacyController() {
        RestController controller = CustomerController.class.getAnnotation(RestController.class);
        assertEquals("pmsCustomerController", controller.value());
    }

    @Test
    void resolvesTenantFromAuthenticatedUserWhenTenantContextIsDisabled() {
        LoginUser user = new LoginUser();
        user.setId(1L);
        user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, java.util.List.of()));
        try {
            assertEquals(Long.valueOf(1L), invokeMethod(new CustomerController(), "tenantId"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void exposesPluralCustomerResourceWithStablePermissions() throws Exception {
        RequestMapping mapping = CustomerController.class.getAnnotation(RequestMapping.class);
        assertEquals("/pms/customers", mapping.value()[0]);

        assertEndpoint("page", GetMapping.class, "pms:customer:query",
                cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerPageReqVO.class);
        assertEndpoint("get", GetMapping.class, "pms:customer:query", Long.class);
        ParameterizedType returnType = (ParameterizedType) CustomerController.class
                .getDeclaredMethod("get", Long.class)
                .getGenericReturnType();
        assertEquals(
                cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerDetailRespVO.class,
                returnType.getActualTypeArguments()[0]);
        assertEndpoint("create", PostMapping.class, "pms:customer:create",
                cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerCreateReqVO.class,
                String.class);
        assertEndpoint("update", PutMapping.class, "pms:customer:update", Long.class,
                cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerUpdateReqVO.class,
                Long.class, String.class);
        assertEndpoint("disable", PostMapping.class, "pms:customer:disable", Long.class,
                cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerLifecycleReqVO.class,
                Long.class, String.class);
        assertEndpoint("delete", PostMapping.class, "pms:customer:delete", Long.class,
                cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerLifecycleReqVO.class,
                Long.class, String.class);
        assertEndpoint("restore", PostMapping.class, "pms:customer:restore", Long.class,
                cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerLifecycleReqVO.class,
                Long.class, String.class);
    }

    @Test
    void commandHeadersUseIdempotencyKeyAndIfMatch() throws Exception {
        Method create = CustomerController.class.getDeclaredMethod("create",
                cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerCreateReqVO.class,
                String.class);
        assertHeader(create, 1, "Idempotency-Key");

        Method update = CustomerController.class.getDeclaredMethod("update", Long.class,
                cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerUpdateReqVO.class,
                Long.class, String.class);
        assertHeader(update, 2, "If-Match");
        assertHeader(update, 3, "Idempotency-Key");
    }

    private void assertEndpoint(String name, Class<?> mappingType, String permission, Class<?>... parameterTypes)
            throws Exception {
        Method method = CustomerController.class.getDeclaredMethod(name, parameterTypes);
        assertNotNull(method.getAnnotation((Class) mappingType));
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertTrue(authorization.value().contains(permission));
    }

    private void assertHeader(Method method, int parameterIndex, String name) {
        RequestHeader header = method.getParameters()[parameterIndex].getAnnotation(RequestHeader.class);
        assertNotNull(header);
        assertEquals(name, header.value());
    }
}
