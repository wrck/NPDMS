package cn.iocoder.yudao.module.pms.project.dal.mysql.projectteam;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目团队成员 Mapper
 */
@Mapper
public interface ProjectTeamMemberMapper extends BaseMapperX<ProjectTeamMemberDO> {

    default PageResult<ProjectTeamMemberDO> selectPage(ProjectTeamMemberPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectTeamMemberDO>()
                .eqIfPresent(ProjectTeamMemberDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(ProjectTeamMemberDO::getUserId, reqVO.getUserId())
                .eqIfPresent(ProjectTeamMemberDO::getRoleCode, reqVO.getRoleCode())
                .eqIfPresent(ProjectTeamMemberDO::getStatus, reqVO.getStatus())
                .orderByDesc(ProjectTeamMemberDO::getId));
    }

    default List<ProjectTeamMemberDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectTeamMemberDO>()
                .eq(ProjectTeamMemberDO::getProjectId, projectId)
                .orderByAsc(ProjectTeamMemberDO::getId));
    }

    default ProjectTeamMemberDO selectByProjectIdAndUserIdAndRoleCode(Long projectId, Long userId, String roleCode) {
        return selectOne(new LambdaQueryWrapperX<ProjectTeamMemberDO>()
                .eq(ProjectTeamMemberDO::getProjectId, projectId)
                .eq(ProjectTeamMemberDO::getUserId, userId)
                .eq(ProjectTeamMemberDO::getRoleCode, roleCode));
    }

}
