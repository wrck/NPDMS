package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CutoverTaskMutation;
import java.util.List;
import java.util.Map;

/** Existing generate/rematch commands expose their matching input without splitting the command. */
public interface CutoverChecklistMatchMutation extends CutoverTaskMutation {
    Long expectedAssessmentVersion();
    List<String> deviceTypeCodes();
    Map<String, GenerateChecklistCommand.SelectedDefinition> selectedConflictDefinitions();
}
