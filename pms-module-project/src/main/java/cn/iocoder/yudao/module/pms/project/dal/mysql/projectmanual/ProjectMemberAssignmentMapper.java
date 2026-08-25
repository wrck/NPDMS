package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentServiceManagerAssignmentsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentMemberResponsibilityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectServiceManagerIntervalClose;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ServiceManagerResponsibilityPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    List<ProjectMemberAssignmentDO> selectCurrentResponsibilityForUpdate(
            @Param("query") CurrentMemberResponsibilityQuery query);

    List<ProjectMemberAssignmentDO> selectActiveByUserForUpdate(
            @Param("query") ActiveProjectMemberForUpdateQuery query);

    Long selectResponsibilityNodeCount(@Param("query") ServiceManagerResponsibilityPageQuery query);

    List<ProjectMasterDO> selectResponsibilityNodePage(
            @Param("query") ServiceManagerResponsibilityPageQuery query);

    List<ProjectMemberAssignmentDO> selectCurrentServiceManagerAssignments(
            @Param("query") CurrentServiceManagerAssignmentsQuery query);

    int closeEffectiveServiceManagerAssignments(
            @Param("query") ProjectServiceManagerIntervalClose query);

    default List<ProjectMemberAssignmentDO> selectActiveForAssignmentState(ProjectAssignmentStateQuery query) {
        return selectList(new LambdaQueryWrapperX<ProjectMemberAssignmentDO>()
                .eq(ProjectMemberAssignmentDO::getProjectId, query.projectId())
                .eq(ProjectMemberAssignmentDO::getStatus, "ACTIVE")
                .and(wrapper -> wrapper.isNull(ProjectMemberAssignmentDO::getEffectiveFrom)
                        .or().le(ProjectMemberAssignmentDO::getEffectiveFrom, query.effectiveAt()))
                .and(wrapper -> wrapper.isNull(ProjectMemberAssignmentDO::getEffectiveTo)
                        .or().gt(ProjectMemberAssignmentDO::getEffectiveTo, query.effectiveAt()))
                .orderByAsc(ProjectMemberAssignmentDO::getMemberRole, ProjectMemberAssignmentDO::getId));
    }

    default List<ProjectMemberAssignmentDO> selectActiveByUser(ActiveProjectMemberQuery query) {
        return selectList(new LambdaQueryWrapperX<ProjectMemberAssignmentDO>()
                .eq(ProjectMemberAssignmentDO::getTenantId, query.tenantId())
                .eq(ProjectMemberAssignmentDO::getUserId, query.userId())
                .eq(ProjectMemberAssignmentDO::getStatus, "ACTIVE")
                .and(wrapper -> wrapper.isNull(ProjectMemberAssignmentDO::getEffectiveFrom)
                        .or().le(ProjectMemberAssignmentDO::getEffectiveFrom, query.effectiveAt()))
                .and(wrapper -> wrapper.isNull(ProjectMemberAssignmentDO::getEffectiveTo)
                        .or().gt(ProjectMemberAssignmentDO::getEffectiveTo, query.effectiveAt()))
                .orderByAsc(ProjectMemberAssignmentDO::getProjectId, ProjectMemberAssignmentDO::getId));
    }
}
