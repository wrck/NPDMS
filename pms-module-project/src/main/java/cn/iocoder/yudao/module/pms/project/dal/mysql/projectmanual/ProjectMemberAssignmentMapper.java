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

    /** 按项目+角色查询历史区间，保证同角色任一人员的重叠关系都被关闭。 */
    default List<ProjectMemberAssignmentDO> selectListByProjectAndRole(Long projectId, String memberRole) {
        return selectList(new LambdaQueryWrapperX<ProjectMemberAssignmentDO>()
                .eq(ProjectMemberAssignmentDO::getProjectId, projectId)
                .eq(ProjectMemberAssignmentDO::getMemberRole, memberRole)
                .orderByAsc(ProjectMemberAssignmentDO::getEffectiveFrom));
    }
}
