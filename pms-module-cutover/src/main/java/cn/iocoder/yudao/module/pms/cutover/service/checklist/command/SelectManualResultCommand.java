package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;

public record SelectManualResultCommand(Long tenantId, Long actorId, Long taskId,
                                        Long expectedTaskVersion, Long checklistId,
                                        Long expectedChecklistVersion, Long expectedProjectScopeVersion,
                                        String stableItemKey, CutoverChecklistFilePort.FileHandle fileHandle,
                                        String factDescription) {
}
