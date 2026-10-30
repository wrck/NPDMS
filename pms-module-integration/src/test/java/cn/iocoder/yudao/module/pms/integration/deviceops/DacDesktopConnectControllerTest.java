package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.module.pms.integration.controller.admin.deviceops.DacDesktopConnectController;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DacDesktopConnectControllerTest {
    @Test void returnsTheEnvelopeConsumedByAxiosAndTheNativeLauncher() throws Exception {
        var service = mock(DacDesktopConnectService.class);
        // Match the production Yudao admin package prefix; native callbacks stay unprefixed.
        var mvc = MockMvcBuilders.standaloneSetup(new DacDesktopConnectController(service), new DacDesktopConnectProofController(service))
                .setCustomHandlerMapping(() -> {
                    var mapping = new org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping();
                    mapping.setPathPrefixes(java.util.Map.of("/admin-api", type -> type.getPackageName().contains(".controller.admin.")));
                    return mapping;
                }).build();
        String id = "a".repeat(32);
        String root = "/api/v1/pms/integration/device-ops/desktop/connections";
        String browser = "/admin-api"+root;
        when(service.begin(any(), isNull())).thenReturn(new DacDesktopConnectService.Started(id, 1, 90));
        when(service.poll(id, null)).thenReturn(new DacDesktopConnectService.Result("pending", null));
        mvc.perform(post(browser).contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\""+id+"\",\"nonceHash\":\""+"b".repeat(64)+"\",\"serverUrl\":\"http://backend\",\"webOrigin\":\"http://web\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requestId").value(id)).andExpect(jsonPath("$.data.expiresInSeconds").value(90));
        mvc.perform(get(browser+"/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("pending"));
        mvc.perform(post(root+"/"+id+"/proof").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\""+"b".repeat(64)+"\",\"status\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").value(true));
        mvc.perform(delete(browser+"/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(get(root+"/"+id)).andExpect(status().isNotFound());
        mvc.perform(post(browser+"/"+id+"/proof").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        verify(service).accept(eq(id), any());
        verify(service).cancel(id, null);
    }
}
