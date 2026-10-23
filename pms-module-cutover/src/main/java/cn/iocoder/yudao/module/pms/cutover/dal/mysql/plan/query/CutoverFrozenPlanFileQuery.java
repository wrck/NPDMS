package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query;

import lombok.Builder;

@Builder
public record CutoverFrozenPlanFileQuery(Long tenantId, Long taskId, Long artifactId,
                                         Integer versionNo, String referenceKey) {}
