package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;

/** 人工结果的工作方式边界；外部查询另由其事实存储核验精确失败来源。 */
public final class CutoverChecklistEvidenceRules {
    private CutoverChecklistEvidenceRules() { }

    public static boolean allowsManual(CutoverChecklistItemDO item) {
        return java.util.Set.of("MANUAL", "EXTERNAL", "COLLECTION").contains(item.getWorkModeCode());
    }
}
