package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.entity.LowCodeConfigVersion;
import com.dp.plat.lowcode.entity.LowCodeRule;
import com.dp.plat.lowcode.service.LowCodeRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 低代码规则 Controller。
 *
 * <p>提供规则 CRUD 与执行接口。写操作需对应权限，并记录操作日志。</p>
 */
@Tag(name = "低代码规则", description = "LowCode rule APIs")
@RestController
@RequestMapping("/api/lowcode/rule")
@RequiredArgsConstructor
public class LowCodeRuleController {

    private final LowCodeRuleService ruleService;

    @Operation(summary = "规则列表")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:rule:list')")
    public Result<List<LowCodeRule>> list() {
        return Result.ok(ruleService.list());
    }

    @Operation(summary = "规则详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:list')")
    public Result<LowCodeRule> get(@PathVariable Long id) {
        return Result.ok(ruleService.getById(id));
    }

    @Operation(summary = "保存规则")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:rule:edit')")
    @OperLog(title = "低代码规则", businessType = 1)
    public Result<LowCodeRule> save(@RequestBody LowCodeRule rule) {
        ruleService.saveOrUpdate(rule);
        return Result.ok(rule);
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:edit')")
    @OperLog(title = "低代码规则", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "执行规则")
    @PostMapping("/{code}/execute")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:exec')")
    public Result<Map<String, Object>> execute(@PathVariable String code,
                                               @RequestBody(required = false) Map<String, Object> facts) {
        return Result.ok(ruleService.execute(code, facts == null ? Map.of() : facts));
    }

    @Operation(summary = "发布规则并生成版本快照")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:edit')")
    @OperLog(title = "低代码规则", businessType = 2)
    public Result<LowCodeConfigVersion> publishWithVersion(@PathVariable Long id) {
        return Result.ok(ruleService.publishWithVersion(id));
    }

    @Operation(summary = "查询规则版本历史")
    @GetMapping("/{id}/versions")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:list')")
    public Result<List<LowCodeConfigVersion>> listVersions(@PathVariable Long id) {
        return Result.ok(ruleService.listRuleVersions(id));
    }

    @Operation(summary = "回滚规则到指定版本")
    @PostMapping("/{id}/rollback/{targetVersion}")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:edit')")
    @OperLog(title = "低代码规则", businessType = 2)
    public Result<Void> rollback(@PathVariable Long id, @PathVariable Integer targetVersion) {
        ruleService.rollbackRule(id, targetVersion);
        return Result.ok();
    }
}
