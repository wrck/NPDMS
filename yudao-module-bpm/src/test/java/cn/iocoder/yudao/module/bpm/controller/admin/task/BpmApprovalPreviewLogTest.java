package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.iocoder.yudao.framework.apilog.core.filter.ApiAccessLogFilter;
import cn.iocoder.yudao.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BpmApprovalPreviewLogTest {
    @Test void previewLogsTheOperationWithoutPersistingFormValuesInQueryParameters() throws Exception {
        var request = new MockHttpServletRequest("GET", "/bpm/process-instance/get-approval-detail");
        WebFrameworkUtils.setLoginUserType(request, UserTypeEnum.ADMIN.getValue());
        request.setAttribute(ApiAccessLogInterceptor.ATTRIBUTE_HANDLER_METHOD, new HandlerMethod(
                new BpmProcessInstanceController(), BpmProcessInstanceController.class.getMethod("getApprovalDetail", BpmApprovalDetailReqVO.class)));
        var filter = new ApiAccessLogFilter(new WebProperties(), "test", mock(ApiAccessLogCommonApi.class));
        var log = new ApiAccessLogCreateReqDTO();
        Boolean enabled = ReflectionTestUtils.invokeMethod(filter, "buildApiAccessLog", log, request,
                LocalDateTime.now(), Map.of("processVariablesStr", "{\"reason\":\"private-form\"}"), null, null);
        assertEquals(Boolean.TRUE, enabled);
        assertEquals(request.getRequestURI(), log.getRequestUrl());
        assertNull(log.getRequestParams());
        assertNull(log.getResponseBody());
    }
}
