package com.dp.plat.lowcode.engine.connector;

import com.dp.plat.lowcode.dto.OpenApiOperation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI/Swagger 文档解析器（缺口5）。
 *
 * <p>由于 Maven 离线无法引入 {@code swagger-parser}，本类使用 Jackson 手工解析
 * OpenAPI JSON 文档，并附带一个<b>有限</b>的 YAML→JSON 转换器以兼容 YAML 格式
 * 的 OpenAPI 文档。</p>
 *
 * <p><b>JSON 解析</b>：优先使用 {@link ObjectMapper} 解析为 {@code Map<String,Object>}，
 * 失败时若内容不以 {@code {} 或 {@code [} 开头则尝试 YAML 转换。</p>
 *
 * <p><b>YAML 兼容</b>：仅支持基础缩进格式（{@code key: value}、嵌套 map、
 * {@code - item} 数组、inline {@code {…}} / {@code […]} flow、注释）。
 * 遇到锚点 {@code &}、别名 {@code *}、标签 {@code !}、块标量 {@code |}/{@code >}、
 * Tab 缩进、合并键 {@code <<} 等复杂特性时抛出
 * "请转换为 JSON" 提示，不尝试解析。</p>
 *
 * <p><b>操作提取</b>：遍历 {@code paths.{path}.{method}}（get/post/put/delete/patch），
 * 提取 operationId（缺失回退 summary）作为操作 name；
 * parameters 中 {@code in=query/path} 归入 params，{@code in=header} 归入 headers；
 * requestBody.content."application/json".schema 作为 bodySchema；
 * responses."200".content."application/json".schema 作为 responseSchema。</p>
 *
 * <p>本端点为<b>纯解析</b>，前端选择操作后通过现有 save 端点保存到
 * {@code LowCodeConnector.config}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiImporter {

    /** OpenAPI 支持的 HTTP 方法（小写，用于 paths 下 method key 匹配） */
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");

    private final ObjectMapper objectMapper;

    /**
     * 解析 OpenAPI 文档内容，提取所有操作。
     *
     * @param content OpenAPI 文档（JSON 或基础 YAML）
     * @return 操作列表（paths 为空或不存在时返回空列表）
     * @throws IllegalArgumentException 内容为空、JSON/YAML 无效、YAML 含复杂特性时抛出
     */
    public List<OpenApiOperation> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("OpenAPI 内容不能为空");
        }
        Map<String, Object> root = parseRoot(content);
        return extractOperations(root);
    }

    // ==================== 根节点解析（JSON 优先，回退 YAML） ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRoot(String content) {
        try {
            Map<String, Object> root = objectMapper.readValue(content,
                    new TypeReference<Map<String, Object>>() {});
            return root != null ? root : new LinkedHashMap<>();
        } catch (Exception jsonEx) {
            String trimmed = content.trim();
            // 明确是 JSON 开头但解析失败 → 直接报错
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                throw new IllegalArgumentException("OpenAPI 内容不是有效的 JSON: " + jsonEx.getMessage(), jsonEx);
            }
            // 否则尝试 YAML→JSON
            log.debug("JSON 解析失败，尝试 YAML 转换: {}", jsonEx.getMessage());
            try {
                Object parsed = parseYaml(content);
                if (!(parsed instanceof Map)) {
                    throw new IllegalArgumentException("OpenAPI 文档顶层必须是对象（Map）结构");
                }
                return (Map<String, Object>) parsed;
            } catch (YamlUnsupportedException e) {
                throw new IllegalArgumentException("YAML 包含不支持的特性（" + e.getMessage()
                        + "），请将文档转换为 JSON 后重试", e);
            }
        }
    }

    // ==================== 操作提取 ====================

    @SuppressWarnings("unchecked")
    private List<OpenApiOperation> extractOperations(Map<String, Object> root) {
        List<OpenApiOperation> operations = new ArrayList<>();
        Object pathsObj = root.get("paths");
        if (!(pathsObj instanceof Map)) {
            return operations;
        }
        Map<String, Object> paths = (Map<String, Object>) pathsObj;
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Object pathItem = pathEntry.getValue();
            if (!(pathItem instanceof Map)) continue;
            Map<String, Object> pathMap = (Map<String, Object>) pathItem;
            // 遍历 pathItem 的 key，过滤出 HTTP 方法（跳过 parameters/summary 等路径级字段）
            // 按文档顺序输出，保持操作清单可预测
            for (Map.Entry<String, Object> methodEntry : pathMap.entrySet()) {
                String method = methodEntry.getKey();
                if (!HTTP_METHODS.contains(method)) continue;
                Object opObj = methodEntry.getValue();
                if (!(opObj instanceof Map)) continue;
                operations.add(buildOperation(method.toUpperCase(), path, (Map<String, Object>) opObj));
            }
        }
        return operations;
    }

    @SuppressWarnings("unchecked")
    private OpenApiOperation buildOperation(String method, String path, Map<String, Object> op) {
        String name = firstNonNullString(op.get("operationId"), op.get("summary"));
        if (name == null || name.isBlank()) {
            name = method + " " + path;
        }

        List<OpenApiOperation.Param> params = new ArrayList<>();
        List<OpenApiOperation.Param> headers = new ArrayList<>();
        Object paramsObj = op.get("parameters");
        if (paramsObj instanceof List<?> paramList) {
            for (Object p : paramList) {
                if (!(p instanceof Map)) continue;
                Map<String, Object> param = (Map<String, Object>) p;
                OpenApiOperation.Param.ParamBuilder pb = OpenApiOperation.Param.builder()
                        .name(asString(param.get("name")))
                        .in(asString(param.get("in")))
                        .required(asBoolean(param.get("required")))
                        .description(asString(param.get("description")));
                Object schema = param.get("schema");
                if (schema instanceof Map) {
                    pb.schema((Map<String, Object>) schema);
                }
                OpenApiOperation.Param built = pb.build();
                if ("header".equalsIgnoreCase(built.getIn())) {
                    headers.add(built);
                } else {
                    // query / path / cookie 等归入 params
                    params.add(built);
                }
            }
        }

        Map<String, Object> bodySchema = extractJsonSchema(op.get("requestBody"));
        Map<String, Object> responseSchema = extractResponseSchema(op.get("responses"));

        return OpenApiOperation.builder()
                .method(method)
                .path(path)
                .name(name)
                .params(params)
                .headers(headers)
                .bodySchema(bodySchema)
                .responseSchema(responseSchema)
                .build();
    }

    /** 从 requestBody 或 response 对象中提取 application/json（或首个 media type）的 schema */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractJsonSchema(Object mediaContainer) {
        if (!(mediaContainer instanceof Map)) return null;
        Map<String, Object> container = (Map<String, Object>) mediaContainer;
        Object contentObj = container.get("content");
        if (!(contentObj instanceof Map)) return null;
        Map<String, Object> content = (Map<String, Object>) contentObj;
        Object media = content.get("application/json");
        if (!(media instanceof Map) && !content.isEmpty()) {
            // 回退：取第一个 media type
            media = content.values().iterator().next();
        }
        if (!(media instanceof Map)) return null;
        Object schema = ((Map<String, Object>) media).get("schema");
        return schema instanceof Map ? (Map<String, Object>) schema : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractResponseSchema(Object responses) {
        if (!(responses instanceof Map)) return null;
        Map<String, Object> resp = (Map<String, Object>) responses;
        Object successResp = resp.get("200");
        if (!(successResp instanceof Map)) {
            // 回退到其他 2XX / default
            for (String code : List.of("201", "202", "204", "2XX", "default")) {
                if (resp.get(code) instanceof Map) {
                    successResp = resp.get(code);
                    break;
                }
            }
        }
        return extractJsonSchema(successResp);
    }

    // ==================== 有限 YAML → JSON 转换 ====================

    /**
     * 解析基础 YAML 为 Java 对象（Map / List / 标量）。
     *
     * <p>支持：缩进嵌套、{@code key: value}、{@code - item} 数组、inline flow
     * {@code {…}}/{@code […]}、{@code #} 注释、引号字符串、布尔/数字/null 字面量。</p>
     *
     * <p>不支持（抛 {@link YamlUnsupportedException}）：Tab 缩进、锚点 {@code &}、
     * 别名 {@code *}、标签 {@code !}、块标量 {@code |}/{@code >}、合并键 {@code <<}、
     * 复杂映射键 {@code ?}。</p>
     */
    private Object parseYaml(String content) {
        List<YamlLine> lines = new ArrayList<>();
        for (String raw : content.split("\n", -1)) {
            String line = stripComment(raw);
            if (line.isBlank()) continue;
            if (line.indexOf('\t') >= 0) {
                throw new YamlUnsupportedException("Tab 缩进");
            }
            int indent = countLeadingSpaces(line);
            String body = line.substring(indent).trim();
            if (body.isEmpty()) continue;
            // 检测复杂特性
            if (body.startsWith("&") || body.startsWith("*") || body.startsWith("!")
                    || body.startsWith("|") || body.startsWith(">") || body.startsWith("<<")
                    || body.startsWith("? ")) {
                throw new YamlUnsupportedException("复杂 YAML 特性: " + body);
            }
            lines.add(new YamlLine(indent, body));
        }
        if (lines.isEmpty()) {
            return new LinkedHashMap<>();
        }
        int[] idx = {0};
        Object result = parseNode(lines, idx, lines.get(0).indent);
        return result;
    }

    private Object parseNode(List<YamlLine> lines, int[] idx, int currentIndent) {
        if (idx[0] >= lines.size()) return new LinkedHashMap<>();
        YamlLine first = lines.get(idx[0]);
        if (first.indent < currentIndent) return new LinkedHashMap<>();
        if (first.body.startsWith("- ")) {
            return parseArray(lines, idx, first.indent);
        }
        return parseMap(lines, idx, first.indent);
    }

    private Map<String, Object> parseMap(List<YamlLine> lines, int[] idx, int currentIndent) {
        Map<String, Object> map = new LinkedHashMap<>();
        while (idx[0] < lines.size()) {
            YamlLine line = lines.get(idx[0]);
            if (line.indent != currentIndent) {
                // 更深缩进由子调用处理；更浅缩进交回上层
                if (line.indent < currentIndent) break;
                idx[0]++;
                continue;
            }
            if (line.body.startsWith("- ")) break;  // 切换到数组
            String body = line.body;
            int colonIdx = findColon(body);
            if (colonIdx < 0) {
                throw new YamlUnsupportedException("无法解析的 YAML 行（缺少 key:value）: " + body);
            }
            String key = unquote(body.substring(0, colonIdx).trim());
            String valuePart = body.substring(colonIdx + 1).trim();
            checkComplexValue(valuePart, body);
            idx[0]++;
            if (valuePart.isEmpty()) {
                map.put(key, parseChild(lines, idx, currentIndent));
            } else {
                map.put(key, parseScalar(valuePart));
            }
        }
        return map;
    }

    private List<Object> parseArray(List<YamlLine> lines, int[] idx, int currentIndent) {
        List<Object> list = new ArrayList<>();
        while (idx[0] < lines.size()) {
            YamlLine line = lines.get(idx[0]);
            if (line.indent != currentIndent) break;
            if (!line.body.startsWith("- ")) break;
            String itemBody = line.body.substring(2).trim();
            checkComplexValue(itemBody, line.body);
            int contentIndent = currentIndent + 2;
            if (itemBody.isEmpty()) {
                idx[0]++;
                list.add(parseChild(lines, idx, currentIndent));
            } else if (itemBody.startsWith("{") || itemBody.startsWith("[")) {
                list.add(parseScalar(itemBody));
                idx[0]++;
            } else {
                int colonIdx = findColon(itemBody);
                if (colonIdx > 0) {
                    // "- key: value" → 数组项为 map，把当前行重写为 contentIndent 的普通行
                    lines.set(idx[0], new YamlLine(contentIndent, itemBody));
                    list.add(parseMap(lines, idx, contentIndent));
                } else {
                    list.add(parseScalar(itemBody));
                    idx[0]++;
                }
            }
        }
        return list;
    }

    /**
     * 解析 key: 后的嵌套子节点（map 或 array），无子节点时返回空 map。
     *
     * <p>支持两种 YAML 嵌套形式：
     * <ul>
     *   <li>更深缩进：{@code key:\n  child: value}</li>
     *   <li>同缩进数组：{@code key:\n- item}（数组项与 key 同缩进，YAML 允许）</li>
     * </ul></p>
     */
    private Object parseChild(List<YamlLine> lines, int[] idx, int parentIndent) {
        if (idx[0] >= lines.size()) return new LinkedHashMap<>();
        YamlLine next = lines.get(idx[0]);
        if (next.indent > parentIndent) {
            return parseNode(lines, idx, next.indent);
        }
        if (next.indent == parentIndent && next.body.startsWith("- ")) {
            return parseArray(lines, idx, parentIndent);
        }
        return new LinkedHashMap<>();
    }

    /**
     * 检测值/数组项中的锚点（{@code &}）、别名（{@code *}）、标签（{@code !}）。
     *
     * <p>这些 YAML 特性出现在值的开头时（如 {@code key: &anchor}、{@code - *alias}），
     * 本解析器不支持，抛 {@link YamlUnsupportedException} 提示转为 JSON。
     * 引号包裹的值（如 {@code key: "a & b"}）不视为锚点。</p>
     */
    private void checkComplexValue(String value, String rawLine) {
        if (value.isEmpty()) return;
        char first = value.charAt(0);
        if (first == '&' || first == '*' || first == '!') {
            throw new YamlUnsupportedException("anchor/alias/tag 不支持: " + rawLine);
        }
    }

    /** 解析标量值（含 inline flow {@code {…}}/{@code […]}、引号字符串、字面量） */
    private Object parseScalar(String value) {
        String v = value.trim();
        if (v.isEmpty()) return "";
        if (v.startsWith("{") || v.startsWith("[")) {
            try {
                return objectMapper.readValue(v, Object.class);
            } catch (Exception e) {
                throw new YamlUnsupportedException("无法解析 inline flow: " + v);
            }
        }
        if ((v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2)
                || (v.startsWith("'") && v.endsWith("'") && v.length() >= 2)) {
            return v.substring(1, v.length() - 1);
        }
        return switch (v) {
            case "true", "True", "TRUE" -> true;
            case "false", "False", "FALSE" -> false;
            case "null", "Null", "~" -> null;
            default -> parseNumberOrString(v);
        };
    }

    private Object parseNumberOrString(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ignored) {
            // fallthrough
        }
        try {
            double d = Double.parseDouble(v);
            if (!Double.isNaN(d) && !Double.isInfinite(d)) return d;
        } catch (NumberFormatException ignored) {
            // fallthrough
        }
        return v;
    }

    /** 找到 key:value 分隔冒号（跳过引号内的冒号，要求冒号后为空格或行尾） */
    private int findColon(String body) {
        boolean inDouble = false, inSingle = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == ':' && !inDouble && !inSingle) {
                if (i == body.length() - 1) return i;
                if (body.charAt(i + 1) == ' ') return i;
            }
        }
        return -1;
    }

    private String unquote(String s) {
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 移除行尾注释（# 前须为空白或行首；引号内 # 不视为注释） */
    private String stripComment(String raw) {
        boolean inDouble = false, inSingle = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '#' && !inDouble && !inSingle) {
                if (i == 0 || Character.isWhitespace(raw.charAt(i - 1))) {
                    return raw.substring(0, i);
                }
            }
        }
        return raw;
    }

    private int countLeadingSpaces(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') n++;
        return n;
    }

    private String firstNonNullString(Object a, Object b) {
        if (a != null && !a.toString().isBlank()) return a.toString();
        if (b != null) return b.toString();
        return null;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private boolean asBoolean(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        return Boolean.parseBoolean(o.toString());
    }

    /** YAML 单行（缩进 + 内容） */
    private record YamlLine(int indent, String body) {}

    /** 不支持的 YAML 特性（用于抛出"请转换为 JSON"提示） */
    private static class YamlUnsupportedException extends RuntimeException {
        YamlUnsupportedException(String message) {
            super(message);
        }
    }
}
