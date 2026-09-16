package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.engine.trigger.LowCodeTrigger;
import com.dp.plat.lowcode.entity.LowCodeTriggerExecutionLog;
import com.dp.plat.lowcode.service.LowCodeTriggerExecutionLogService;
import com.dp.plat.lowcode.service.LowCodeTriggerService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 低代码触发器 Controller。
 *
 * <p>提供触发器 CRUD 与手动执行接口。写操作需对应权限，并记录操作日志。</p>
 */
@Tag(name = "低代码触发器", description = "LowCode trigger APIs")
@RestController
@RequestMapping("/api/lowcode/trigger")
@RequiredArgsConstructor
public class LowCodeTriggerController {

    private final LowCodeTriggerService triggerService;
    private final LowCodeTriggerExecutionLogService executionLogService;

    @Operation(summary = "触发器列表")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:trigger:list')")
    public Result<List<LowCodeTrigger>> list() {
        return Result.ok(triggerService.list());
    }

    @Operation(summary = "触发器详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:trigger:list')")
    public Result<LowCodeTrigger> get(@PathVariable Long id) {
        return Result.ok(triggerService.getById(id));
    }

    @Operation(summary = "保存触发器")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:trigger:edit')")
    @OperLog(title = "低代码触发器", businessType = 1)
    public Result<LowCodeTrigger> save(@RequestBody LowCodeTrigger trigger) {
        triggerService.saveOrUpdate(trigger);
        return Result.ok(trigger);
    }

    @Operation(summary = "删除触发器")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:trigger:edit')")
    @OperLog(title = "低代码触发器", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        triggerService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "手动执行触发器")
    @PostMapping("/{code}/execute")
    @PreAuthorize("@ss.hasPermission('lowcode:trigger:edit')")
    public Result<Map<String, Object>> execute(@PathVariable String code,
                                               @RequestBody(required = false) Map<String, Object> data) {
        return Result.ok(triggerService.executeTrigger(code, data == null ? Map.of() : data));
    }

    @Operation(summary = "查询指定触发器的执行历史")
    @GetMapping("/{id}/execution-logs")
    @PreAuthorize("@ss.hasPermission('lowcode:trigger:list')")
    public Result<List<LowCodeTriggerExecutionLog>> getExecutionLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(executionLogService.listByTriggerId(id, limit));
    }

    /**
     * 查询全局最近触发器执行历史。
     *
     * <p>支持两种查询模式：
     * <ul>
     *   <li><b>时间窗口</b>：传 {@code hours} 参数，返回近 N 小时内的执行历史（APM 看板用）。</li>
     *   <li><b>全局最近</b>：不传 {@code hours}，返回最近 {@code limit} 条执行历史。</li>
     * </ul></p>
     *
     * @param hours 时间窗口小时数（可选，APM 看板用）
     * @param limit 返回条数上限（默认 50）
     */
    @Operation(summary = "查询全局最近触发器执行历史（支持时间窗口）")
    @GetMapping("/execution-logs/recent")
    @PreAuthorize("@ss.hasPermission('lowcode:trigger:list')")
    public Result<List<LowCodeTriggerExecutionLog>> getRecentLogs(
            @RequestParam(required = false) Integer hours,
            @RequestParam(defaultValue = "50") int limit) {
        if (hours != null && hours > 0) {
            return Result.ok(executionLogService.listRecentByHours(hours, limit));
        }
        return Result.ok(executionLogService.listRecent(limit));
    }
}
