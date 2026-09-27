package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;

import java.util.Map;

/** 带版本的规则语义与类型化事实。相同受支持语义在不同执行器上结果必须一致。 */
public record RuleSemanticRequest(String ruleCode, String ruleVersion, Map<String, FactObservation> facts) {
}
