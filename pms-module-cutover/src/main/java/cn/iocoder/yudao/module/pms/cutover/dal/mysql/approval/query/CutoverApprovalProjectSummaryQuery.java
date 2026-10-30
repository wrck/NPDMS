package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import java.util.Set;

public record CutoverApprovalProjectSummaryQuery(Long tenantId, Set<String> processInstanceIds) { }
