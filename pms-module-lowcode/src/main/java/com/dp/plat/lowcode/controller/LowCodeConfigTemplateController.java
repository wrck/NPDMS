package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.entity.LowCodeConfigTemplate;
import com.dp.plat.lowcode.service.ConfigTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 低代码配置模板市场 Controller（批次5-T8）。
 *
 * <p>提供模板 CRUD + 上架/下架/归档 + 市场浏览/搜索 + 下载（参数化）+ 评分 + 版本查询。
 * 借鉴 Zoho 模板市场 / Appsmith 模板 / Mendix App Store。</p>
 */
@Tag(name = "低代码配置模板市场", description = "LowCode config template marketplace")
@RestController
@RequestMapping("/api/lowcode/config-template")
@RequiredArgsConstructor
public class LowCodeConfigTemplateController {

    private final ConfigTemplateService configTemplateService;

    @Operation(summary = "保存模板（新增/更新，按 code 去重）")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:template:create')")
    @OperLog(title = "低代码模板-保存", businessType = 1)
    public Result<LowCodeConfigTemplate> save(@RequestBody LowCodeConfigTemplate template) {
        configTemplateService.save(template);
        return Result.ok(template);
    }

    @Operation(summary = "上架模板")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:template:publish')")
    @OperLog(title = "低代码模板-上架", businessType = 2)
    public Result<LowCodeConfigTemplate> publish(@PathVariable Long id) {
        return Result.ok(configTemplateService.publish(id));
    }

    @Operation(summary = "下架模板")
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("@ss.hasPermission('lowcode:template:publish')")
    @OperLog(title = "低代码模板-下架", businessType = 2)
    public Result<LowCodeConfigTemplate> unpublish(@PathVariable Long id) {
        return Result.ok(configTemplateService.unpublish(id));
    }

    @Operation(summary = "归档模板")
    @PostMapping("/{id}/archive")
    @PreAuthorize("@ss.hasPermission('lowcode:template:delete')")
    @OperLog(title = "低代码模板-归档", businessType = 3)
    public Result<LowCodeConfigTemplate> archive(@PathVariable Long id) {
        return Result.ok(configTemplateService.archive(id));
    }

    @Operation(summary = "模板详情（按 ID）")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:template:list')")
    public Result<LowCodeConfigTemplate> get(@PathVariable Long id) {
        return Result.ok(configTemplateService.getById(id));
    }

    @Operation(summary = "模板详情（按 code）")
    @GetMapping("/code/{code}")
    @PreAuthorize("@ss.hasPermission('lowcode:template:list')")
    public Result<LowCodeConfigTemplate> getByCode(@PathVariable String code) {
        return Result.ok(configTemplateService.getByCode(code));
    }

    @Operation(summary = "查询所有模板（含 DRAFT/ARCHIVED，管理用）")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:template:list')")
    public Result<List<LowCodeConfigTemplate>> list() {
        return Result.ok(configTemplateService.listAll());
    }

    @Operation(summary = "市场浏览（仅已发布，支持关键词/类型/分类过滤）")
    @GetMapping("/marketplace")
    @PreAuthorize("@ss.hasPermission('lowcode:template:list')")
    public Result<List<LowCodeConfigTemplate>> marketplace(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String configType,
            @RequestParam(required = false) String category) {
        return Result.ok(configTemplateService.marketplace(keyword, configType, category));
    }

    @Operation(summary = "下载模板（增加下载计数，应用参数化替换后返回配置）")
    @PostMapping("/{id}/download")
    @PreAuthorize("@ss.hasPermission('lowcode:template:download')")
    @OperLog(title = "低代码模板-下载", businessType = 2)
    public Result<LowCodeConfigTemplate> download(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> parameters) {
        return Result.ok(configTemplateService.download(id, parameters == null ? Map.of() : parameters));
    }

    @Operation(summary = "评分（更新平均评分与评分数）")
    @PostMapping("/{id}/rate")
    @PreAuthorize("@ss.hasPermission('lowcode:template:rate')")
    public Result<LowCodeConfigTemplate> rate(@PathVariable Long id,
                                                @RequestBody RateRequest request) {
        return Result.ok(configTemplateService.rate(id, request.getRating()));
    }

    @Operation(summary = "查询某 code 的所有版本（按 version desc）")
    @GetMapping("/versions/{code}")
    @PreAuthorize("@ss.hasPermission('lowcode:template:list')")
    public Result<List<LowCodeConfigTemplate>> listVersions(@PathVariable String code) {
        return Result.ok(configTemplateService.listVersions(code));
    }

    /** 评分请求体 */
    @lombok.Data
    public static class RateRequest {
        /** 评分（0-5） */
        private BigDecimal rating;
    }
}
