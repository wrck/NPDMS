package com.dp.plat.lowcode.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.entity.LowCodeConfigAuditLog;
import com.dp.plat.lowcode.mapper.LowCodeConfigAuditLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 低代码配置审计日志 Controller（缺口2）。
 *
 * <p>提供审计日志的分页查询与详情查看接口，便于平台运维与合规追溯。</p>
 */
@Tag(name = "低代码配置审计日志", description = "LowCode config audit log APIs")
@RestController
@RequestMapping("/api/lowcode/config-audit")
@RequiredArgsConstructor
public class LowCodeConfigAuditLogController {

    private final LowCodeConfigAuditLogMapper auditLogMapper;

    @Operation(summary = "分页查询配置审计日志")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:config-audit:list')")
    public Result<Page<LowCodeConfigAuditLog>> page(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String configType,
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = size < 1 || size > 200 ? 20 : size;
        LambdaQueryWrapper<LowCodeConfigAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(actor != null && !actor.isBlank(), LowCodeConfigAuditLog::getActor, actor)
                .eq(configType != null && !configType.isBlank(), LowCodeConfigAuditLog::getConfigType, configType)
                .eq(configId != null, LowCodeConfigAuditLog::getConfigId, configId)
                .eq(action != null && !action.isBlank(), LowCodeConfigAuditLog::getAction, action)
                .ge(startTime != null, LowCodeConfigAuditLog::getCreateTime, startTime)
                .le(endTime != null, LowCodeConfigAuditLog::getCreateTime, endTime)
                .orderByDesc(LowCodeConfigAuditLog::getCreateTime);
        Page<LowCodeConfigAuditLog> pageResult = auditLogMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return Result.ok(pageResult);
    }

    @Operation(summary = "查询审计日志详情（含 before/after JSON 快照）")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:config-audit:list')")
    public Result<LowCodeConfigAuditLog> get(@PathVariable Long id) {
        return Result.ok(auditLogMapper.selectById(id));
    }
}
