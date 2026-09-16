package com.dp.plat.lowcode.controller;

import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.service.LowCodeFormService;
import com.dp.plat.lowcode.service.LowCodeListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 低代码预览模式数据 API。
 *
 * <p>提供测试数据填充表单/列表，供预览页面调用。</p>
 */
@Tag(name = "低代码预览", description = "LowCode preview")
@RestController
@RequestMapping("/api/lowcode/preview")
@RequiredArgsConstructor
public class LowCodePreviewController {

    private final LowCodeFormService formService;
    private final LowCodeListService listService;

    @Operation(summary = "获取表单预览数据")
    @GetMapping("/form/{formId}")
    public Result<Map<String, Object>> previewForm(@PathVariable Long formId) {
        Map<String, Object> result = new HashMap<>();
        result.put("form", formService.getById(formId));
        // 测试数据（简化版：返回空对象，前端按字段定义渲染）
        result.put("data", new HashMap<String, Object>());
        return Result.ok(result);
    }

    @Operation(summary = "获取列表预览数据")
    @GetMapping("/list/{listId}")
    public Result<Map<String, Object>> previewList(@PathVariable Long listId) {
        Map<String, Object> result = new HashMap<>();
        result.put("list", listService.getById(listId));
        // 测试数据（简化版：返回空列表）
        result.put("data", List.of());
        return Result.ok(result);
    }
}
