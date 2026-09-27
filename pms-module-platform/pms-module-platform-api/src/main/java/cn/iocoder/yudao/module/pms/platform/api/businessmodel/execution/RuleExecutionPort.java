package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

/** 规则执行端口：执行器可更换；无法表达的规则在能力校验阶段拒绝，不翻译成近似条件。 */
public interface RuleExecutionPort {

    RuleVerdict evaluate(RuleSemanticRequest request);
}
