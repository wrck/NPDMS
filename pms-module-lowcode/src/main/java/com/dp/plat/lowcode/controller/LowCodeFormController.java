package com.dp.plat.lowcode.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeForm;
import com.dp.plat.lowcode.service.LowCodeFormService;
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
 * 低代码表单配置 Controller。
 *
 * <p>提供表单配置的 CRUD、按 code 查询已发布配置、发布/归档状态流转、
 * JSON 导入导出等接口。写操作需对应权限，并记录操作日志。</p>
 */
@Tag(name = "低代码表单配置", description = "LowCode form configuration APIs")
@RestController
@RequestMapping("/api/lowcode/form")
@RequiredArgsConstructor
public class LowCodeFormController {

    private final LowCodeFormService lowCodeFormService;

    @Operation(summary = "分页查询表单配置")
    @GetMapping
    public Result<IPage<LowCodeForm>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            LowCodeConfigQuery query) {
        Page<LowCodeForm> page = new Page<>(current, size);
        return Result.ok(lowCodeFormService.page(page, query));
    }

    @Operation(summary = "根据ID查询表单配置")
    @GetMapping("/{id}")
    public Result<LowCodeForm> getById(@PathVariable Long id) {
        return Result.ok(lowCodeFormService.getById(id));
    }

    @Operation(summary = "根据编码查询已发布表单配置")
    @GetMapping("/code/{code}")
    public Result<LowCodeForm> getByCode(@PathVariable String code) {
        return Result.ok(lowCodeFormService.getByCode(code));
    }

    @Operation(summary = "创建表单配置")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:form:add')")
    @OperLog(title = "低代码表单配置", businessType = 1)
    public Result<LowCodeForm> create(@Valid @RequestBody LowCodeForm form) {
        return Result.ok(lowCodeFormService.create(form));
    }

    @Operation(summary = "更新表单配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:form:edit')")
    @OperLog(title = "低代码表单配置", businessType = 2)
    public Result<LowCodeForm> update(@PathVariable Long id, @Valid @RequestBody LowCodeForm form) {
        form.setId(id);
        return Result.ok(lowCodeFormService.update(form));
    }

    @Operation(summary = "删除表单配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:form:remove')")
    @OperLog(title = "低代码表单配置", businessType = 3)
    public Result<?> delete(@PathVariable Long id) {
        lowCodeFormService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "发布表单配置")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:form:publish')")
    @OperLog(title = "低代码表单配置", businessType = 2)
    public Result<?> publish(@PathVariable Long id) {
        lowCodeFormService.publish(id);
        return Result.ok();
    }

    @Operation(summary = "归档表单配置")
    @PostMapping("/{id}/archive")
    @PreAuthorize("@ss.hasPermission('lowcode:form:archive')")
    @OperLog(title = "低代码表单配置", businessType = 2)
    public Result<?> archive(@PathVariable Long id) {
        lowCodeFormService.archive(id);
        return Result.ok();
    }

    @Operation(summary = "导出表单配置 JSON")
    @GetMapping("/{code}/export")
    @PreAuthorize("@ss.hasPermission('lowcode:form:export')")
    @OperLog(title = "低代码表单配置", businessType = 4)
    public ResponseEntity<byte[]> exportConfig(@PathVariable String code) {
        byte[] data = lowCodeFormService.exportConfig(code);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "form-" + code + ".json");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @Operation(summary = "导入表单配置 JSON")
    @PostMapping("/import")
    @PreAuthorize("@ss.hasPermission('lowcode:form:import')")
    @OperLog(title = "低代码表单配置", businessType = 5)
    public Result<LowCodeForm> importConfig(@RequestBody String json) {
        return Result.ok(lowCodeFormService.importConfig(json));
    }
}
