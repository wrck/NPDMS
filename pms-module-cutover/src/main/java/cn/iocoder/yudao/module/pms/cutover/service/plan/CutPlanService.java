package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanApproveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutPlanDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 割接方案 Service 接口（FR-CUT-008 / FR-CUT-009）。
 * <p>
 * 方案编码在任务内唯一；评审通过后形成不可覆盖基线版本；
 * 未评审通过方案阻断割接执行。
 */
public interface CutPlanService {

    Long createCutPlan(@Valid CutPlanSaveReqVO createReqVO);

    void updateCutPlan(@Valid CutPlanSaveReqVO updateReqVO);

    void deleteCutPlan(Long id);

    CutPlanDO getCutPlan(Long id);

    CutPlanDO validateCutPlanExists(Long id);

    PageResult<CutPlanDO> getCutPlanPage(CutPlanPageReqVO pageReqVO);

    List<CutPlanDO> getCutPlanListByTask(Long taskId);

    /**
     * 提交评审（0草稿 → 1待评审）
     */
    void submitForReview(Long id);

    /**
     * 评审通过（1待评审 → 2已通过），并冻结基线版本
     */
    void approve(@Valid CutPlanApproveReqVO reqVO);

    /**
     * 评审驳回（1待评审 → 3已驳回）
     */
    void reject(@Valid CutPlanApproveReqVO reqVO);

    /**
     * 终止方案（任意非终态 → 4已终止）
     */
    void terminate(Long id);

    /**
     * 校验任务下存在已评审通过方案，供割接执行调用阻断后续受控动作。
     */
    void validateTaskPlanApproved(Long taskId);
}
