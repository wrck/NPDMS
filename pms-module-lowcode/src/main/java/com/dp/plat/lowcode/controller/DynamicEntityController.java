package com.dp.plat.lowcode.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.DynamicQueryRequest;
import com.dp.plat.lowcode.engine.DynamicEntityDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

import java.util.Map;

/**
 * 动态实体数据 CRUD Controller。
 *
 * <p>运行时根据实体编码动态路由到对应物理表，提供通用 CRUD 接口。
 * 权限按实体编码动态校验：通过 SpEL 拼接权限串
 * {@code lowcode:data:{entityCode}:{action}} 实现按实体粒度授权。</p>
 */
@Tag(name = "动态实体数据 CRUD", description = "LowCode dynamic entity data APIs")
@RestController
@RequestMapping("/api/lowcode/data/{entityCode}")
@RequiredArgsConstructor
public class DynamicEntityController {

    private final DynamicEntityDataService dataService;

    @Operation(summary = "分页查询动态实体数据")
    @GetMapping
    @PreAuthorize("@ss.hasPermi('lowcode:data:' + #entityCode + ':list')")
    public Result<Map<String, Object>> list(@PathVariable String entityCode,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) Map<String, Object> filters) {
        return Result.ok(dataService.list(entityCode, page, size, filters));
    }

    @Operation(summary = "高级查询动态实体数据（LIKE/IN/BETWEEN/OR/排序）")
    @PostMapping("/query")
    @PreAuthorize("@ss.hasPermi('lowcode:data:' + #entityCode + ':list')")
    public Result<Page<Map<String, Object>>> queryAdvanced(@PathVariable String entityCode,
                                                            @RequestBody DynamicQueryRequest request) {
        request.setEntityCode(entityCode);
        return Result.ok(dataService.queryAdvanced(request));
    }

    @Operation(summary = "查询单条动态实体数据")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lowcode:data:' + #entityCode + ':query')")
    public Result<Map<String, Object>> getById(@PathVariable String entityCode,
                                                @PathVariable Long id) {
        return Result.ok(dataService.getById(entityCode, id));
    }

    @Operation(summary = "新增动态实体数据")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('lowcode:data:' + #entityCode + ':add')")
    @OperLog(title = "动态实体数据", businessType = 1)
    public Result<Long> create(@PathVariable String entityCode,
                                @RequestBody Map<String, Object> data) {
        return Result.ok(dataService.create(entityCode, data));
    }

    @Operation(summary = "更新动态实体数据")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lowcode:data:' + #entityCode + ':edit')")
    @OperLog(title = "动态实体数据", businessType = 2)
    public Result<Void> update(@PathVariable String entityCode,
                                @PathVariable Long id,
                                @RequestBody Map<String, Object> data) {
        dataService.update(entityCode, id, data);
        return Result.ok();
    }

    @Operation(summary = "删除动态实体数据")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lowcode:data:' + #entityCode + ':delete')")
    @OperLog(title = "动态实体数据", businessType = 3)
    public Result<Void> delete(@PathVariable String entityCode,
                                @PathVariable Long id) {
        dataService.delete(entityCode, id);
        return Result.ok();
    }
}
