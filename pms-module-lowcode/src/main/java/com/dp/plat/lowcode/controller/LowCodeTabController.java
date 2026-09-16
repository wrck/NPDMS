package com.dp.plat.lowcode.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeTab;
import com.dp.plat.lowcode.service.LowCodeTabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 低代码标签页配置 Controller。
 *
 * <p>提供标签页配置的 CRUD、按 code 查询已发布配置、发布/归档状态流转、
 * JSON 导入导出等接口。</p>
 */
@Tag(name = "低代码标签页配置", description = "LowCode tab configuration APIs")
@RestController
@RequestMapping("/api/lowcode/tab")
@RequiredArgsConstructor
public class LowCodeTabController {

    private final LowCodeTabService lowCodeTabService;

    @Operation(summary = "分页查询标签页配置")
    @GetMapping
    public Result<IPage<LowCodeTab>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            LowCodeConfigQuery query) {
        Page<LowCodeTab> page = new Page<>(current, size);
        return Result.ok(lowCodeTabService.page(page, query));
    }

    @Operation(summary = "根据ID查询标签页配置")
    @GetMapping("/{id}")
    public Result<LowCodeTab> getById(@PathVariable Long id) {
        return Result.ok(lowCodeTabService.getById(id));
    }

    @Operation(summary = "根据编码查询已发布标签页配置")
    @GetMapping("/code/{code}")
    public Result<LowCodeTab> getByCode(@PathVariable String code) {
        return Result.ok(lowCodeTabService.getByCode(code));
    }

    @Operation(summary = "创建标签页配置")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:tab:add')")
    @OperLog(title = "低代码标签页配置", businessType = 1)
    public Result<LowCodeTab> create(@Valid @RequestBody LowCodeTab tab) {
        return Result.ok(lowCodeTabService.create(tab));
    }

    @Operation(summary = "更新标签页配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:tab:edit')")
    @OperLog(title = "低代码标签页配置", businessType = 2)
    public Result<LowCodeTab> update(@PathVariable Long id, @Valid @RequestBody LowCodeTab tab) {
        tab.setId(id);
        return Result.ok(lowCodeTabService.update(tab));
    }

    @Operation(summary = "删除标签页配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:tab:remove')")
    @OperLog(title = "低代码标签页配置", businessType = 3)
    public Result<?> delete(@PathVariable Long id) {
        lowCodeTabService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "发布标签页配置")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:tab:publish')")
    @OperLog(title = "低代码标签页配置", businessType = 2)
    public Result<?> publish(@PathVariable Long id) {
        lowCodeTabService.publish(id);
        return Result.ok();
    }

    @Operation(summary = "归档标签页配置")
    @PostMapping("/{id}/archive")
    @PreAuthorize("@ss.hasPermission('lowcode:tab:archive')")
    @OperLog(title = "低代码标签页配置", businessType = 2)
    public Result<?> archive(@PathVariable Long id) {
        lowCodeTabService.archive(id);
        return Result.ok();
    }

    @Operation(summary = "导出标签页配置 JSON")
    @GetMapping("/{code}/export")
    @PreAuthorize("@ss.hasPermission('lowcode:tab:export')")
    @OperLog(title = "低代码标签页配置", businessType = 4)
    public ResponseEntity<byte[]> exportConfig(@PathVariable String code) {
        byte[] data = lowCodeTabService.exportConfig(code);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "tab-" + code + ".json");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @Operation(summary = "导入标签页配置 JSON")
    @PostMapping("/import")
    @PreAuthorize("@ss.hasPermission('lowcode:tab:import')")
    @OperLog(title = "低代码标签页配置", businessType = 5)
    public Result<LowCodeTab> importConfig(@RequestBody String json) {
        return Result.ok(lowCodeTabService.importConfig(json));
    }
}
