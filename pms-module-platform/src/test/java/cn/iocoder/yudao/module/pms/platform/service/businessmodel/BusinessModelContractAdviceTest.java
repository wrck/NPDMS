package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelContractAdvice;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A thin inherited controller outside the old advice package must inherit diagnostics too. */
class BusinessModelContractAdviceTest {
    public static class Note extends BaseProjectBusinessEntity { }
    @RestController @RequestMapping("/test/inherited-diagnostics")
    public static class Controller extends ProjectBusinessController<ProjectBusinessService<Note>,Note> { }
    @SuppressWarnings("unchecked")
    private void rejects(String reason,int status,int code) throws Exception {
        var service=(ProjectBusinessService<Note>)mock(ProjectBusinessService.class);
        when(service.get(1L)).thenThrow(new BusinessContractException(reason,"diagnostic"));
        var controller=new Controller();ReflectionTestUtils.setField(controller,"service",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(
                new GlobalExceptionHandler("inherited-diagnostics",mock(ApiErrorLogCommonApi.class)),
                new BusinessModelContractAdvice()).build();
        mvc.perform(get("/test/inherited-diagnostics/1")).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }
    @Test void staleVersionIsAConflictRatherThanAnUnknownServerFailure() throws Exception {
        rejects("CONCURRENCY_CONFLICT",409,ErrorCodeConstants.BUSINESS_MODEL_CONCURRENCY_CONFLICT.getCode());
    }
    @Test void inheritedPermissionDenialKeepsForbiddenStatus() throws Exception {
        rejects("ACCESS_DENIED",403,ErrorCodeConstants.BUSINESS_MODEL_ACCESS_DENIED.getCode());
    }
    @Test void closedFieldsAreDefinitiveClientRejections() throws Exception {
        rejects("FIELD_NOT_OPEN",400,ErrorCodeConstants.BUSINESS_MODEL_CONTRACT_REJECTED.getCode());
    }
}
