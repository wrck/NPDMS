package cn.iocoder.yudao.module.pms.lowcode.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.dto.LowCodeConfigQuery;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeForm;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeFormService;
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
    public CommonResult<IPage<LowCodeForm>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            LowCodeConfigQuery query) {
        Page<LowCodeForm> page = new Page<>(current, size);
        return CommonResult.success(lowCodeFormService.page(page, query));
    }

    @Operation(summary = "根据ID查询表单配置")
    @GetMapping("/{id}")
    public CommonResult<LowCodeForm> getById(@PathVariable Long id) {
        return CommonResult.success(lowCodeFormService.getById(id));
    }

    @Operation(summary = "根据编码查询已发布表单配置")
    @GetMapping("/code/{code}")
    public CommonResult<LowCodeForm> getByCode(@PathVariable String code) {
        return CommonResult.success(lowCodeFormService.getByCode(code));
    }

    @Operation(summary = "创建表单配置")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:form:add')")
    public CommonResult<LowCodeForm> create(@Valid @RequestBody LowCodeForm form) {
        return CommonResult.success(lowCodeFormService.create(form));
    }

    @Operation(summary = "更新表单配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:form:edit')")
    public CommonResult<LowCodeForm> update(@PathVariable Long id, @Valid @RequestBody LowCodeForm form) {
        form.setId(id);
        return CommonResult.success(lowCodeFormService.update(form));
    }

    @Operation(summary = "删除表单配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:form:remove')")
    public CommonResult<?> delete(@PathVariable Long id) {
        lowCodeFormService.delete(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "发布表单配置")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:form:publish')")
    public CommonResult<?> publish(@PathVariable Long id) {
        lowCodeFormService.publish(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "归档表单配置")
    @PostMapping("/{id}/archive")
    @PreAuthorize("@ss.hasPermission('lowcode:form:archive')")
    public CommonResult<?> archive(@PathVariable Long id) {
        lowCodeFormService.archive(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "导出表单配置 JSON")
    @GetMapping("/{code}/export")
    @PreAuthorize("@ss.hasPermission('lowcode:form:export')")
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
    public CommonResult<LowCodeForm> importConfig(@RequestBody String json) {
        return CommonResult.success(lowCodeFormService.importConfig(json));
    }
}
