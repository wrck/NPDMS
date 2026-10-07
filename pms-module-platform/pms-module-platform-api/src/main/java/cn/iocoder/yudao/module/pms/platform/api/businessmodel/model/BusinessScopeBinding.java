package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

/** Explicit data ownership mapping. A null field is legal only for explicit tenant scope. */
public record BusinessScopeBinding(String policyRef, String ownershipFieldCode) {
    public static BusinessScopeBinding tenant() { return new BusinessScopeBinding("tenant", null); }
}
