package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskRetiredDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectTaskGovernanceGuardQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * PMS 项目任务 WBS Mapper
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Mapper
@Deprecated
public interface ProjectTaskMapper extends BaseMapperX<ProjectTaskRetiredDO> {

    default PageResult<ProjectTaskRetiredDO> selectPageRetired(ProjectTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectTaskRetiredDO>()
                .eqIfPresent(ProjectTaskRetiredDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(ProjectTaskRetiredDO::getParentId, reqVO.getParentId())
                .likeIfPresent(ProjectTaskRetiredDO::getName, reqVO.getName())
                .likeIfPresent(ProjectTaskRetiredDO::getCode, reqVO.getCode())
                .eqIfPresent(ProjectTaskRetiredDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectTaskRetiredDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(ProjectTaskRetiredDO::getAssigneeUserId, reqVO.getAssigneeUserId())
                .orderByDesc(ProjectTaskRetiredDO::getId));
    }

    default ProjectTaskRetiredDO selectByProjectIdAndCodeRetired(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<ProjectTaskRetiredDO>()
                .eq(ProjectTaskRetiredDO::getProjectId, projectId)
                .eq(ProjectTaskRetiredDO::getCode, code));
    }

    default List<ProjectTaskRetiredDO> selectListByProjectIdRetired(Long projectId) {
        return selectList(ProjectTaskRetiredDO::getProjectId, projectId);
    }

    default List<ProjectTaskRetiredDO> selectListByParentIdRetired(Long parentId) {
        return selectList(ProjectTaskRetiredDO::getParentId, parentId);
    }

    default List<ProjectTaskRetiredDO> selectListByRootIdRetired(Long rootId) {
        return selectList(ProjectTaskRetiredDO::getRootId, rootId);
    }

    default List<ProjectTaskRetiredDO> selectListByPathPrefixRetired(String pathPrefix) {
        return selectList(new LambdaQueryWrapperX<ProjectTaskRetiredDO>()
                .likeRight(ProjectTaskRetiredDO::getPath, pathPrefix));
    }

    default List<ProjectTaskRetiredDO> selectListForGovernanceGuardRetired(ProjectTaskGovernanceGuardQuery query) {
        if (query.projectIds().isEmpty()) {
            return List.of();
        }
        return selectListForGovernanceGuardQueryRetired(query);
    }

    List<ProjectTaskRetiredDO> selectListForGovernanceGuardQueryRetired(
            @Param("query") ProjectTaskGovernanceGuardQuery query);

}
