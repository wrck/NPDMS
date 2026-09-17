package cn.iocoder.yudao.module.pms.project.api.stageplan;

import java.time.LocalDate;
import java.util.List;

/** SOL 施工计划倒排消费的 PROJ 阶段计划契约：阶段事实只读与计划起止日期受控写入。 */
public interface ProjectStagePlanApi {

    /** 项目下阶段计划事实（按原排序返回）；含建议起止与计划起止日期、状态与版本。 */
    List<StagePlanFact> listStages(Long tenantId, Long projectId);

    /** 校验项目存在后按阶段ID写入计划起止日期；不修改状态与版本。返回更新行数。 */
    int applyPlanDates(Long tenantId, Long projectId, List<StagePlanDate> dates);

    record StagePlanFact(Long stageId, String code, String name, Integer sort,
                         LocalDate suggestedStartTime, LocalDate suggestedEndTime,
                         LocalDate planStartTime, LocalDate planEndTime,
                         String status, Integer version) {
    }

    record StagePlanDate(Long stageId, LocalDate planStartTime, LocalDate planEndTime) {
    }
}
