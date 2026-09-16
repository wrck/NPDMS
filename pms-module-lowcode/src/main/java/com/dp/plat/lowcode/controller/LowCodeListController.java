package com.dp.plat.lowcode.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeList;
import com.dp.plat.lowcode.service.LowCodeListService;
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
 * 低代码列表配置 Controller。
 *
 * <p>提供列表配置的 CRUD、按 code 查询已发布配置、发布/归档状态流转、
 * JSON 导入导出等接口。</p>
 */
@Tag(name = "低代码列表配置", description = "LowCode list configuration APIs")
@RestController
@RequestMapping("/api/lowcode/list")
@RequiredArgsConstructor
public class LowCodeListController {

    private final LowCodeListService lowCodeListService;

    @Operation(summary = "分页查询列表配置")
    @GetMapping
    public Result<IPage<LowCodeList>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            LowCodeConfigQuery query) {
        Page<LowCodeList> page = new Page<>(current, size);
        return Result.ok(lowCodeListService.page(page, query));
    }

    @Operation(summary = "根据ID查询列表配置")
    @GetMapping("/{id}")
    public Result<LowCodeList> getById(@PathVariable Long id) {
        return Result.ok(lowCodeListService.getById(id));
    }

    @Operation(summary = "根据编码查询已发布列表配置")
    @GetMapping("/code/{code}")
    public Result<LowCodeList> getByCode(@PathVariable String code) {
        return Result.ok(lowCodeListService.getByCode(code));
    }

    @Operation(summary = "创建列表配置")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:list:add')")
    @OperLog(title = "低代码列表配置", businessType = 1)
    public Result<LowCodeList> create(@Valid @RequestBody LowCodeList list) {
        return Result.ok(lowCodeListService.create(list));
    }

    @Operation(summary = "更新列表配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:list:edit')")
    @OperLog(title = "低代码列表配置", businessType = 2)
    public Result<LowCodeList> update(@PathVariable Long id, @Valid @RequestBody LowCodeList list) {
        list.setId(id);
        return Result.ok(lowCodeListService.update(list));
    }

    @Operation(summary = "删除列表配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:list:remove')")
    @OperLog(title = "低代码列表配置", businessType = 3)
    public Result<?> delete(@PathVariable Long id) {
        lowCodeListService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "发布列表配置")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:list:publish')")
    @OperLog(title = "低代码列表配置", businessType = 2)
    public Result<?> publish(@PathVariable Long id) {
        lowCodeListService.publish(id);
        return Result.ok();
    }

    @Operation(summary = "归档列表配置")
    @PostMapping("/{id}/archive")
    @PreAuthorize("@ss.hasPermission('lowcode:list:archive')")
    @OperLog(title = "低代码列表配置", businessType = 2)
    public Result<?> archive(@PathVariable Long id) {
        lowCodeListService.archive(id);
        return Result.ok();
    }

    @Operation(summary = "导出列表配置 JSON")
    @GetMapping("/{code}/export")
    @PreAuthorize("@ss.hasPermission('lowcode:list:export')")
    @OperLog(title = "低代码列表配置", businessType = 4)
    public ResponseEntity<byte[]> exportConfig(@PathVariable String code) {
        byte[] data = lowCodeListService.exportConfig(code);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "list-" + code + ".json");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @Operation(summary = "导入列表配置 JSON")
    @PostMapping("/import")
    @PreAuthorize("@ss.hasPermission('lowcode:list:import')")
    @OperLog(title = "低代码列表配置", businessType = 5)
    public Result<LowCodeList> importConfig(@RequestBody String json) {
        return Result.ok(lowCodeListService.importConfig(json));
    }
}
