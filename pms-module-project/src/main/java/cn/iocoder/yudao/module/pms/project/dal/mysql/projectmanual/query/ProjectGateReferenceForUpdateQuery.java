package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.util.List;

/** 一组已锁Gate的冻结Reference查询。 */
public record ProjectGateReferenceForUpdateQuery(Long tenantId, List<Long> gateIds) {
}
