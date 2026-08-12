package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskApproveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 割接任务 Service 接口（FR-CUT-001 / FR-CUT-002 / FR-CUT-003 / FR-CUT-006）。
 * <p>
 * 割接任务编码在项目内唯一；状态变更使用 {@link cn.iocoder.yudao.module.pms.cutover.domain.CutTaskStatusRules} 校验；
 * 发起割接前必须满足前置门禁 {@link #validateProjectCutoverReady}。
 */
public interface CutTaskService {

    /**
     * 创建割接任务
     *
     * @param createReqVO 创建信息
     * @return 割接任务编号
     */
    Long createCutTask(@Valid CutTaskSaveReqVO createReqVO);

    /**
     * 更新割接任务
     *
     * @param updateReqVO 更新信息
     */
    void updateCutTask(@Valid CutTaskSaveReqVO updateReqVO);

    /**
     * 删除割接任务
     *
     * @param id 割接任务编号
     */
    void deleteCutTask(Long id);

    /**
     * 查询割接任务详情
     *
     * @param id 割接任务编号
     * @return 割接任务对象
     */
    CutTaskDO getCutTask(Long id);

    /**
     * 校验割接任务存在
     *
     * @param id 割接任务编号
     * @return 割接任务对象
     */
    CutTaskDO validateCutTaskExists(Long id);

    /**
     * 分页查询割接任务
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<CutTaskDO> getCutTaskPage(CutTaskPageReqVO pageReqVO);

    /**
     * 按项目查询割接任务列表
     *
     * @param projectId 项目编号
     * @return 割接任务列表
     */
    List<CutTaskDO> getCutTaskListByProject(Long projectId);

    /**
     * 校验项目割接前置门禁（FR-CUT-001）。
     * <p>
     * 前序必填、测试、方案审批和资源准备全部通过时才允许发起割接流程。
     *
     * @param projectId 项目编号
     */
    void validateProjectCutoverReady(Long projectId);

    /**
     * 提交评审（0草稿 → 2待评审）
     *
     * @param id 割接任务编号
     */
    void submitForReview(Long id);

    /**
     * 评审通过（2待评审 → 3待执行）
     *
     * @param reqVO 评审请求
     */
    void approve(@Valid CutTaskApproveReqVO reqVO);

    /**
     * 评审驳回（2待评审 → 1准备中）
     *
     * @param reqVO 评审请求
     */
    void reject(@Valid CutTaskApproveReqVO reqVO);

    /**
     * 开始执行（3待执行 → 4执行中）
     *
     * @param id 割接任务编号
     */
    void startExecution(Long id);

    /**
     * 完成执行（4执行中 → 5稳定观察）
     *
     * @param id 割接任务编号
     */
    void completeExecution(Long id);

    /**
     * 开始观察（4执行中 → 5稳定观察）
     *
     * @param id 割接任务编号
     */
    void startObservation(Long id);

    /**
     * 完成观察（5稳定观察 → 6已完成）
     *
     * @param id 割接任务编号
     */
    void completeObservation(Long id);

    /**
     * 回退（4执行中 → 7已回退）
     *
     * @param id 割接任务编号
     */
    void rollback(Long id);

    /**
     * 终止（任意非终态 → 8已终止）
     *
     * @param id 割接任务编号
     */
    void terminate(Long id);
}
