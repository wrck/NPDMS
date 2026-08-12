package cn.iocoder.yudao.module.pms.project.service.planchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeApproveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangePhaseSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangeRequestDO;

import java.util.List;

/**
 * PMS 项目计划变更审批 Service 接口（FR-PROJ-020 / T-V2-PROJ-003）
 * <p>
 * 状态机：0草稿 → 1已提交 → 2审批中 → 3已通过 → 4已驳回 → 5已撤回 → 6已终止
 */
public interface PlanChangeService {

    /**
     * 创建计划变更（含阶段快照）
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createPlanChange(PlanChangeSaveReqVO createReqVO);

    /**
     * 更新计划变更（仅草稿态可改）
     *
     * @param updateReqVO 更新信息
     */
    void updatePlanChange(PlanChangeSaveReqVO updateReqVO);

    /**
     * 删除计划变更（仅草稿/已驳回态可删）
     *
     * @param id 编号
     */
    void deletePlanChange(Long id);

    /**
     * 获得分页
     */
    PageResult<PlanChangeRequestDO> getPlanChangePage(PlanChangePageReqVO pageReqVO);

    /**
     * 获得详情
     */
    PlanChangeRequestDO getPlanChange(Long id);

    /**
     * 获得阶段快照列表
     */
    List<PlanChangePhaseSnapshotDO> getPhaseSnapshots(Long changeRequestId);

    /**
     * 提交（0草稿 → 1已提交）
     */
    void submitPlanChange(Long id);

    /**
     * 审批（2审批中 → 3已通过/4已驳回）
     * 通过时生成新基线版本号，但实际应用阶段需调用 applyPlanChange
     */
    void approvePlanChange(PlanChangeApproveReqVO reqVO);

    /**
     * 撤回（1已提交/2审批中 → 5已撤回）
     */
    void withdrawPlanChange(Long id);

    /**
     * 终止（任意状态 → 6已终止）
     */
    void terminatePlanChange(Long id);

    /**
     * 应用变更到项目阶段（3已通过 → 写入阶段新计划时间，生成新基线）
     */
    void applyPlanChange(Long id);

}
