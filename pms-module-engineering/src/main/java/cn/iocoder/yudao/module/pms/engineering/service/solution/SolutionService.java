package cn.iocoder.yudao.module.pms.engineering.service.solution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionGenerateDraftReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;

import jakarta.validation.Valid;

/**
 * PMS 实施方案 Service 接口（FR-ENG-011 / FR-ENG-013）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 */
public interface SolutionService {

    Long createSolution(@Valid SolutionSaveReqVO createReqVO);

    void updateSolution(@Valid SolutionSaveReqVO updateReqVO);

    void deleteSolution(Long id);

    SolutionDO getSolution(Long id);

    PageResult<SolutionDO> getSolutionPage(SolutionPageReqVO pageReqVO);

    /**
     * 提交方案：草稿(0) → 已提交(1)
     */
    void submitSolution(Long id);

    /**
     * 开始评审：已提交(1) → 审批中(2)
     */
    void startReview(Long id);

    /**
     * 审批通过：审批中(2) → 已通过(3)，冻结基线版本
     */
    void approveSolution(@Valid SolutionApproveReqVO reqVO);

    /**
     * 审批驳回：审批中(2) → 已驳回(4)
     */
    void rejectSolution(@Valid SolutionApproveReqVO reqVO);

    /**
     * 撤回方案：审批中(2) → 已撤回(5)
     */
    void withdrawSolution(Long id);

    /**
     * 终止方案：审批中(2) → 已终止(6)
     */
    void terminateSolution(Long id);

    /**
     * 基于项目编号与方案编码生成方案草稿
     */
    Long generateDraft(@Valid SolutionGenerateDraftReqVO reqVO);
}
