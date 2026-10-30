package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.List;

/** P3 可修订的实施信息；等级仍由已提交的 P2 评估确定。 */
public record CutoverChecklistMatchInput(String cutoverType, String networkMode, List<String> deviceTypeCodes) {
    public CutoverChecklistMatchInput {
        deviceTypeCodes = deviceTypeCodes == null ? List.of() : List.copyOf(deviceTypeCodes);
    }
}
