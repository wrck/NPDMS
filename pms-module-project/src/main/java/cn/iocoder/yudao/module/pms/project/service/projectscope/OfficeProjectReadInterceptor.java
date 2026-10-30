package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

/** 独立只读通道：业务条件每次验证，不能用于写入或获得文件内容/访问凭证。 */
@Configuration
@RequiredArgsConstructor
public class OfficeProjectReadInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    public static final String PROJECT_HEADER = "X-Pms-Office-Project";
    public static final String NAME_HEADER = "X-Pms-Office-Name";
    public static final String CONTRACT_HEADER = "X-Pms-Office-Contract";
    private static final Pattern FILE_OPERATION = Pattern.compile("download|export|print|ticket|presign|/content(?:/|$)|/infra/file(?:/|$)");
    private final OfficeProjectSearchService search;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        OfficeProjectReadContext.clear();
        String project = request.getHeader(PROJECT_HEADER);
        if (project == null && request.getHeader(NAME_HEADER) == null && request.getHeader(CONTRACT_HEADER) == null) return true;
        if (!"GET".equals(request.getMethod()) || FILE_OPERATION.matcher(request.getRequestURI().toLowerCase(Locale.ROOT)).find()
                || !(handler instanceof HandlerMethod method)
                || !CommonResult.class.isAssignableFrom(method.getReturnType().getParameterType())
                || FILE_OPERATION.matcher(method.getMethod().getName().toLowerCase(Locale.ROOT)).find()) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        OfficeProjectReadContext.Scope scope;
        try {
            scope = search.validate(new OfficeProjectSearchService.Lookup(Long.valueOf(project),
                    decode(request.getHeader(NAME_HEADER)), decode(request.getHeader(CONTRACT_HEADER))));
        } catch (IllegalArgumentException invalid) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        OfficeProjectReadContext.set(scope);
        response.setHeader("Cache-Control", "no-store");
        return true;
    }

    private String decode(String value) {
        if (value == null) return null;
        if (value.length() > 6144) throw new IllegalArgumentException("OFFICE_CRITERION_TOO_LONG");
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        OfficeProjectReadContext.clear();
    }
}
