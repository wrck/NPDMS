package com.dp.plat.lowcode.controller;

import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.engine.rule.RuleSetDefinition;
import com.dp.plat.lowcode.engine.rule.RuleSetOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 规则集编排 API（批次3-T1）。
 *
 * <p>提供规则集的执行接口，支持 THEN/WHEN/IF/SWITCH 四种编排语义，
 * 将决策表/表达式/LiteFlow EL/微流编排为复合规则集。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/lowcode/rule-set")
@RequiredArgsConstructor
public class LowCodeRuleSetController {

    private final RuleSetOrchestrator ruleSetOrchestrator;

    /**
     * 执行规则集。
     *
     * <p>请求体格式：
     * <pre>{@json
     * {
     *   "definition": {
     *     "code": "order-processing",
     *     "name": "订单处理规则集",
     *     "orchestration": "THEN",
     *     "nodes": [
     *       {"id":"check","type":"expression","expression":"amount > 100"},
     *       {"id":"discount","type":"decision_table","definition":"{...}"},
     *       {"id":"notify","type":"liteflow","el":"THEN(sendSms, sendEmail)"}
     *     ]
     *   },
     *   "inputs": {"amount": 150, "customerId": "C001"}
     * }
     * }</pre></p>
     *
     * @param request 含 definition + inputs 的请求体
     * @return 执行结果（含 finalResult 和 nodeResults trace）
     */
    @PostMapping("/execute")
    @PreAuthorize("@ss.hasPermi('lowcode:rule:execute')")
    public Result<RuleSetOrchestrator.RuleSetResult> execute(@RequestBody RuleSetExecuteRequest request) {
        log.info("执行规则集: code={}", request.getDefinition().getCode());
        RuleSetOrchestrator.RuleSetResult result =
                ruleSetOrchestrator.execute(request.getDefinition(), request.getInputs());
        if ("FAILED".equals(result.getStatus())) {
            return Result.fail(result.getErrorMessage());
        }
        return Result.ok(result);
    }

    /**
     * 规则集执行请求。
     */
    @lombok.Data
    public static class RuleSetExecuteRequest {
        /** 规则集定义 */
        private RuleSetDefinition definition;
        /** 输入参数 */
        private Map<String, Object> inputs;
    }
}
