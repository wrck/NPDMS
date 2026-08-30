package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import java.time.LocalDateTime;

public record AuthorityScopeReleaseUpdate(Long tenantId, Long scopeId, Integer expectedVersion,
                                          LocalDateTime effectiveTo, LocalDateTime updateTime) {
}
