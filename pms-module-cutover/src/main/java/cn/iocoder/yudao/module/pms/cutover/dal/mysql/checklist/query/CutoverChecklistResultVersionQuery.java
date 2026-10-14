package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

import lombok.Builder;

@Builder
public record CutoverChecklistResultVersionQuery(Long tenantId, Long checklistItemId, Integer resultVersion) {}
