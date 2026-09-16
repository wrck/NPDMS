package cn.iocoder.yudao.module.pms.lowcode.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.engine.publish.PublishService;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodePublishRecord;
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
    public CommonResult<LowCodePublishRecord> submit(@RequestParam String configType,
                                               @RequestParam Long configId,
                                               @RequestParam(required = false) String changeLog,
                                               @RequestParam Long userId,
                                               @RequestParam(required = false) String userName) {
        return CommonResult.success(publishService.submitForPublish(configType, configId, changeLog, userId, userName));
    }

    @Operation(summary = "审批通过")
    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPermission('lowcode:publish:approve')")
    public CommonResult<LowCodePublishRecord> approve(@PathVariable Long id,
                                                @RequestParam Long approverId,
                                                @RequestParam(required = false) String approver) {
        return CommonResult.success(publishService.approve(id, approverId, approver));
    }

    @Operation(summary = "审批拒绝")
    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPermission('lowcode:publish:approve')")
    public CommonResult<LowCodePublishRecord> reject(@PathVariable Long id,
                                               @RequestParam String reason,
                                               @RequestParam Long approverId,
                                               @RequestParam(required = false) String approver) {
        return CommonResult.success(publishService.reject(id, reason, approverId, approver));
    }

    @Operation(summary = "回滚发布")
    @PostMapping("/{id}/rollback")
    @PreAuthorize("@ss.hasPermission('lowcode:publish:rollback')")
    public CommonResult<LowCodePublishRecord> rollback(@PathVariable Long id,
                                                 @RequestParam Long userId,
                                                 @RequestParam(required = false) String userName) {
        return CommonResult.success(publishService.rollback(id, userId, userName));
    }

    @Operation(summary = "查询配置发布记录")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:publish:list')")
    public CommonResult<List<LowCodePublishRecord>> list(@RequestParam String configType,
                                                   @RequestParam Long configId) {
        return CommonResult.success(publishService.listByConfig(configType, configId));
    }

    @Operation(summary = "查询待审批发布")
    @GetMapping("/pending")
    @PreAuthorize("@ss.hasPermission('lowcode:publish:approve')")
    public CommonResult<List<LowCodePublishRecord>> pending() {
        return CommonResult.success(publishService.listPending());
    }
}
