package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy.Option;
import java.util.List;

/** P3 type choices from the task's frozen CUT configuration and the current checklist selection. */
public record CutoverChecklistMatchingOptions(Long configurationRevisionId, Integer configurationRevisionNo,
                                               List<Option> cutoverTypes, List<Option> networkModes,
                                               List<Option> deviceTypes, cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistMatchInput matchingInput) {
}
