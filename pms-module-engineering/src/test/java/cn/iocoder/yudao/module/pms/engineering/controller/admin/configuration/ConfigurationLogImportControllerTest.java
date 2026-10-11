package cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration;

import cn.iocoder.yudao.module.pms.engineering.service.configuration.ConfigurationLogImportService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConfigurationLogImportControllerTest {
    final ConfigurationLogImportService imports=mock(ConfigurationLogImportService.class);
    final ConfigurationLogImportController controller=new ConfigurationLogImportController(imports);
    final org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).build();
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(byte[] content) {
        return post("/api/v1/pms/configuration-log-imports").param("projectId","21")
                .param("operationId","request").param("fileName","log.txt")
                .contentType("application/octet-stream").content(content);
    }
    @Test void missingSerialDisplaysOwnerReasonInsteadOfGenericServerError() throws Exception {
        when(imports.importLog(any(),any(),any(),any())).thenThrow(new BusinessContractException("CONFIGURATION_LOG_IMPORT_REJECTED","日志块缺少设备序列号（第1块）"));
        mvc.perform(request(new byte[]{1})).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("日志块缺少设备序列号（第1块）"));
    }
    @Test void mismatchedOwnershipDisplaysRejectionAndNeverSuccess() throws Exception {
        when(imports.importLog(any(),any(),any(),any())).thenThrow(new BusinessContractException("CONFIGURATION_LOG_IMPORT_REJECTED","设备不属于当前项目"));
        mvc.perform(request(new byte[]{1})).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("设备不属于当前项目"));
    }
    @Test void emptyFileIsRejectedBeforeImport() throws Exception {
        mvc.perform(request(new byte[0])).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(imports);
    }
    @Test void oversizedFileIsRejectedBeforeImport() throws Exception {
        mvc.perform(request(new byte[20*1024*1024+1])).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(imports);
    }
}
