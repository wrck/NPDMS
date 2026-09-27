package cn.iocoder.yudao.module.pms.platform.api.businessmodel.result;

/** 业务结果语义（沿用既有裁定）：支持新形成、复用、固定结果及历史事实，不能只剩一个布尔值。 */
public enum ResultSemantics {
    NEW_RESULT, REUSE_EXISTING, PINNED_RESULT, CURRENT_VALID, HISTORICAL_FACT
}
