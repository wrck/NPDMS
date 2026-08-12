package cn.iocoder.yudao.module.pms.project.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectAssignManagerReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectClassifyReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;

import jakarta.validation.Valid;

/**
 * PMS 项目 Service 接口
 */
public interface ProjectService {

    /**
     * 创建项目
     *
     * @param createReqVO 项目信息
     * @return 项目编号
     */
    Long createProject(@Valid ProjectSaveReqVO createReqVO);

    /**
     * 更新项目
     *
     * @param updateReqVO 项目信息
     */
    void updateProject(@Valid ProjectSaveReqVO updateReqVO);

    /**
     * 删除项目
     *
     * @param id 项目编号
     */
    void deleteProject(Long id);

    /**
     * 获得项目
     *
     * @param id 项目编号
     * @return 项目信息
     */
    ProjectDO getProject(Long id);

    /**
     * 获得项目分页列表
     *
     * @param pageReqVO 分页条件
     * @return 项目分页列表
     */
    PageResult<ProjectDO> getProjectPage(ProjectPageReqVO pageReqVO);

    /**
     * 项目分类
     *
     * @param reqVO 分类信息
     */
    void classifyProject(@Valid ProjectClassifyReqVO reqVO);

    /**
     * 指派项目经理
     *
     * @param reqVO 指派信息
     */
    void assignProjectManager(@Valid ProjectAssignManagerReqVO reqVO);

}
