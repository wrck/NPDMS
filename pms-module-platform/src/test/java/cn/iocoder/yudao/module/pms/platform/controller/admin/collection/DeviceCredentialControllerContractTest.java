package cn.iocoder.yudao.module.pms.platform.controller.admin.collection;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.DeviceCredentialApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialDTO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceCredentialControllerContractTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesStableResourceWithoutSecretResponse() throws Exception {
        RequestMapping mapping = DeviceCredentialController.class.getAnnotation(RequestMapping.class);
        assertEquals("/api/v1/pms/device-credentials", mapping.value()[0]);

        Method create = DeviceCredentialController.class.getDeclaredMethod("create", DeviceCredentialCreateReqVO.class);
        assertNotNull(create.getAnnotation(PostMapping.class));
        assertTrue(create.getAnnotation(PreAuthorize.class).value().contains("pms:device-credential:create"));

        Method get = DeviceCredentialController.class.getDeclaredMethod("get", Long.class);
        assertNotNull(get.getAnnotation(GetMapping.class));
        assertTrue(get.getAnnotation(PreAuthorize.class).value().contains("pms:device-credential:query"));

        assertFalse(Arrays.stream(DeviceCredentialDTO.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .anyMatch(name -> name.equals("secret") || name.contains("ciphertext") || name.contains("kmsreference")));
    }

    @Test
    void derivesTenantAndActorFromTrustedContextsAndClearsSecret() {
        DeviceCredentialApi api = mock(DeviceCredentialApi.class);
        DeviceCredentialController controller = new DeviceCredentialController(api);
        DeviceCredentialCreateReqVO request = new DeviceCredentialCreateReqVO();
        request.setCredentialCode("credential-1");
        request.setCredentialType("SSH");
        request.setUsername("operator");
        request.setSecret("secret-value".toCharArray());
        request.setDeviceId("device-1");
        request.setCommandTemplateId("template-1");
        request.setExpiresAt(LocalDateTime.parse("2026-08-29T10:00:00"));
        TenantContextHolder.setTenantId(7L);
        LoginUser user = new LoginUser();
        user.setId(8L);
        user.setTenantId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, java.util.List.of()));
        when(api.create(org.mockito.ArgumentMatchers.any())).thenReturn(
                new DeviceCredentialDTO(1L, "credential-1", "SSH", "operator", "********", 1L, "ACTIVE", 2L));

        controller.create(request);

        ArgumentCaptor<DeviceCredentialCreateCommand> command = ArgumentCaptor.forClass(DeviceCredentialCreateCommand.class);
        verify(api).create(command.capture());
        assertEquals(7L, command.getValue().tenantId());
        assertEquals(8L, command.getValue().actorId());
        assertTrue(allZero(request.getSecret()));
    }

    private static boolean allZero(char[] value) {
        for (char item : value) {
            if (item != '\0') {
                return false;
            }
        }
        return true;
    }
}
