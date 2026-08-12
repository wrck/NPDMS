package cn.iocoder.yudao.module.pms.project.service.projecttree;

import cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo.ProjectTreeCreateChildReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo.ProjectTreeMoveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo.ProjectTreeNodeRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * PMS 项目树 Service 接口
 *
 * 项目树采用物化路径模型，通过 ProjectDO 的 parent_id/root_id/path/depth 字段承载。
 */
public interface ProjectTreeService {

    /**
     * 创建子项目（挂到指定父项目下）
     *
     * @param reqVO 创建信息
     * @return 子项目编号
     */
    Long createChildProject(@Valid ProjectTreeCreateChildReqVO reqVO);

    /**
     * 移动子树到新的父项目下
     *
     * @param reqVO 移动信息
     */
    void moveSubtree(@Valid ProjectTreeMoveReqVO reqVO);

    /**
     * 获取指定根项目的整棵项目树
     *
     * @param rootProjectId 根项目编号
     * @return 树形结构
     */
    ProjectTreeNodeRespVO getProjectTree(Long rootProjectId);

    /**
     * 获取指定项目的所有后代（不含自身）
     *
     * @param projectId 项目编号
     * @return 后代项目列表
     */
    List<ProjectDO> getDescendants(Long projectId);

    /**
     * 获取指定项目从根到自身的路径
     *
     * @param projectId 项目编号
     * @return 路径项目列表（按从根到自身的顺序）
     */
    List<ProjectDO> getProjectPath(Long projectId);

}
