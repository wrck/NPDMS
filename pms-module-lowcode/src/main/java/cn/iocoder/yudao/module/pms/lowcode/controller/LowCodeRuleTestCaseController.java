package cn.iocoder.yudao.module.pms.lowcode.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeRuleTestCase;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeRuleTestCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 规则测试用例 API（批次3-T3）。
 *
 * <p>提供规则测试用例的 CRUD + 批量运行测试，验证规则定义的正确性。</p>
 */
@Tag(name = "低代码规则测试用例")
@RestController
@RequestMapping("/api/lowcode/rule-test-case")
@RequiredArgsConstructor
public class LowCodeRuleTestCaseController {

    private final LowCodeRuleTestCaseService ruleTestCaseService;

    @Operation(summary = "查询规则的测试用例列表")
    @GetMapping("/rule/{ruleId}")
    @PreAuthorize("@ss.hasPermi('lowcode:rule:test')")
    public CommonResult<List<LowCodeRuleTestCase>> list(@PathVariable Long ruleId) {
        return CommonResult.success(ruleTestCaseService.lambdaQuery()
                .eq(LowCodeRuleTestCase::getRuleId, ruleId)
                .orderByDesc(LowCodeRuleTestCase::getCreateTime)
                .list());
    }

    @Operation(summary = "获取测试用例详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lowcode:rule:test')")
    public CommonResult<LowCodeRuleTestCase> get(@PathVariable Long id) {
        return CommonResult.success(ruleTestCaseService.getById(id));
    }

    @Operation(summary = "新增测试用例")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('lowcode:rule:test')")
    public CommonResult<LowCodeRuleTestCase> create(@RequestBody LowCodeRuleTestCase testCase) {
        ruleTestCaseService.save(testCase);
        return CommonResult.success(testCase);
    }

    @Operation(summary = "更新测试用例")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lowcode:rule:test')")
    public CommonResult<Void> update(@PathVariable Long id, @RequestBody LowCodeRuleTestCase testCase) {
        testCase.setId(id);
        ruleTestCaseService.updateById(testCase);
        return CommonResult.success(null);
    }

    @Operation(summary = "删除测试用例")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lowcode:rule:test')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        ruleTestCaseService.removeById(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "运行规则的所有测试用例")
    @PostMapping("/rule/{ruleId}/run")
    @PreAuthorize("@ss.hasPermi('lowcode:rule:test')")
    public CommonResult<List<LowCodeRuleTestCaseService.RuleTestResult>> runTests(@PathVariable Long ruleId) {
        return CommonResult.success(ruleTestCaseService.runTests(ruleId));
    }

    @Operation(summary = "运行单个测试用例")
    @PostMapping("/{id}/run")
    @PreAuthorize("@ss.hasPermi('lowcode:rule:test')")
    public CommonResult<LowCodeRuleTestCaseService.RuleTestResult> runSingle(@PathVariable Long id) {
        return CommonResult.success(ruleTestCaseService.runSingleTest(id));
    }
}
