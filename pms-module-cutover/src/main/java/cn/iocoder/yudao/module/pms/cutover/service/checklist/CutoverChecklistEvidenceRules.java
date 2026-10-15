package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;

/** 人工结果的工作方式边界；外部查询另由其事实存储核验精确失败来源。 */
public final class CutoverChecklistEvidenceRules {
    private CutoverChecklistEvidenceRules() { }

    public static boolean allowsManual(CutoverChecklistItemDO item, CutoverChecklistItemResultDO source) {
        if ("MANUAL".equals(item.getWorkModeCode()) || "EXTERNAL".equals(item.getWorkModeCode())) return true;
        if (!"COLLECTION".equals(item.getWorkModeCode()) || source == null) return false;
        String failure = "COLLECTION".equals(source.getResultSourceCode()) ? source.getLoadFailureCode()
                : "MANUAL".equals(source.getResultSourceCode()) ? source.getOriginalFailureCode() : null;
        return failure != null && !failure.isBlank();
    }
}
