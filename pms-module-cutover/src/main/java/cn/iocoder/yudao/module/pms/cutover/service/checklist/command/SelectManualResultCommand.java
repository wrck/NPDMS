package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;

public record SelectManualResultCommand(Long tenantId, Long actorId, Long taskId,
                                        Integer expectedTaskVersion, Long checklistId,
                                        Integer expectedChecklistVersion, Long expectedProjectScopeVersion,
                                        String stableItemKey, CutoverChecklistFilePort.FileHandle fileHandle,
                                        String factDescription) {
}
