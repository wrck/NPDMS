package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.List;

public record SaveChecklistDeviceVersionsCommand(Long tenantId, Long actorId, Long taskId,
        Long expectedTaskVersion, Long checklistId, Long expectedChecklistVersion,
        Long expectedProjectScopeVersion, List<Device> devices) implements CutoverChecklistDraftMutation {
    public record Device(Long deviceId, String productModel, String currentSoftwareVersion,
                         String targetSoftwareVersion) { }
}
