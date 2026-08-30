package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import java.util.List;

public record AuthorityScopeDetailsQuery(Long tenantId, List<Long> scopeIds) {
    public AuthorityScopeDetailsQuery {
        scopeIds = scopeIds == null ? List.of() : List.copyOf(scopeIds);
    }
}
