package cn.iocoder.yudao.module.pms.project.dal.mysql.projectteam;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberRetiredDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目团队成员 Mapper
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Mapper
@Deprecated
public interface ProjectTeamMemberMapper extends BaseMapperX<ProjectTeamMemberRetiredDO> {

    default PageResult<ProjectTeamMemberRetiredDO> selectPageRetired(ProjectTeamMemberPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectTeamMemberRetiredDO>()
                .eqIfPresent(ProjectTeamMemberRetiredDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(ProjectTeamMemberRetiredDO::getUserId, reqVO.getUserId())
                .eqIfPresent(ProjectTeamMemberRetiredDO::getRoleCode, reqVO.getRoleCode())
                .eqIfPresent(ProjectTeamMemberRetiredDO::getStatus, reqVO.getStatus())
                .orderByDesc(ProjectTeamMemberRetiredDO::getId));
    }

    default List<ProjectTeamMemberRetiredDO> selectListByProjectIdRetired(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectTeamMemberRetiredDO>()
                .eq(ProjectTeamMemberRetiredDO::getProjectId, projectId)
                .orderByAsc(ProjectTeamMemberRetiredDO::getId));
    }

    default ProjectTeamMemberRetiredDO selectByProjectIdAndUserIdAndRoleCodeRetired(Long projectId, Long userId, String roleCode) {
        return selectOne(new LambdaQueryWrapperX<ProjectTeamMemberRetiredDO>()
                .eq(ProjectTeamMemberRetiredDO::getProjectId, projectId)
                .eq(ProjectTeamMemberRetiredDO::getUserId, userId)
                .eq(ProjectTeamMemberRetiredDO::getRoleCode, roleCode));
    }

}
