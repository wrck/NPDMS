package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.engine.rule.RuleEngineService;
import com.dp.plat.lowcode.entity.LowCodeRule;
import com.dp.plat.lowcode.entity.LowCodeRuleTestCase;
import com.dp.plat.lowcode.mapper.LowCodeRuleTestCaseMapper;
import com.dp.plat.lowcode.service.LowCodeRuleService;
import com.dp.plat.lowcode.service.LowCodeRuleTestCaseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 规则测试用例服务实现（批次3-T3）。
 *
 * <p>运行测试用例时，根据规则类型（决策表/表达式）调用对应的执行器，
 * 然后按断言模式（EQUALS/CONTAINS/NOT_NULL）判定 PASS/FAIL。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeRuleTestCaseServiceImpl
        extends ServiceImpl<LowCodeRuleTestCaseMapper, LowCodeRuleTestCase>
        implements LowCodeRuleTestCaseService {

    private final RuleEngineService ruleEngineService;
    private final LowCodeRuleService ruleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RuleTestResult> runTests(Long ruleId) {
        List<LowCodeRuleTestCase> testCases = lambdaQuery()
                .eq(LowCodeRuleTestCase::getRuleId, ruleId)
                .eq(LowCodeRuleTestCase::getEnabled, true)
                .list();
        List<RuleTestResult> results = new ArrayList<>();
        for (LowCodeRuleTestCase tc : testCases) {
            results.add(runSingleTest(tc));
        }
        return results;
    }

    @Override
    public RuleTestResult runSingleTest(Long testCaseId) {
        LowCodeRuleTestCase tc = getById(testCaseId);
        if (tc == null) {
            throw new IllegalArgumentException("测试用例不存在: " + testCaseId);
        }
        return runSingleTest(tc);
    }

    @SuppressWarnings("unchecked")
    private RuleTestResult runSingleTest(LowCodeRuleTestCase tc) {
        RuleTestResult result = new RuleTestResult();
        result.setTestCaseId(tc.getId());
        result.setTestCaseName(tc.getName());
        result.setRuleId(tc.getRuleId());
        result.setAssertionMode(tc.getAssertionMode());

        long startMs = System.currentTimeMillis();
        try {
            // 加载规则定义
            LowCodeRule rule = ruleService.getById(tc.getRuleId());
            if (rule == null) {
                result.setStatus("ERROR");
                result.setMessage("规则不存在: " + tc.getRuleId());
                return result;
            }

            // 解析输入
            Map<String, Object> input = objectMapper.readValue(tc.getInputJson(),
                    new TypeReference<Map<String, Object>>() {});

            // 根据规则类型执行
            Object actualOutput;
            String ruleType = rule.getType();
            if ("DECISION_TABLE".equalsIgnoreCase(ruleType) || "decision_table".equalsIgnoreCase(ruleType)) {
                actualOutput = ruleEngineService.executeDecisionTable(rule.getDefinition(), input);
            } else if ("EXPRESSION".equalsIgnoreCase(ruleType) || "expression".equalsIgnoreCase(ruleType)) {
                actualOutput = ruleEngineService.executeExpression(rule.getDefinition(), input);
            } else if ("LITEFLOW".equalsIgnoreCase(ruleType) || "liteflow".equalsIgnoreCase(ruleType)) {
                actualOutput = ruleEngineService.executeLiteFlow(rule.getDefinition(), input);
            } else {
                result.setStatus("ERROR");
                result.setMessage("不支持的规则类型: " + ruleType);
                return result;
            }
            result.setActualOutput(actualOutput);

            // 解析期望输出
            Object expectedOutput = objectMapper.readValue(tc.getExpectedOutputJson(), Object.class);
            result.setExpectedOutput(expectedOutput);

            // 断言
            boolean pass = assertOutput(actualOutput, expectedOutput, tc.getAssertionMode());
            result.setStatus(pass ? "PASS" : "FAIL");
            if (!pass) {
                result.setMessage("断言失败: 实际=" + actualOutput + ", 期望=" + expectedOutput);
            }
        } catch (Exception e) {
            result.setStatus("ERROR");
            result.setMessage("执行异常: " + e.getMessage());
            log.error("测试用例 {} 执行异常: {}", tc.getId(), e.getMessage(), e);
        } finally {
            result.setDurationMs(System.currentTimeMillis() - startMs);
        }
        return result;
    }

    /** 断言输出 */
    @SuppressWarnings("unchecked")
    private boolean assertOutput(Object actual, Object expected, String mode) {
        if (mode == null) mode = "EQUALS";
        switch (mode.toUpperCase()) {
            case "NOT_NULL":
                return actual != null;
            case "CONTAINS":
                if (actual instanceof List && expected instanceof List) {
                    List<Object> actualList = (List<Object>) actual;
                    List<Object> expectedList = (List<Object>) expected;
                    return actualList.containsAll(expectedList);
                }
                return actual != null && actual.toString().contains(expected.toString());
            case "EQUALS":
            default:
                return Objects.equals(actual, expected);
        }
    }
}
