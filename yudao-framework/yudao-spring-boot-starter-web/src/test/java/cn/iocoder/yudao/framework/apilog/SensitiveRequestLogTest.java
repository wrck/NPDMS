package cn.iocoder.yudao.framework.apilog;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SensitiveRequestLogTest {
    static class Controller { @ApiAccessLog(requestEnable = false) public void submit() {} }
    @Test void developmentLoggerDoesNotReadSensitiveBody() throws Exception {
        var request = spy(new MockHttpServletRequest()); request.setContentType("application/json");
        request.setContent("{\"password\":\"synthetic-secret\"}".getBytes());
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(SpringUtils::isProd).thenReturn(false);
            new ApiAccessLogInterceptor().preHandle(request, new MockHttpServletResponse(),
                    new HandlerMethod(new Controller(), Controller.class.getMethod("submit")));
        }
        verify(request, never()).getReader(); verify(request, never()).getInputStream();
    }
    @Test void exceptionEvidenceOmitsSensitiveRequestParameters() throws Exception {
        var request = new MockHttpServletRequest(); request.setContentType("application/json");
        request.setContent("{\"password\":\"synthetic-secret\"}".getBytes()); request.addParameter("commands", "sensitive-command");
        request.setAttribute(ApiAccessLogInterceptor.ATTRIBUTE_HANDLER_METHOD,
                new HandlerMethod(new Controller(), Controller.class.getMethod("submit")));
        var handler = new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));
        var log = new ApiErrorLogCreateReqDTO();
        try (var web = mockStatic(cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils.class)) {
            ReflectionTestUtils.invokeMethod(handler, "buildExceptionLog", log, request, new IllegalStateException("safe message"));
        }
        assertFalse(log.getRequestParams().contains("synthetic-secret"));
        assertFalse(log.getRequestParams().contains("sensitive-command"));
    }
}
