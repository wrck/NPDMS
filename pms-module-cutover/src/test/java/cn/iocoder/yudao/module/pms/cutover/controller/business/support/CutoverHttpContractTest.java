package cn.iocoder.yudao.module.pms.cutover.controller.business.support;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration;
import cn.iocoder.yudao.module.pms.cutover.controller.business.checklist.CutoverChecklistController;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.CutoverConfigurationController;
import cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo.CutoverTaskResponses;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationService;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CutoverHttpContractTest {
    @Test
    void configurationVersionConflictAndMissingIntentHaveActionableHttpErrors() throws Exception {
        var service = mock(CutoverConfigurationService.class);
        var controller = new CutoverConfigurationController();
        ReflectionTestUtils.setField(controller, "service", service);
        var mvc = standaloneSetup(controller).setControllerAdvice(new CutoverCommandExceptionAdvice()).build();
        mvc.perform(post("/api/v1/pms/cutover-configurations/revisions/91/actions/copy").header("If-Match", "3"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.recoveryAction").value("FIX_REQUEST"));
        verifyNoInteractions(service);
        when(service.copyRevision(any())).thenThrow(new ServiceException(1_012_004_003, "配置版本已变化"));
        mvc.perform(post("/api/v1/pms/cutover-configurations/revisions/91/actions/copy")
                        .header("If-Match", "3").header("Idempotency-Key", "copy-91"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.reasonCode").value("CONFIGURATION_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.data.recoveryAction").value("REFRESH_AGGREGATE"));
    }

    @Test
    void checklistConflictAndUnavailableEvidenceExposeRecoveryWithoutSuccessEnvelope() throws Exception {
        var service = mock(CutoverChecklistApplicationService.class);
        var controller = new CutoverChecklistController(service, mock(CutoverChecklistExportService.class),
                () -> new CutoverChecklistContext(1L, 9L, "http-contract"));
        var mvc = standaloneSetup(controller).setControllerAdvice(new CutoverCommandExceptionAdvice()).build();
        when(service.getView(any())).thenThrow(
                new CutoverChecklistException(CutoverChecklistException.Code.VERSION_CONFLICT, "清单已更新"));
        mvc.perform(get("/api/v1/pms/cutover-tasks/101/checklist"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.reasonCode").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.data.recoveryAction").value("REFRESH_AGGREGATE"));
        doThrow(new CutoverChecklistException(CutoverChecklistException.Code.COLLECTION_FACT_INVALID, "采集事实已更新"))
                .when(service).getView(any());
        mvc.perform(get("/api/v1/pms/cutover-tasks/101/checklist"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.recoveryAction").value("REFRESH_OWNER_FACTS"));
    }

    @Test
    void responseProjectionKeepsWireLongAndEpochTimeAtTheSerializationBoundary() {
        var mapper = JsonMapper.builder().addModule(new YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
        var time = LocalDateTime.of(2026, 10, 14, 12, 0);
        var response = CutoverTaskResponses.ConfigurationChoice.from(new CutoverTaskViews.ConfigurationChoice(
                "DEFAULT", "默认配置", 9_007_199_254_740_993L, 1, time, null));
        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(json.get("revisionId").isTextual()).isTrue();
        assertThat(json.get("revisionId").asText()).isEqualTo("9007199254740993");
        assertThat(json.get("effectiveFrom").asLong()).isEqualTo(time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        assertThat(json.get("effectiveTo").isNull()).isTrue();
    }
}
