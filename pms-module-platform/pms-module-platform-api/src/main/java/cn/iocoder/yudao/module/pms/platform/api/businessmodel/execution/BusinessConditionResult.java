package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

/** The semantic verdict and exact observed aggregate/evidence basis are separate versions. */
public record BusinessConditionResult(RuleVerdict verdict, String factVersion) { }
