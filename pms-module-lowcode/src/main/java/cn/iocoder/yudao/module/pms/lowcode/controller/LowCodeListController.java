package cn.iocoder.yudao.module.pms.lowcode.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.dto.LowCodeConfigQuery;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeList;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeListService;
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
    public CommonResult<IPage<LowCodeList>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            LowCodeConfigQuery query) {
        Page<LowCodeList> page = new Page<>(current, size);
        return CommonResult.success(lowCodeListService.page(page, query));
    }

    @Operation(summary = "根据ID查询列表配置")
    @GetMapping("/{id}")
    public CommonResult<LowCodeList> getById(@PathVariable Long id) {
        return CommonResult.success(lowCodeListService.getById(id));
    }

    @Operation(summary = "根据编码查询已发布列表配置")
    @GetMapping("/code/{code}")
    public CommonResult<LowCodeList> getByCode(@PathVariable String code) {
        return CommonResult.success(lowCodeListService.getByCode(code));
    }

    @Operation(summary = "创建列表配置")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:list:add')")
    public CommonResult<LowCodeList> create(@Valid @RequestBody LowCodeList list) {
        return CommonResult.success(lowCodeListService.create(list));
    }

    @Operation(summary = "更新列表配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:list:edit')")
    public CommonResult<LowCodeList> update(@PathVariable Long id, @Valid @RequestBody LowCodeList list) {
        list.setId(id);
        return CommonResult.success(lowCodeListService.update(list));
    }

    @Operation(summary = "删除列表配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:list:remove')")
    public CommonResult<?> delete(@PathVariable Long id) {
        lowCodeListService.delete(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "发布列表配置")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:list:publish')")
    public CommonResult<?> publish(@PathVariable Long id) {
        lowCodeListService.publish(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "归档列表配置")
    @PostMapping("/{id}/archive")
    @PreAuthorize("@ss.hasPermission('lowcode:list:archive')")
    public CommonResult<?> archive(@PathVariable Long id) {
        lowCodeListService.archive(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "导出列表配置 JSON")
    @GetMapping("/{code}/export")
    @PreAuthorize("@ss.hasPermission('lowcode:list:export')")
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
    public CommonResult<LowCodeList> importConfig(@RequestBody String json) {
        return CommonResult.success(lowCodeListService.importConfig(json));
    }
}
