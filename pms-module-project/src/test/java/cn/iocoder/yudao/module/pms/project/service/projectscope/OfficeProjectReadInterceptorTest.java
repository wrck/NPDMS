package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfficeProjectReadInterceptorTest {
    private final OfficeProjectSearchService search = mock(OfficeProjectSearchService.class);
    private final OfficeProjectReadInterceptor interceptor = new OfficeProjectReadInterceptor(search);
    public CommonResult<String> read() { return CommonResult.success("ok"); }
    public byte[] file() { return new byte[0]; }
    public CommonResult<String> generatedFileTicket() { return CommonResult.success("test"); }
    @AfterEach void clear() { OfficeProjectReadContext.clear(); }
    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader(OfficeProjectReadInterceptor.PROJECT_HEADER, "42");
        request.addHeader(OfficeProjectReadInterceptor.NAME_HEADER, URLEncoder.encode("精确名称 + 甲", StandardCharsets.UTF_8));
        return request;
    }
    private HandlerMethod handler(String name) throws Exception { return new HandlerMethod(this, getClass().getMethod(name)); }

    @Test void revalidatesEveryRequestAndAlwaysClearsContextIncludingFailedRequests() throws Exception {
        var scope = new OfficeProjectReadContext.Scope(1L, 9L, 42L);
        var lookup = new OfficeProjectSearchService.Lookup(42L, "精确名称 + 甲", null);
        when(search.validate(lookup)).thenReturn(scope).thenThrow(new ServiceException(403, "revoked"));
        var request = request("GET", "/admin-api/pms/projects/42");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("read")));
        assertEquals(scope, OfficeProjectReadContext.current());
        interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("read"), new RuntimeException());
        assertNull(OfficeProjectReadContext.current());
        assertThrows(ServiceException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("read")));
        assertNull(OfficeProjectReadContext.current());
        verify(search, times(2)).validate(lookup);
    }

    @Test void rejectsWritesDownloadsExportsContentAndAccessTicketsBeforeController() throws Exception {
        for (var method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            assertThrows(ServiceException.class, () -> interceptor.preHandle(request(method, "/pms/projects/42"), new MockHttpServletResponse(), handler("read")));
        }
        for (var path : new String[]{"/pms/training/42/pdf:access-ticket", "/pms/results/7/download", "/pms/exports/7", "/infra/receipts/content", "/infra/file/presigned-url"}) {
            assertThrows(ServiceException.class, () -> interceptor.preHandle(request("GET", path), new MockHttpServletResponse(), handler("read")));
        }
        assertThrows(ServiceException.class, () -> interceptor.preHandle(request("GET", "/pms/file"), new MockHttpServletResponse(), handler("file")));
        assertThrows(ServiceException.class, () -> interceptor.preHandle(request("GET", "/pms/training/42/files/7"), new MockHttpServletResponse(), handler("generatedFileTicket")));
        verifyNoInteractions(search);
    }

    @Test void malformedOrPartialHeadersFailClosedAndNormalRequestsDoNotAcquireScope() throws Exception {
        var request = request("GET", "/pms/projects/42");
        request.removeHeader(OfficeProjectReadInterceptor.PROJECT_HEADER);
        assertThrows(ServiceException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("read")));
        var normal = new MockHttpServletRequest("GET", "/pms/projects/page");
        assertTrue(interceptor.preHandle(normal, new MockHttpServletResponse(), handler("read")));
        assertNull(OfficeProjectReadContext.current());
        verifyNoInteractions(search);
    }
}
