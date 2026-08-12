package cn.iocoder.yudao.module.pms.project.domain.customer;

import java.util.Objects;

/**
 * 客户聚合内不依赖持久化的核心业务规则。
 */
public final class CustomerRules {

    private CustomerRules() {
    }

    public static void requireUnchangedCode(String currentCode, String requestedCode) {
        if (!Objects.equals(currentCode, requestedCode)) {
            throw new IllegalArgumentException("客户编码创建后不可修改");
        }
    }

    public static void requirePrimaryContactAvailable(boolean requestedPrimary, boolean activePrimaryExists) {
        if (requestedPrimary && activePrimaryExists) {
            throw new IllegalStateException("同一客户只能存在一个有效主联系人");
        }
    }

}
