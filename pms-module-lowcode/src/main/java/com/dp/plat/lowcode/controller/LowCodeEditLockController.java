package com.dp.plat.lowcode.controller;

import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.engine.editlock.EditLockInfo;
import com.dp.plat.lowcode.engine.editlock.EditLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "低代码编辑锁", description = "LowCode edit lock")
@RestController
@RequestMapping("/api/lowcode/edit-lock")
@RequiredArgsConstructor
public class LowCodeEditLockController {

    private final EditLockService editLockService;

    @Operation(summary = "获取编辑锁")
    @PostMapping("/acquire")
    @PreAuthorize("@ss.hasPermission('lowcode:editlock:acquire')")
    public Result<EditLockInfo> acquire(@RequestParam String configType,
                                        @RequestParam Long configId,
                                        @RequestParam Long userId,
                                        @RequestParam(required = false) String userName) {
        return Result.ok(editLockService.acquire(configType, configId, userId, userName));
    }

    @Operation(summary = "心跳续期")
    @PostMapping("/renew")
    public Result<EditLockInfo> renew(@RequestParam String configType,
                                      @RequestParam Long configId,
                                      @RequestParam Long userId) {
        return Result.ok(editLockService.renew(configType, configId, userId));
    }

    @Operation(summary = "释放编辑锁")
    @PostMapping("/release")
    @PreAuthorize("@ss.hasPermission('lowcode:editlock:release')")
    public Result<Void> release(@RequestParam String configType,
                                @RequestParam Long configId,
                                @RequestParam Long userId) {
        editLockService.release(configType, configId, userId);
        return Result.ok();
    }

    @Operation(summary = "查询当前持锁人")
    @GetMapping
    public Result<EditLockInfo> getLock(@RequestParam String configType,
                                        @RequestParam Long configId) {
        return Result.ok(editLockService.getLock(configType, configId));
    }
}
