package cn.iocoder.yudao.module.pms.lowcode.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.dto.OpenApiOperation;
import cn.iocoder.yudao.module.pms.lowcode.engine.connector.ConnectorResult;
import cn.iocoder.yudao.module.pms.lowcode.engine.connector.OpenApiImporter;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeConnector;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeConnectorService;
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
    public CommonResult<List<LowCodeConnector>> list() {
        return CommonResult.success(connectorService.list());
    }

    @Operation(summary = "连接器详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:list')")
    public CommonResult<LowCodeConnector> get(@PathVariable Long id) {
        return CommonResult.success(connectorService.getById(id));
    }

    @Operation(summary = "保存连接器")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:connector:edit')")
    public CommonResult<LowCodeConnector> save(@RequestBody LowCodeConnector connector) {
        connectorService.saveOrUpdate(connector);
        return CommonResult.success(connector);
    }

    @Operation(summary = "删除连接器")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:edit')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        connectorService.removeById(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "测试连接器")
    @PostMapping("/{code}/test")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:test')")
    public CommonResult<ConnectorResult> test(@PathVariable String code) {
        return CommonResult.success(connectorService.test(code));
    }

    @Operation(summary = "执行连接器")
    @PostMapping("/{code}/execute")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:test')")
    public CommonResult<ConnectorResult> execute(@PathVariable String code,
                                           @RequestBody(required = false) Map<String, Object> params) {
        return CommonResult.success(connectorService.execute(code, params == null ? Map.of() : params));
    }

    @Operation(summary = "测试单个操作（设计器实时测试，按操作名执行已保存连接器的指定操作）")
    @PostMapping("/{code}/test-operation")
    @PreAuthorize("@ss.hasPermission('lowcode:connector:test')")
    public CommonResult<ConnectorResult> testOperation(@PathVariable String code,
                                                   @RequestBody TestOperationRequest request) {
        return CommonResult.success(connectorService.testOperation(
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
    public CommonResult<List<OpenApiOperation>> parseOpenApi(@RequestBody ParseOpenApiRequest request) {
        return CommonResult.success(openApiImporter.parse(request.getContent()));
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
