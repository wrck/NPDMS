package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

import java.time.LocalDateTime;
import java.util.Set;

public record ProjectContractFactsQuery(Long tenantId, Set<Long> projectIds, LocalDateTime effectiveAt) { }
