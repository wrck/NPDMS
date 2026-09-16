package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.OpenApiOperation;
import com.dp.plat.lowcode.engine.connector.ConnectorResult;
import com.dp.plat.lowcode.engine.connector.OpenApiImporter;
import com.dp.plat.lowcode.entity.LowCodeConnector;
import com.dp.plat.lowcode.service.LowCodeConnectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 低代码连接器 Controller。
 *
 * <p>提供连接器 CRUD + 测试 + 执行接口。写操作需对应权限，并记录操作日志。</p>
 */
@Tag(name = "低代码连接器", description = "LowCode connector APIs")
@RestController
@RequestMapping("/api/lowcode/connector")
@RequiredArgsConstructor
public class LowCodeConnectorController {

    private final LowCodeConnectorService connectorService;
    private final OpenApiImporter openApiImporter;

    @Operation(summary = "连接器列表")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:connector:list')")
    public Result<List<LowCodeConnector>> list() {
        return Result.ok(connectorService.list());
    }

    @Operation(summary = "连接器详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:list')")
    public Result<LowCodeConnector> get(@PathVariable Long id) {
        return Result.ok(connectorService.getById(id));
    }

    @Operation(summary = "保存连接器")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:connector:edit')")
    @OperLog(title = "低代码连接器", businessType = 1)
    public Result<LowCodeConnector> save(@RequestBody LowCodeConnector connector) {
        connectorService.saveOrUpdate(connector);
        return Result.ok(connector);
    }

    @Operation(summary = "删除连接器")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:edit')")
    @OperLog(title = "低代码连接器", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        connectorService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "测试连接器")
    @PostMapping("/{code}/test")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:test')")
    @OperLog(title = "低代码连接器-测试", businessType = 9)
    public Result<ConnectorResult> test(@PathVariable String code) {
        return Result.ok(connectorService.test(code));
    }

    @Operation(summary = "执行连接器")
    @PostMapping("/{code}/execute")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:test')")
    public Result<ConnectorResult> execute(@PathVariable String code,
                                           @RequestBody(required = false) Map<String, Object> params) {
        return Result.ok(connectorService.execute(code, params == null ? Map.of() : params));
    }

    @Operation(summary = "测试单个操作（设计器实时测试，按操作名执行已保存连接器的指定操作）")
    @PostMapping("/{code}/test-operation")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:test')")
    public Result<ConnectorResult> testOperation(@PathVariable String code,
                                                   @RequestBody TestOperationRequest request) {
        return Result.ok(connectorService.testOperation(
                code,
                request.getOperationName(),
                request.getParams()));
    }

    /**
     * 解析 OpenAPI/Swagger 文档，提取操作清单（缺口5）。
     *
     * <p>纯解析端点，不落库。前端选择操作后通过现有 save 端点保存到
     * {@code LowCodeConnector.config}。支持 JSON 与基础 YAML 格式，
     * 复杂 YAML 返回"请转换为 JSON"提示。</p>
     */
    @Operation(summary = "解析 OpenAPI 文档提取操作清单")
    @PostMapping("/parse-openapi")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:edit')")
    public Result<List<OpenApiOperation>> parseOpenApi(@RequestBody ParseOpenApiRequest request) {
        return Result.ok(openApiImporter.parse(request.getContent()));
    }

    /** 测试操作请求体（与前端 TestOperationPayload 对齐） */
    @lombok.Data
    public static class TestOperationRequest {
        /** 操作名（REST: operations 数组中的 name；DB: SQL 模板名） */
        private String operationName;
        /** 执行参数 */
        private Map<String, Object> params;
    }

    /** OpenAPI 解析请求体 */
    @lombok.Data
    public static class ParseOpenApiRequest {
        /** OpenAPI 文档内容（JSON 或基础 YAML） */
        private String content;
    }
}
