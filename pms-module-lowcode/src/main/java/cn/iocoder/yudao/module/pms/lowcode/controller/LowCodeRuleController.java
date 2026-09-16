package cn.iocoder.yudao.module.pms.lowcode.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeConfigVersion;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeRule;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeRuleService;
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
    public CommonResult<List<LowCodeRule>> list() {
        return CommonResult.success(ruleService.list());
    }

    @Operation(summary = "规则详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:list')")
    public CommonResult<LowCodeRule> get(@PathVariable Long id) {
        return CommonResult.success(ruleService.getById(id));
    }

    @Operation(summary = "保存规则")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:rule:edit')")
    public CommonResult<LowCodeRule> save(@RequestBody LowCodeRule rule) {
        ruleService.saveOrUpdate(rule);
        return CommonResult.success(rule);
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:edit')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        ruleService.removeById(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "执行规则")
    @PostMapping("/{code}/execute")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:exec')")
    public CommonResult<Map<String, Object>> execute(@PathVariable String code,
                                               @RequestBody(required = false) Map<String, Object> facts) {
        return CommonResult.success(ruleService.execute(code, facts == null ? Map.of() : facts));
    }

    @Operation(summary = "发布规则并生成版本快照")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:edit')")
    public CommonResult<LowCodeConfigVersion> publishWithVersion(@PathVariable Long id) {
        return CommonResult.success(ruleService.publishWithVersion(id));
    }

    @Operation(summary = "查询规则版本历史")
    @GetMapping("/{id}/versions")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:list')")
    public CommonResult<List<LowCodeConfigVersion>> listVersions(@PathVariable Long id) {
        return CommonResult.success(ruleService.listRuleVersions(id));
    }

    @Operation(summary = "回滚规则到指定版本")
    @PostMapping("/{id}/rollback/{targetVersion}")
    @PreAuthorize("@ss.hasPermission('lowcode:rule:edit')")
    public CommonResult<Void> rollback(@PathVariable Long id, @PathVariable Integer targetVersion) {
        ruleService.rollbackRule(id, targetVersion);
        return CommonResult.success(null);
    }
}
