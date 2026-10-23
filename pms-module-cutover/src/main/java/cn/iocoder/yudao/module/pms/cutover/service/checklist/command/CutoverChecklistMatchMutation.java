package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import cn.iocoder.yudao.module.pms.cutover.service.task.command.CutoverTaskMutation;
import java.util.Map;

/** Existing generate/rematch commands expose their matching input without splitting the command. */
public interface CutoverChecklistMatchMutation extends CutoverTaskMutation {
    Long expectedAssessmentVersion();
    Long expectedProjectScopeVersion();
    CutoverChecklistMatchInput matchingInput();
    Map<String, GenerateChecklistCommand.SelectedDefinition> selectedConflictDefinitions();
}
