package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeRuleTestCase;

import java.util.List;

/**
 * 规则测试用例服务（批次3-T3）。
 *
 * <p>提供测试用例 CRUD + 批量运行测试，验证规则定义的正确性。</p>
 */
public interface LowCodeRuleTestCaseService extends IService<LowCodeRuleTestCase> {

    /**
     * 运行指定规则的所有启用测试用例。
     *
     * @param ruleId 规则 ID
     * @return 测试结果列表
     */
    List<RuleTestResult> runTests(Long ruleId);

    /**
     * 运行单个测试用例。
     *
     * @param testCaseId 测试用例 ID
     * @return 测试结果
     */
    RuleTestResult runSingleTest(Long testCaseId);

    /**
     * 测试结果。
     */
    class RuleTestResult {
        /** 测试用例 ID */
        private Long testCaseId;
        /** 测试用例名称 */
        private String testCaseName;
        /** 规则 ID */
        private Long ruleId;
        /** 状态：PASS | FAIL | ERROR */
        private String status;
        /** 实际输出 */
        private Object actualOutput;
        /** 期望输出 */
        private Object expectedOutput;
        /** 断言模式 */
        private String assertionMode;
        /** 失败原因（FAIL/ERROR 时填写） */
        private String message;
        /** 执行耗时（ms） */
        private long durationMs;

        public Long getTestCaseId() { return testCaseId; }
        public void setTestCaseId(Long testCaseId) { this.testCaseId = testCaseId; }
        public String getTestCaseName() { return testCaseName; }
        public void setTestCaseName(String testCaseName) { this.testCaseName = testCaseName; }
        public Long getRuleId() { return ruleId; }
        public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Object getActualOutput() { return actualOutput; }
        public void setActualOutput(Object actualOutput) { this.actualOutput = actualOutput; }
        public Object getExpectedOutput() { return expectedOutput; }
        public void setExpectedOutput(Object expectedOutput) { this.expectedOutput = expectedOutput; }
        public String getAssertionMode() { return assertionMode; }
        public void setAssertionMode(String assertionMode) { this.assertionMode = assertionMode; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }
}
