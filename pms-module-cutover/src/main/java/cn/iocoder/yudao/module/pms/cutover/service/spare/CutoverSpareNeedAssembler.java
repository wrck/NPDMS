package cn.iocoder.yudao.module.pms.cutover.service.spare;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistItemsQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot.AssessmentNeedSource;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot.ChecklistRiskNeedSource;

import java.util.ArrayList;
import java.util.List;

/** 从当前P2/P3权威行形成CUT-08备件需求；不读取检查项结果。 */
public final class CutoverSpareNeedAssembler {
    private final CutoverAssessmentMapper assessmentMapper;
    private final CutoverChecklistMapper checklistMapper;
    private final CutoverChecklistItemMapper checklistItemMapper;

    public CutoverSpareNeedAssembler(CutoverAssessmentMapper assessmentMapper,
            CutoverChecklistMapper checklistMapper, CutoverChecklistItemMapper checklistItemMapper) {
        this.assessmentMapper = assessmentMapper;
        this.checklistMapper = checklistMapper;
        this.checklistItemMapper = checklistItemMapper;
    }

    public SpareNeedSnapshot assemble(long tenantId, CutoverTaskDO task) {
        if (task == null || task.getId() == null || !Long.valueOf(tenantId).equals(task.getTenantId())) {
            throw corrupted("task");
        }
        List<SpareNeedSnapshot.NeedSource> sources = new ArrayList<>();
        if (task.getCurrentAssessmentId() != null) {
            CutoverAssessmentDO assessment = assessmentMapper.selectById(task.getCurrentAssessmentId());
            require(assessment != null && Long.valueOf(tenantId).equals(assessment.getTenantId())
                    && task.getId().equals(assessment.getCutoverTaskId())
                    && Integer.valueOf(1).equals(assessment.getCurrentMarker())
                    && "SUBMITTED".equals(assessment.getAssessmentStatus())
                    && assessment.getAssessmentVersion() != null && assessment.getAssessmentVersion() > 0,
                    "assessment identity");
            Boolean applied = assessment.getSparePartAppliedFlag();
            require(applied != null, "sparePartApplied");
            if (applied) {
                sources.add(new AssessmentNeedSource(assessment.getId(), assessment.getAssessmentVersion(), true));
            }
        }
        CutoverChecklistDO checklist = checklistMapper.selectCurrent(new CutoverChecklistRowQuery(tenantId, task.getId(), null));
        if (checklist != null) {
            require(Long.valueOf(tenantId).equals(checklist.getTenantId())
                    && task.getId().equals(checklist.getCutoverTaskId())
                    && Integer.valueOf(1).equals(checklist.getCurrentMarker()), "checklist identity");
            List<CutoverChecklistItemDO> matched = checklistItemMapper
                    .selectListByChecklist(new CutoverChecklistItemsQuery(tenantId, checklist.getId())).stream()
                    .filter(item -> "MAJOR_PROJECT_SPARES".equals(item.getStableItemKey()))
                    .filter(item -> Boolean.TRUE.equals(item.getApplicableFlag())).toList();
            require(matched.size() <= 1, "checklist risk duplicate");
            if (!matched.isEmpty()) {
                CutoverChecklistItemDO item = matched.getFirst();
                require(item.getId() != null && item.getVersion() != null && item.getVersion() >= 0,
                        "checklist risk identity");
                sources.add(new ChecklistRiskNeedSource(item.getId(), item.getVersion() == null ? null : item.getVersion().longValue(),
                        item.getStableItemKey(), true));
            }
        }
        return new SpareNeedSnapshot(!sources.isEmpty(), sources);
    }

    private static void require(boolean condition, String field) {
        if (!condition) throw corrupted(field);
    }

    private static IllegalStateException corrupted(String field) {
        return new IllegalStateException("spare need owner data corrupted: " + field);
    }
}
