package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationSurveyRespVO;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationSurveyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PRE-02: exact survey GET/PATCH URL, root If-Match and yyyy-MM-dd wire contract. */
class PreparationSurveyControllerTest {
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test
    void patchUsesRootVersionAndReturnsBusinessDateAndNewRootVersion() throws Exception {
        PreparationSurveyService service = mock(PreparationSurveyService.class);
        var controller = new PreparationSurveyController(service, new MockEnvironment());
        var mvc = MockMvcBuilders.standaloneSetup(controller).setMessageConverters(
                new MappingJackson2HttpMessageConverter(new ObjectMapper().registerModule(new JavaTimeModule()))).build();
        login();
        PreparationSurveyRespVO response = new PreparationSurveyRespVO(); response.setPreparationId(2L);
        response.setVersion(5); response.setSurveyDate(LocalDate.of(2026, 9, 8));
        when(service.patch(eq(2L), eq(4), any(), any())).thenReturn(response);
        mvc.perform(patch("/api/v1/pms/preparations/2/survey").header("If-Match", "4")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedProjectVersion\":2,\"surveyDate\":\"2026-09-08\",\"grounding\":\"接地\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(5))
                .andExpect(jsonPath("$.data.surveyDate").value("2026-09-08"));
        verify(service).patch(eq(2L), eq(4), argThat(request -> request.getSurveyDate().equals(LocalDate.of(2026, 9, 8))
                && request.getLocationCommand() == null && request.getSubmittedFields().contains("grounding")),
                argThat(actor -> actor.tenantId().equals(1L) && actor.actorId().equals(7L)));
    }

    @Test
    void getUsesPreparationIdAndMissingIfMatchNeverWrites() throws Exception {
        PreparationSurveyService service = mock(PreparationSurveyService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PreparationSurveyController(service, new MockEnvironment())).build();
        login();
        mvc.perform(get("/api/v1/pms/preparations/2/survey")).andExpect(status().isOk());
        verify(service).get(eq(2L), any());
        mvc.perform(patch("/api/v1/pms/preparations/2/survey").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedProjectVersion\":2}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).patch(any(), any(), any(), any());
    }

    private void login() {
        TenantContextHolder.setTenantId(1L);
        LoginUser user = new LoginUser(); user.setId(7L); user.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
    }
}
