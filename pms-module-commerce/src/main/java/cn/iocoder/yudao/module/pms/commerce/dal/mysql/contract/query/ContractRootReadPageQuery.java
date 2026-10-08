package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

import java.util.List;

/** Bounded keyset read of currently authorized contract roots, in native directory order. */
public record ContractRootReadPageQuery(Long tenantId, List<String> companyCodes, List<Long> projectIds,
                                       String afterCode, Long afterId, int limit) {
}
