package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目任务 WBS Mapper
 */
@Mapper
public interface ProjectTaskMapper extends BaseMapperX<ProjectTaskDO> {

    default PageResult<ProjectTaskDO> selectPage(ProjectTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectTaskDO>()
                .eqIfPresent(ProjectTaskDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(ProjectTaskDO::getParentId, reqVO.getParentId())
                .likeIfPresent(ProjectTaskDO::getName, reqVO.getName())
                .likeIfPresent(ProjectTaskDO::getCode, reqVO.getCode())
                .eqIfPresent(ProjectTaskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectTaskDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(ProjectTaskDO::getAssigneeUserId, reqVO.getAssigneeUserId())
                .orderByDesc(ProjectTaskDO::getId));
    }

    default ProjectTaskDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<ProjectTaskDO>()
                .eq(ProjectTaskDO::getProjectId, projectId)
                .eq(ProjectTaskDO::getCode, code));
    }

    default List<ProjectTaskDO> selectListByProjectId(Long projectId) {
        return selectList(ProjectTaskDO::getProjectId, projectId);
    }

    default List<ProjectTaskDO> selectListByParentId(Long parentId) {
        return selectList(ProjectTaskDO::getParentId, parentId);
    }

    default List<ProjectTaskDO> selectListByRootId(Long rootId) {
        return selectList(ProjectTaskDO::getRootId, rootId);
    }

    default List<ProjectTaskDO> selectListByPathPrefix(String pathPrefix) {
        return selectList(new LambdaQueryWrapperX<ProjectTaskDO>()
                .likeRight(ProjectTaskDO::getPath, pathPrefix));
    }

}
