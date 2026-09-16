package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.engine.publish.PublishService;
import com.dp.plat.lowcode.entity.LowCodePublishRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "低代码发布流水线", description = "LowCode publish pipeline")
@RestController
@RequestMapping("/api/lowcode/publish")
@RequiredArgsConstructor
public class LowCodePublishController {

    private final PublishService publishService;

    @Operation(summary = "提交发布申请")
    @PostMapping("/submit")
    @PreAuthorize("@ss.hasPermission('lowcode:publish:submit')")
    @OperLog(title = "低代码发布", businessType = 1)
    public Result<LowCodePublishRecord> submit(@RequestParam String configType,
                                               @RequestParam Long configId,
                                               @RequestParam(required = false) String changeLog,
                                               @RequestParam Long userId,
                                               @RequestParam(required = false) String userName) {
        return Result.ok(publishService.submitForPublish(configType, configId, changeLog, userId, userName));
    }

    @Operation(summary = "审批通过")
    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPermission('lowcode:publish:approve')")
    @OperLog(title = "低代码发布", businessType = 2)
    public Result<LowCodePublishRecord> approve(@PathVariable Long id,
                                                @RequestParam Long approverId,
                                                @RequestParam(required = false) String approver) {
        return Result.ok(publishService.approve(id, approverId, approver));
    }

    @Operation(summary = "审批拒绝")
    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPermission('lowcode:publish:approve')")
    @OperLog(title = "低代码发布", businessType = 2)
    public Result<LowCodePublishRecord> reject(@PathVariable Long id,
                                               @RequestParam String reason,
                                               @RequestParam Long approverId,
                                               @RequestParam(required = false) String approver) {
        return Result.ok(publishService.reject(id, reason, approverId, approver));
    }

    @Operation(summary = "回滚发布")
    @PostMapping("/{id}/rollback")
    @PreAuthorize("@ss.hasPermission('lowcode:publish:rollback')")
    @OperLog(title = "低代码发布", businessType = 2)
    public Result<LowCodePublishRecord> rollback(@PathVariable Long id,
                                                 @RequestParam Long userId,
                                                 @RequestParam(required = false) String userName) {
        return Result.ok(publishService.rollback(id, userId, userName));
    }

    @Operation(summary = "查询配置发布记录")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:publish:list')")
    public Result<List<LowCodePublishRecord>> list(@RequestParam String configType,
                                                   @RequestParam Long configId) {
        return Result.ok(publishService.listByConfig(configType, configId));
    }

    @Operation(summary = "查询待审批发布")
    @GetMapping("/pending")
    @PreAuthorize("@ss.hasPermission('lowcode:publish:approve')")
    public Result<List<LowCodePublishRecord>> pending() {
        return Result.ok(publishService.listPending());
    }
}
