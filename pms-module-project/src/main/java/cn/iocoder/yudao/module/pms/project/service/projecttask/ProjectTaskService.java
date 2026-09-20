package cn.iocoder.yudao.module.pms.project.service.projecttask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskMoveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskTreeRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskRetiredDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * PMS 项目任务 WBS Service 接口
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface ProjectTaskService {

    /**
     * 创建项目任务
     *
     * @param createReqVO 任务信息
     * @return 任务编号
     */
    Long createProjectTaskRetired(@Valid ProjectTaskSaveReqVO createReqVO);

    /**
     * 更新项目任务
     *
     * @param updateReqVO 任务信息
     */
    void updateProjectTaskRetired(@Valid ProjectTaskSaveReqVO updateReqVO);

    /**
     * 删除项目任务（存在子任务时拒绝删除）
     *
     * @param id 任务编号
     */
    void deleteProjectTaskRetired(Long id);

    /**
     * 获得项目任务
     *
     * @param id 任务编号
     * @return 任务信息
     */
    ProjectTaskRetiredDO getProjectTaskRetired(Long id);

    /**
     * 获得项目任务分页列表
     *
     * @param pageReqVO 分页条件
     * @return 任务分页列表
     */
    PageResult<ProjectTaskRetiredDO> getProjectTaskPageRetired(ProjectTaskPageReqVO pageReqVO);

    /**
     * 获取指定项目的任务树
     *
     * @param projectId 项目编号
     * @return 任务树（多根）
     */
    List<ProjectTaskTreeRespVO> getProjectTaskTreeRetired(Long projectId);

    /**
     * 获取指定任务的所有后代（不含自身）
     *
     * @param taskId 任务编号
     * @return 后代任务列表
     */
    List<ProjectTaskRetiredDO> getProjectTaskDescendantsRetired(Long taskId);

    /**
     * 移动任务到新的父任务下
     *
     * @param reqVO 移动信息
     */
    void moveProjectTaskRetired(@Valid ProjectTaskMoveReqVO reqVO);

}
