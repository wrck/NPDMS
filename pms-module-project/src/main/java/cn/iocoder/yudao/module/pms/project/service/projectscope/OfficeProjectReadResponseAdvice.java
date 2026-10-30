package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/** 旧记录可能带公开文件地址。只读响应不下发下载地址或办理凭证，数据库原件保持不变。 */
@ControllerAdvice
public class OfficeProjectReadResponseAdvice implements ResponseBodyAdvice<Object> {
    private static final Set<String> FILE_LINKS = Set.of("url", "uri", "fileurl", "filepath", "downloadurl", "downloadpath",
            "pdfurl", "accessurl", "presignedurl", "storageurl", "publicurl", "attachmenturl", "attachmentfiles");
    @Override public boolean supports(MethodParameter returnType, Class converterType) {
        return OfficeProjectReadContext.current() != null && CommonResult.class.isAssignableFrom(returnType.getParameterType());
    }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType, Class converterType,
                                            ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof CommonResult<?> result) || OfficeProjectReadContext.current() == null) return body;
        JsonNode data = JsonUtils.getObjectMapper().valueToTree(result.getData());
        redact(data);
        return new CommonResult<JsonNode>().setCode(result.getCode()).setMsg(result.getMsg()).setData(data);
    }
    private void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (var entry : new ArrayList<>(object.properties())) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (FILE_LINKS.contains(key) || key.endsWith("token") || key.endsWith("password") || key.endsWith("secret")) {
                    object.remove(entry.getKey());
                } else redact(entry.getValue());
            }
        } else if (node != null && node.isArray()) node.forEach(this::redact);
    }
}
