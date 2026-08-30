package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import java.util.Set;

public record AuthorityCandidateVisibleQuery(Long tenantId, Set<String> companyCodes, String objectType,
                                             String candidateStatus, int offset, int limit) {
}
