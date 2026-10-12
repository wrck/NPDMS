package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

import java.util.List;

/** Project read permission is checked by the Owner; company scope must remain nonempty. */
public record ProjectContractRelationPageQuery(Long tenantId, Long projectId,
                                              List<String> companyCodes, int offset, int limit) {}
