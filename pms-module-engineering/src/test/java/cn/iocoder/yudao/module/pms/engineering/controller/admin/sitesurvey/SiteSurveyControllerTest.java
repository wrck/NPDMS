package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.SiteSurveyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FR-ENG-001：原API路径、必需If-Match与原字段JSON绑定。独立MVC测试不替代运行时权限验收。 */
class SiteSurveyControllerTest {
    private SiteSurveyController controller;
    private SiteSurveyService service;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        controller = new SiteSurveyController(); service = mock(SiteSurveyService.class);
        ReflectionTestUtils.setField(controller, "siteSurveyService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setMessageConverters(new MappingJackson2HttpMessageConverter(
                new ObjectMapper().registerModule(new JavaTimeModule()))).build();
    }

    @ParameterizedTest @ValueSource(strings = {"delete", "confirm", "reject", "archive"})
    void missingIfMatchNeverReachesService(String action) throws Exception {
        mvc.perform(("delete".equals(action) ? delete("/pms/eng-site-survey/" + action)
                : put("/pms/eng-site-survey/" + action)).param("id", "101")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"4", "\"4\""})
    void forwardsEveryActionVersion(String version) throws Exception {
        mvc.perform(delete("/pms/eng-site-survey/delete").param("id", "101").header("If-Match", version))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        for (String action : new String[]{"confirm", "reject", "archive"}) {
            mvc.perform(put("/pms/eng-site-survey/" + action).param("id", "101").header("If-Match", version))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        }
        verify(service).deleteSiteSurvey(101L, 4); verify(service).confirmSiteSurvey(101L, 4);
        verify(service).rejectSiteSurvey(101L, 4); verify(service).archiveSiteSurvey(101L, 4);
    }

    @ParameterizedTest @ValueSource(strings = {"", "*", "-1", "W/\"4\"", "4,5", "abc", "2147483648", "\"4", "4\""})
    void malformedVersionNeverBecomesUnversionedWrite(String value) {
        assertThrows(ServiceException.class, () -> controller.deleteSiteSurvey(101L, value));
        assertThrows(ServiceException.class, () -> controller.confirmSiteSurvey(101L, value));
        assertThrows(ServiceException.class, () -> controller.rejectSiteSurvey(101L, value));
        assertThrows(ServiceException.class, () -> controller.archiveSiteSurvey(101L, value));
        verifyNoInteractions(service);
    }

    @Test void updateBindsAllOriginalFieldsAndBodyVersion() throws Exception {
        mvc.perform(put("/pms/eng-site-survey/update").contentType(MediaType.APPLICATION_JSON).content("""
                {"id":101,"projectId":10,"code":"SUR-1","name":"工勘","version":4,
                 "surveyDate":"2026-09-08","surveyorUserId":7,"location":"核心机房",
                 "powerSupply":"供电","cabinet":"机柜","networkPort":"网口","fiber":"光纤",
                 "module":"模块","cable":"线缆","ground":"接地","constructionResource":"施工资源",
                 "conclusion":"结论","remark":"备注"}
                """)).andExpect(status().isOk());
        verify(service).updateSiteSurvey(argThat(request -> request.getId() == 101L && request.getProjectId() == 10L
                && request.getVersion() == 4 && request.getSurveyDate().equals(LocalDate.of(2026, 9, 8))
                && request.getSurveyorUserId() == 7L && "供电".equals(request.getPowerSupply())
                && "机柜".equals(request.getCabinet()) && "网口".equals(request.getNetworkPort())
                && "光纤".equals(request.getFiber()) && "模块".equals(request.getModule())
                && "线缆".equals(request.getCable()) && "接地".equals(request.getGround())
                && "施工资源".equals(request.getConstructionResource()) && "结论".equals(request.getConclusion())
                && "备注".equals(request.getRemark()) && request.getStatus() == null));
    }
}
