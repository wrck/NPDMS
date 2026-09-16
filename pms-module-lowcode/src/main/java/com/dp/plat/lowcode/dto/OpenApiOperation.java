package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 解析后的单个操作 DTO（缺口5）。
 *
 * <p>由 {@code OpenApiImporter.parse} 从 OpenAPI/Swagger 文档的
 * {@code paths.{path}.{method}} 节点提取，前端选择操作后通过现有 save 端点
 * 保存到 {@code LowCodeConnector.config}。</p>
 *
 * <p><b>字段说明</b>：
 * <ul>
 *   <li>{@code method}：HTTP 方法（GET/POST/PUT/DELETE/PATCH，大写）</li>
 *   <li>{@code path}：URL 路径模板，如 {@code /users/{id}}</li>
 *   <li>{@code name}：操作名，取 {@code operationId}，缺失时回退到 {@code summary}</li>
 *   <li>{@code params}：query/path 参数列表（{@code in=query} 或 {@code in=path}）</li>
 *   <li>{@code headers}：header 参数列表（{@code in=header}）</li>
 *   <li>{@code bodySchema}：请求体 JSON Schema（{@code requestBody.content."application/json".schema}）</li>
 *   <li>{@code responseSchema}：成功响应 JSON Schema（{@code responses."200".content."application/json".schema}）</li>
 * </ul></p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiOperation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** HTTP 方法：GET/POST/PUT/DELETE/PATCH */
    private String method;

    /** URL 路径模板，如 /users/{id} */
    private String path;

    /** 操作名（operationId 优先，缺失时回退到 summary） */
    private String name;

    /** query/path 参数列表 */
    @Builder.Default
    private List<Param> params = new ArrayList<>();

    /** header 参数列表 */
    @Builder.Default
    private List<Param> headers = new ArrayList<>();

    /** 请求体 JSON Schema（无请求体时为 null） */
    private Map<String, Object> bodySchema;

    /** 成功响应 JSON Schema（无 200 响应时为 null） */
    private Map<String, Object> responseSchema;

    /**
     * 单个参数（query/path/header）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Param implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 参数名 */
        private String name;

        /** 参数位置：query / path / header */
        private String in;

        /** 是否必填 */
        private boolean required;

        /** 描述 */
        private String description;

        /** 参数 Schema（type/format 等） */
        private Map<String, Object> schema;
    }
}
