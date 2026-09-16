package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.engine.microflow.MicroflowDebugger;
import com.dp.plat.lowcode.engine.microflow.MicroflowDiagramService;
import com.dp.plat.lowcode.entity.LowCodeMicroflow;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 低代码微流 Controller。
 *
 * <p>提供微流 CRUD 与执行接口。写操作需对应权限，并记录操作日志。</p>
 */
@Tag(name = "低代码微流", description = "LowCode microflow APIs")
@RestController
@RequestMapping("/api/lowcode/microflow")
@RequiredArgsConstructor
public class LowCodeMicroflowController {

    private final LowCodeMicroflowService microflowService;
    private final MicroflowDebugger microflowDebugger;
    private final MicroflowDiagramService diagramService;

    @Operation(summary = "微流列表")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:list')")
    public Result<List<LowCodeMicroflow>> list() {
        return Result.ok(microflowService.list());
    }

    @Operation(summary = "微流详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:list')")
    public Result<LowCodeMicroflow> get(@PathVariable Long id) {
        return Result.ok(microflowService.getById(id));
    }

    @Operation(summary = "保存微流")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:edit')")
    @OperLog(title = "低代码微流", businessType = 1)
    public Result<LowCodeMicroflow> save(@RequestBody LowCodeMicroflow microflow) {
        microflowService.saveOrUpdate(microflow);
        return Result.ok(microflow);
    }

    @Operation(summary = "删除微流")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:edit')")
    @OperLog(title = "低代码微流", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        microflowService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "执行微流")
    @PostMapping("/{code}/execute")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    public Result<Map<String, Object>> execute(@PathVariable String code,
                                               @RequestBody(required = false) Map<String, Object> inputs) {
        return Result.ok(microflowService.execute(code, inputs == null ? Map.of() : inputs));
    }

    // ===================== 微流图渲染（批次3-T6） =====================

    @Operation(summary = "导出微流流程图为 SVG")
    @GetMapping("/{id}/diagram.svg")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:list')")
    public ResponseEntity<byte[]> exportSvg(@PathVariable Long id) {
        LowCodeMicroflow microflow = microflowService.getById(id);
        if (microflow == null) {
            return ResponseEntity.notFound().build();
        }
        String svg = diagramService.renderSvg(microflow.getDefinition());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("image/svg+xml"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"microflow-" + id + ".svg\"");
        return ResponseEntity.ok()
                .headers(headers)
                .body(svg.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "导出微流流程图为 PNG")
    @GetMapping("/{id}/diagram.png")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:list')")
    public ResponseEntity<byte[]> exportPng(@PathVariable Long id) {
        LowCodeMicroflow microflow = microflowService.getById(id);
        if (microflow == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] png = diagramService.renderPng(microflow.getDefinition());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"microflow-" + id + ".png\"");
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(png);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===================== 微流断点调试 =====================

    @Operation(summary = "启动微流调试会话")
    @PostMapping("/{code}/debug/start")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    @OperLog(title = "低代码微流调试", businessType = 0)
    public Result<MicroflowDebugger.DebugSession> startDebug(@PathVariable String code,
                                                              @RequestBody(required = false) DebugStartRequest req) {
        Map<String, Object> inputs = req == null || req.getInputs() == null ? Map.of() : req.getInputs();
        Set<String> breakpoints = req == null || req.getBreakpointNodeIds() == null ? Set.of() : req.getBreakpointNodeIds();
        return Result.ok(microflowDebugger.startSession(code, inputs, breakpoints));
    }

    @Operation(summary = "单步执行（step over）")
    @PostMapping("/debug/{sessionId}/step")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    public Result<MicroflowDebugger.DebugStepResult> stepOver(@PathVariable String sessionId) {
        return Result.ok(microflowDebugger.stepOver(sessionId));
    }

    @Operation(summary = "继续执行到下一断点")
    @PostMapping("/debug/{sessionId}/continue")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    public Result<MicroflowDebugger.DebugStepResult> continueExecution(@PathVariable String sessionId) {
        return Result.ok(microflowDebugger.continueExecution(sessionId));
    }

    @Operation(summary = "查询当前变量状态")
    @GetMapping("/debug/{sessionId}/variables")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    public Result<Map<String, Object>> getVariables(@PathVariable String sessionId) {
        return Result.ok(microflowDebugger.getVariables(sessionId));
    }

    @Operation(summary = "终止微流调试会话")
    @DeleteMapping("/debug/{sessionId}")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    public Result<Void> terminateDebug(@PathVariable String sessionId) {
        microflowDebugger.terminate(sessionId);
        return Result.ok();
    }

    @Operation(summary = "添加断点")
    @PostMapping("/debug/{sessionId}/breakpoints/{nodeId}")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    public Result<Void> addBreakpoint(@PathVariable String sessionId, @PathVariable String nodeId) {
        microflowDebugger.addBreakpoint(sessionId, nodeId);
        return Result.ok();
    }

    @Operation(summary = "移除断点")
    @DeleteMapping("/debug/{sessionId}/breakpoints/{nodeId}")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    public Result<Void> removeBreakpoint(@PathVariable String sessionId, @PathVariable String nodeId) {
        microflowDebugger.removeBreakpoint(sessionId, nodeId);
        return Result.ok();
    }

    /** 微流调试启动请求体 */
    @Data
    public static class DebugStartRequest {
        /** 输入参数 */
        private Map<String, Object> inputs;
        /** 初始断点节点 ID 集合 */
        private Set<String> breakpointNodeIds;
    }
}
