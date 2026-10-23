package cn.iocoder.yudao.module.pms.cutover.service.checklist.query;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext;

/** checklistId 为空读当前修订，否则读取同一任务中的指定历史修订。 */
public record CutoverChecklistReadQuery(CutoverChecklistContext context, Long taskId, Long checklistId) { }
