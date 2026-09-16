package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.entity.LowCodeGrayRelease;
import com.dp.plat.lowcode.service.GrayReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 低代码灰度发布 Controller（批次5-T4，借鉴华为 AppCube / OutSystems LifeTime）。
 */
@Tag(name = "低代码灰度发布", description = "LowCode gray release")
@RestController
@RequestMapping("/api/lowcode/gray-release")
@RequiredArgsConstructor
public class LowCodeGrayReleaseController {

    private final GrayReleaseService grayReleaseService;

    @Operation(summary = "创建灰度发布策略")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:gray-release:edit')")
    @OperLog(title = "低代码灰度发布", businessType = 1)
    public Result<LowCodeGrayRelease> create(@RequestBody CreateGrayRequest req) {
        return Result.ok(grayReleaseService.createGrayRelease(
                req.getPublishRecordId(), req.getGrayPercentage(),
                req.getTenantWhitelist(), req.getCreateBy()));
    }

    @Operation(summary = "调整灰度比例")
    @PostMapping("/{id}/percentage")
    @PreAuthorize("@ss.hasPermission('lowcode:gray-release:edit')")
    @OperLog(title = "低代码灰度发布", businessType = 2)
    public Result<LowCodeGrayRelease> updatePercentage(@PathVariable Long id,
                                                         @RequestParam Integer newPercentage) {
        return Result.ok(grayReleaseService.updatePercentage(id, newPercentage));
    }

    @Operation(summary = "全量发布")
    @PostMapping("/{id}/full")
    @PreAuthorize("@ss.hasPermission('lowcode:gray-release:edit')")
    @OperLog(title = "低代码灰度发布", businessType = 2)
    public Result<LowCodeGrayRelease> releaseFull(@PathVariable Long id) {
        return Result.ok(grayReleaseService.releaseFull(id));
    }

    @Operation(summary = "回滚灰度")
    @PostMapping("/{id}/rollback")
    @PreAuthorize("@ss.hasPermission('lowcode:gray-release:edit')")
    @OperLog(title = "低代码灰度发布", businessType = 2)
    public Result<LowCodeGrayRelease> rollback(@PathVariable Long id) {
        return Result.ok(grayReleaseService.rollbackGray(id));
    }

    @Operation(summary = "查询指定配置的灰度记录")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:gray-release:list')")
    public Result<List<LowCodeGrayRelease>> list(@RequestParam String configType,
                                                   @RequestParam Long configId) {
        return Result.ok(grayReleaseService.listByConfig(configType, configId));
    }

    @Operation(summary = "查询当前活跃灰度")
    @GetMapping("/active")
    @PreAuthorize("@ss.hasPermission('lowcode:gray-release:list')")
    public Result<LowCodeGrayRelease> active(@RequestParam String configType,
                                                @RequestParam Long configId) {
        return Result.ok(grayReleaseService.getActiveGrayRelease(configType, configId));
    }

    @Operation(summary = "判定用户/租户是否命中灰度")
    @GetMapping("/check")
    @PreAuthorize("@ss.hasPermission('lowcode:gray-release:list')")
    public Result<Boolean> check(@RequestParam String configType,
                                  @RequestParam Long configId,
                                  @RequestParam(required = false) Long userId,
                                  @RequestParam(required = false) String tenantId) {
        return Result.ok(grayReleaseService.isInGray(configType, configId, userId, tenantId));
    }

    @Data
    @Schema(description = "创建灰度发布请求")
    public static class CreateGrayRequest {
        @Schema(description = "发布记录 ID") private Long publishRecordId;
        @Schema(description = "灰度比例 0-100") private Integer grayPercentage;
        @Schema(description = "租户白名单 JSON 数组字符串") private String tenantWhitelist;
        @Schema(description = "创建人") private String createBy;
    }
}
