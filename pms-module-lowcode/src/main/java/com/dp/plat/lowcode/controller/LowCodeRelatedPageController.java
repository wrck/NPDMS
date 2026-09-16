package com.dp.plat.lowcode.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeRelatedPage;
import com.dp.plat.lowcode.service.LowCodeRelatedPageService;
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
 * 低代码关联页配置 Controller。
 *
 * <p>提供关联页配置的 CRUD、按 code 查询已发布配置、发布/归档状态流转、
 * JSON 导入导出等接口。</p>
 */
@Tag(name = "低代码关联页配置", description = "LowCode related page configuration APIs")
@RestController
@RequestMapping("/api/lowcode/related-page")
@RequiredArgsConstructor
public class LowCodeRelatedPageController {

    private final LowCodeRelatedPageService lowCodeRelatedPageService;

    @Operation(summary = "分页查询关联页配置")
    @GetMapping
    public Result<IPage<LowCodeRelatedPage>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            LowCodeConfigQuery query) {
        Page<LowCodeRelatedPage> page = new Page<>(current, size);
        return Result.ok(lowCodeRelatedPageService.page(page, query));
    }

    @Operation(summary = "根据ID查询关联页配置")
    @GetMapping("/{id}")
    public Result<LowCodeRelatedPage> getById(@PathVariable Long id) {
        return Result.ok(lowCodeRelatedPageService.getById(id));
    }

    @Operation(summary = "根据编码查询已发布关联页配置")
    @GetMapping("/code/{code}")
    public Result<LowCodeRelatedPage> getByCode(@PathVariable String code) {
        return Result.ok(lowCodeRelatedPageService.getByCode(code));
    }

    @Operation(summary = "创建关联页配置")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:add')")
    @OperLog(title = "低代码关联页配置", businessType = 1)
    public Result<LowCodeRelatedPage> create(@Valid @RequestBody LowCodeRelatedPage relatedPage) {
        return Result.ok(lowCodeRelatedPageService.create(relatedPage));
    }

    @Operation(summary = "更新关联页配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:edit')")
    @OperLog(title = "低代码关联页配置", businessType = 2)
    public Result<LowCodeRelatedPage> update(@PathVariable Long id, @Valid @RequestBody LowCodeRelatedPage relatedPage) {
        relatedPage.setId(id);
        return Result.ok(lowCodeRelatedPageService.update(relatedPage));
    }

    @Operation(summary = "删除关联页配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:remove')")
    @OperLog(title = "低代码关联页配置", businessType = 3)
    public Result<?> delete(@PathVariable Long id) {
        lowCodeRelatedPageService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "发布关联页配置")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:publish')")
    @OperLog(title = "低代码关联页配置", businessType = 2)
    public Result<?> publish(@PathVariable Long id) {
        lowCodeRelatedPageService.publish(id);
        return Result.ok();
    }

    @Operation(summary = "归档关联页配置")
    @PostMapping("/{id}/archive")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:archive')")
    @OperLog(title = "低代码关联页配置", businessType = 2)
    public Result<?> archive(@PathVariable Long id) {
        lowCodeRelatedPageService.archive(id);
        return Result.ok();
    }

    @Operation(summary = "导出关联页配置 JSON")
    @GetMapping("/{code}/export")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:export')")
    @OperLog(title = "低代码关联页配置", businessType = 4)
    public ResponseEntity<byte[]> exportConfig(@PathVariable String code) {
        byte[] data = lowCodeRelatedPageService.exportConfig(code);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "related-page-" + code + ".json");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @Operation(summary = "导入关联页配置 JSON")
    @PostMapping("/import")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:import')")
    @OperLog(title = "低代码关联页配置", businessType = 5)
    public Result<LowCodeRelatedPage> importConfig(@RequestBody String json) {
        return Result.ok(lowCodeRelatedPageService.importConfig(json));
    }
}
