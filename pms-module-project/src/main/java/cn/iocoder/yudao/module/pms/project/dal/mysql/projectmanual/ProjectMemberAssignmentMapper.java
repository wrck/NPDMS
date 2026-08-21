package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目成员角色区间 Mapper（F-PM01 / V57）
 */
@Mapper
public interface ProjectMemberAssignmentMapper extends BaseMapperX<ProjectMemberAssignmentDO> {

    /**
     * 按项目查询成员区间（生效时间升序，含历史）
     */
    default List<ProjectMemberAssignmentDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectMemberAssignmentDO>()
                .eq(ProjectMemberAssignmentDO::getProjectId, projectId)
                .orderByAsc(ProjectMemberAssignmentDO::getEffectiveFrom)
                .orderByAsc(ProjectMemberAssignmentDO::getId));
    }

    /**
     * 按项目+用户+角色查询历史区间（重叠判定输入）
     */
    default List<ProjectMemberAssignmentDO> selectListByRole(Long projectId, Long userId, String memberRole) {
        return selectList(new LambdaQueryWrapperX<ProjectMemberAssignmentDO>()
                .eq(ProjectMemberAssignmentDO::getProjectId, projectId)
                .eq(ProjectMemberAssignmentDO::getUserId, userId)
                .eq(ProjectMemberAssignmentDO::getMemberRole, memberRole)
                .orderByAsc(ProjectMemberAssignmentDO::getEffectiveFrom));
    }
}
