package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.List;

public record SaveChecklistCommand(Long tenantId, Long actorId, Long taskId,
                                   Long expectedTaskVersion, Long checklistId,
                                   Long expectedChecklistVersion, Long expectedProjectScopeVersion,
                                   List<DirectAnswer> answers) {
    public SaveChecklistCommand {
        answers = answers == null ? List.of() : List.copyOf(answers);
    }

    public record DirectAnswer(String stableItemKey, String answerSnapshot) {
    }
}
