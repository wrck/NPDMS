package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.entity.LowCodeApprovalChain;
import com.dp.plat.lowcode.service.LowCodeApprovalChainService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 低代码发布多级审批链 Controller。
 *
 * <p>提供审批链 CRUD + 按配置类型查询。同一 configType 仅允许一条启用审批链，
 * 新启用一条时由前端负责将其它链停用（后端 getEnabledByConfigType 仅取第一条）。</p>
 */
@Tag(name = "低代码审批链", description = "LowCode approval chain")
@RestController
@RequestMapping("/api/lowcode/approval-chain")
@RequiredArgsConstructor
public class LowCodeApprovalChainController {

    private final LowCodeApprovalChainService approvalChainService;

    @Operation(summary = "按配置类型查询审批链列表")
    @GetMapping("/config-type/{configType}")
    @PreAuthorize("@ss.hasPermission('lowcode:approval-chain:list')")
    public Result<List<LowCodeApprovalChain>> listByConfigType(@PathVariable String configType) {
        return Result.ok(approvalChainService.listByConfigType(configType));
    }

    @Operation(summary = "审批链列表（全部）")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:approval-chain:list')")
    public Result<List<LowCodeApprovalChain>> list() {
        return Result.ok(approvalChainService.list());
    }

    @Operation(summary = "审批链详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:approval-chain:list')")
    public Result<LowCodeApprovalChain> get(@PathVariable Long id) {
        return Result.ok(approvalChainService.getById(id));
    }

    @Operation(summary = "新建审批链")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:approval-chain:edit')")
    @OperLog(title = "低代码审批链", businessType = 1)
    public Result<LowCodeApprovalChain> create(@RequestBody LowCodeApprovalChain chain) {
        if (chain.getEnabled() == null) {
            chain.setEnabled(1);
        }
        approvalChainService.save(chain);
        return Result.ok(chain);
    }

    @Operation(summary = "更新审批链")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:approval-chain:edit')")
    @OperLog(title = "低代码审批链", businessType = 2)
    public Result<Void> update(@PathVariable Long id, @RequestBody LowCodeApprovalChain chain) {
        chain.setId(id);
        approvalChainService.updateById(chain);
        return Result.ok();
    }

    @Operation(summary = "删除审批链")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:approval-chain:edit')")
    @OperLog(title = "低代码审批链", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        approvalChainService.removeById(id);
        return Result.ok();
    }
}
