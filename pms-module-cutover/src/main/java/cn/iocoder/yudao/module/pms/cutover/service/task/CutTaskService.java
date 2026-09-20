package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskApproveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskRetiredDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 割接任务 Service 接口（FR-CUT-001 / FR-CUT-002 / FR-CUT-003 / FR-CUT-006）。
 * <p>
 * 割接任务编码在项目内唯一；状态变更使用 {@link cn.iocoder.yudao.module.pms.cutover.domain.CutTaskStatusRules} 校验；
 * 发起割接前必须满足前置门禁 {@link #validateProjectCutoverReadyRetired}。
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface CutTaskService {

    /**
     * 创建割接任务
     *
     * @param createReqVO 创建信息
     * @return 割接任务编号
     */
    Long createCutTaskRetired(@Valid CutTaskSaveReqVO createReqVO);

    /**
     * 更新割接任务
     *
     * @param updateReqVO 更新信息
     */
    void updateCutTaskRetired(@Valid CutTaskSaveReqVO updateReqVO);

    /**
     * 删除割接任务
     *
     * @param id 割接任务编号
     */
    void deleteCutTaskRetired(Long id);

    /**
     * 查询割接任务详情
     *
     * @param id 割接任务编号
     * @return 割接任务对象
     */
    CutTaskRetiredDO getCutTaskRetired(Long id);

    /**
     * 校验割接任务存在
     *
     * @param id 割接任务编号
     * @return 割接任务对象
     */
    CutTaskRetiredDO validateCutTaskExistsRetired(Long id);

    /**
     * 分页查询割接任务
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<CutTaskRetiredDO> getCutTaskPageRetired(CutTaskPageReqVO pageReqVO);

    /**
     * 按项目查询割接任务列表
     *
     * @param projectId 项目编号
     * @return 割接任务列表
     */
    List<CutTaskRetiredDO> getCutTaskListByProjectRetired(Long projectId);

    /**
     * 校验项目割接前置门禁（FR-CUT-001）。
     * <p>
     * 前序必填、测试、方案审批和资源准备全部通过时才允许发起割接流程。
     *
     * @param projectId 项目编号
     */
    void validateProjectCutoverReadyRetired(Long projectId);

    /**
     * 提交评审（0草稿 → 2待评审）
     *
     * @param id 割接任务编号
     */
    void submitForReviewRetired(Long id);

    /**
     * 评审通过（2待评审 → 3闭环中）
     *
     * @param reqVO 评审请求
     */
    void approveRetired(@Valid CutTaskApproveReqVO reqVO);

    /**
     * 评审驳回（2待评审 → 1准备中）
     *
     * @param reqVO 评审请求
     */
    void rejectRetired(@Valid CutTaskApproveReqVO reqVO);

}
