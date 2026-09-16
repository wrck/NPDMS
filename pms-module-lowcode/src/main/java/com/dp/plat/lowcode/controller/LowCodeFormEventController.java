package com.dp.plat.lowcode.controller;

import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.service.LowCodeFormEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 低代码表单事件 Controller。
 *
 * <p>提供表单事件触发端点，供前端表单渲染器在 onLoad/onChange/onSubmit 时调用。</p>
 */
@Tag(name = "低代码表单事件", description = "LowCode form event trigger")
@RestController
@RequestMapping("/api/lowcode/form")
@RequiredArgsConstructor
public class LowCodeFormEventController {

    private final LowCodeFormEventService formEventService;

    @Operation(summary = "触发表单事件")
    @PostMapping("/{formId}/event/{eventType}")
    public Result<Map<String, Object>> triggerEvent(@PathVariable Long formId,
                                                    @PathVariable String eventType,
                                                    @RequestBody(required = false) Map<String, Object> data) {
        return Result.ok(formEventService.triggerEvent(formId, eventType, data == null ? Map.of() : data));
    }
}
