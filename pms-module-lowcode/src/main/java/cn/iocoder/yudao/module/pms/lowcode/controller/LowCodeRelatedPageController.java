package cn.iocoder.yudao.module.pms.lowcode.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.dto.LowCodeConfigQuery;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeRelatedPage;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeRelatedPageService;
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
    public CommonResult<IPage<LowCodeRelatedPage>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            LowCodeConfigQuery query) {
        Page<LowCodeRelatedPage> page = new Page<>(current, size);
        return CommonResult.success(lowCodeRelatedPageService.page(page, query));
    }

    @Operation(summary = "根据ID查询关联页配置")
    @GetMapping("/{id}")
    public CommonResult<LowCodeRelatedPage> getById(@PathVariable Long id) {
        return CommonResult.success(lowCodeRelatedPageService.getById(id));
    }

    @Operation(summary = "根据编码查询已发布关联页配置")
    @GetMapping("/code/{code}")
    public CommonResult<LowCodeRelatedPage> getByCode(@PathVariable String code) {
        return CommonResult.success(lowCodeRelatedPageService.getByCode(code));
    }

    @Operation(summary = "创建关联页配置")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:add')")
    public CommonResult<LowCodeRelatedPage> create(@Valid @RequestBody LowCodeRelatedPage relatedPage) {
        return CommonResult.success(lowCodeRelatedPageService.create(relatedPage));
    }

    @Operation(summary = "更新关联页配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:edit')")
    public CommonResult<LowCodeRelatedPage> update(@PathVariable Long id, @Valid @RequestBody LowCodeRelatedPage relatedPage) {
        relatedPage.setId(id);
        return CommonResult.success(lowCodeRelatedPageService.update(relatedPage));
    }

    @Operation(summary = "删除关联页配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:remove')")
    public CommonResult<?> delete(@PathVariable Long id) {
        lowCodeRelatedPageService.delete(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "发布关联页配置")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:publish')")
    public CommonResult<?> publish(@PathVariable Long id) {
        lowCodeRelatedPageService.publish(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "归档关联页配置")
    @PostMapping("/{id}/archive")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:archive')")
    public CommonResult<?> archive(@PathVariable Long id) {
        lowCodeRelatedPageService.archive(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "导出关联页配置 JSON")
    @GetMapping("/{code}/export")
    @PreAuthorize("@ss.hasPermission('lowcode:relatedPage:export')")
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
    public CommonResult<LowCodeRelatedPage> importConfig(@RequestBody String json) {
        return CommonResult.success(lowCodeRelatedPageService.importConfig(json));
    }
}
