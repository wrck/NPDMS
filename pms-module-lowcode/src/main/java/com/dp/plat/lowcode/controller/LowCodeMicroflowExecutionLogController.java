package com.dp.plat.lowcode.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.entity.LowCodeMicroflowExecutionLog;
import com.dp.plat.lowcode.mapper.LowCodeMicroflowExecutionLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 低代码微流执行轨迹 Controller（借鉴 Joget APM）。
 *
 * <p>提供按 executionId 查询某次执行的所有节点轨迹，以及按 microflowId 或时间窗口查询最近执行的接口。
 * 为兼容 APM 看板的全局视角，{@code /recent} 端点支持 {@code hours} 参数进行时间窗口全局查询。</p>
 */
@Tag(name = "低代码微流执行轨迹", description = "LowCode microflow execution log APIs")
@RestController
@RequestMapping("/api/lowcode/microflow-execution-log")
@RequiredArgsConstructor
public class LowCodeMicroflowExecutionLogController {

    private final LowCodeMicroflowExecutionLogMapper executionLogMapper;

    @Operation(summary = "按执行ID查询节点轨迹")
    @GetMapping("/{executionId}")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    public Result<List<LowCodeMicroflowExecutionLog>> getByExecutionId(@PathVariable String executionId) {
        List<LowCodeMicroflowExecutionLog> logs = executionLogMapper.selectList(
                new LambdaQueryWrapper<LowCodeMicroflowExecutionLog>()
                        .eq(LowCodeMicroflowExecutionLog::getExecutionId, executionId)
                        .orderByAsc(LowCodeMicroflowExecutionLog::getId));
        return Result.ok(logs);
    }

    /**
     * 查询微流最近执行轨迹。
     *
     * <p>支持两种查询模式（二选一，{@code hours} 优先）：
     * <ul>
     *   <li><b>全局时间窗口</b>：传 {@code hours} 参数，返回近 N 小时内所有微流的执行轨迹（APM 看板用）。</li>
     *   <li><b>按微流查询</b>：传 {@code microflowId} 参数，返回该微流最近 {@code limit} 条执行轨迹。</li>
     * </ul>
     * 两个参数均为可选；若都不传则返回空列表。</p>
     *
     * @param microflowId 微流ID（可选，与 hours 互斥）
     * @param hours       时间窗口小时数（可选，与 microflowId 互斥）
     * @param limit       返回条数上限（默认 50，最大 500）
     */
    @Operation(summary = "查询微流最近执行轨迹（支持全局时间窗口 / 按微流查询）")
    @GetMapping("/recent")
    @PreAuthorize("@ss.hasPermission('lowcode:microflow:exec')")
    public Result<List<LowCodeMicroflowExecutionLog>> getRecent(
            @RequestParam(required = false) Long microflowId,
            @RequestParam(required = false) Integer hours,
            @RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 50 : limit;
        LambdaQueryWrapper<LowCodeMicroflowExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (hours != null && hours > 0) {
            // APM 全局视角：按时间窗口查询所有微流
            wrapper.ge(LowCodeMicroflowExecutionLog::getStartTime,
                    LocalDateTime.now().minusHours(hours));
            wrapper.orderByDesc(LowCodeMicroflowExecutionLog::getStartTime);
        } else if (microflowId != null) {
            // 按微流查询（向后兼容）
            wrapper.eq(LowCodeMicroflowExecutionLog::getMicroflowId, microflowId);
            wrapper.orderByDesc(LowCodeMicroflowExecutionLog::getId);
        } else {
            // 都不传：返回空列表（避免全表扫描）
            return Result.ok(List.of());
        }
        wrapper.last("LIMIT " + safeLimit);
        List<LowCodeMicroflowExecutionLog> logs = executionLogMapper.selectList(wrapper);
        return Result.ok(logs);
    }
}
