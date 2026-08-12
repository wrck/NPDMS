package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceApproveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernancePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectGovernanceActionDO;

/**
 * PMS 项目治理动作 Service 接口（FR-PROJ-022 / T-V2-PROJ-003）
 * <p>
 * 动作类型：ROLLBACK 回退总部重新指派 / DIRECT_CLOSE 直接关闭
 * 状态机：0草稿 → 1已提交 → 2审批中 → 3已执行 → 4已驳回 → 5已撤回
 */
public interface ProjectGovernanceService {

    /**
     * 创建治理动作
     */
    Long createGovernanceAction(ProjectGovernanceSaveReqVO createReqVO);

    /**
     * 更新治理动作（仅草稿态）
     */
    void updateGovernanceAction(ProjectGovernanceSaveReqVO updateReqVO);

    /**
     * 删除治理动作（仅草稿/已驳回态）
     */
    void deleteGovernanceAction(Long id);

    /**
     * 获得分页
     */
    PageResult<ProjectGovernanceActionDO> getGovernanceActionPage(ProjectGovernancePageReqVO pageReqVO);

    /**
     * 获得详情
     */
    ProjectGovernanceActionDO getGovernanceAction(Long id);

    /**
     * 提交（0草稿 → 1已提交）
     */
    void submitGovernanceAction(Long id);

    /**
     * 审批执行（1已提交/2审批中 → 3已执行/4已驳回）
     * PASS 时执行回退或关闭动作；REJECT 驳回；RETURN 退回草稿
     */
    void approveGovernanceAction(ProjectGovernanceApproveReqVO reqVO);

    /**
     * 撤回（1已提交/2审批中 → 5已撤回）
     */
    void withdrawGovernanceAction(Long id);

}
