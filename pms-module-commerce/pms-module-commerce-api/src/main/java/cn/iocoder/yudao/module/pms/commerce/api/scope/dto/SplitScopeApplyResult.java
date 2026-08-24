package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

import java.util.List;

public record SplitScopeApplyResult(boolean valid, boolean replayed, Long scopeVersion,
                                    List<AppliedScope> scopes, List<String> errors) {

    public record AppliedScope(String clientItemKey, Long projectId, Long scopeId) {
    }
}
