package cn.iocoder.yudao.module.pms.lowcode.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeDataSource;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeDataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多数据源配置 API（批次3-T7）。
 *
 * <p>提供数据源配置 CRUD + 连接测试 + 激活/停用。
 * 支持三种集成模式：DIRECT / REPLICA / FEDERATED。</p>
 */
@Tag(name = "低代码多数据源")
@RestController
@RequestMapping("/api/lowcode/datasource")
@RequiredArgsConstructor
public class LowCodeDataSourceController {

    private final LowCodeDataSourceService dataSourceService;

    @Operation(summary = "查询数据源列表")
    @GetMapping
    @PreAuthorize("@ss.hasPermi('lowcode:datasource:list')")
    public CommonResult<List<LowCodeDataSource>> list() {
        return CommonResult.success(dataSourceService.list());
    }

    @Operation(summary = "获取数据源详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lowcode:datasource:list')")
    public CommonResult<LowCodeDataSource> get(@PathVariable Long id) {
        return CommonResult.success(dataSourceService.getById(id));
    }

    @Operation(summary = "新增数据源")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('lowcode:datasource:edit')")
    public CommonResult<LowCodeDataSource> create(@RequestBody LowCodeDataSource ds) {
        dataSourceService.save(ds);
        return CommonResult.success(ds);
    }

    @Operation(summary = "更新数据源")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lowcode:datasource:edit')")
    public CommonResult<Void> update(@PathVariable Long id, @RequestBody LowCodeDataSource ds) {
        ds.setId(id);
        dataSourceService.updateById(ds);
        return CommonResult.success(null);
    }

    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lowcode:datasource:edit')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        LowCodeDataSource ds = dataSourceService.getById(id);
        if (ds != null) {
            // 删除前先停用（从运行时注销）
            dataSourceService.deactivate(ds.getCode());
        }
        dataSourceService.removeById(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "测试数据源连接")
    @PostMapping("/test")
    @PreAuthorize("@ss.hasPermi('lowcode:datasource:list')")
    public CommonResult<Map<String, Object>> testConnection(@RequestBody LowCodeDataSource ds) {
        return CommonResult.success(dataSourceService.testConnection(ds));
    }

    @Operation(summary = "激活数据源")
    @PostMapping("/{code}/activate")
    @PreAuthorize("@ss.hasPermi('lowcode:datasource:edit')")
    public CommonResult<Void> activate(@PathVariable String code) {
        dataSourceService.activate(code);
        return CommonResult.success(null);
    }

    @Operation(summary = "停用数据源")
    @PostMapping("/{code}/deactivate")
    @PreAuthorize("@ss.hasPermi('lowcode:datasource:edit')")
    public CommonResult<Void> deactivate(@PathVariable String code) {
        dataSourceService.deactivate(code);
        return CommonResult.success(null);
    }
}
