package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectMemberPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectMemberIdentityQuery;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentServiceManagerAssignmentsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentMemberResponsibilityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectManagerMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery;
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

    default PageResult<ProjectMemberAssignmentDO> selectMemberPage(ProjectMemberPageQuery query) {
        var conditions = new LambdaQueryWrapperX<ProjectMemberAssignmentDO>()
                .eq(ProjectMemberAssignmentDO::getTenantId, query.getTenantId())
                .eq(ProjectMemberAssignmentDO::getProjectId, query.getProjectId())
                .likeIfPresent(ProjectMemberAssignmentDO::getMemberName, query.getKeyword())
                .orderByAsc(ProjectMemberAssignmentDO::getMemberName, ProjectMemberAssignmentDO::getMemberRole)
                .orderByDesc(ProjectMemberAssignmentDO::getEffectiveFrom, ProjectMemberAssignmentDO::getId);
        if (query.getRole() != null) {
            conditions.in(ProjectMemberAssignmentDO::getMemberRole,
                    cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.storedCodes(query.getRole()));
        }
        if ("CURRENT".equals(query.getState())) {
            conditions.eq(ProjectMemberAssignmentDO::getStatus, "ACTIVE")
                    .and(w -> w.isNull(ProjectMemberAssignmentDO::getEffectiveFrom)
                            .or().le(ProjectMemberAssignmentDO::getEffectiveFrom, query.getEffectiveAt()))
                    .and(w -> w.isNull(ProjectMemberAssignmentDO::getEffectiveTo)
                            .or().gt(ProjectMemberAssignmentDO::getEffectiveTo, query.getEffectiveAt()));
        } else {
            conditions.and(w -> w.ne(ProjectMemberAssignmentDO::getStatus, "ACTIVE")
                    .or().le(ProjectMemberAssignmentDO::getEffectiveTo, query.getEffectiveAt()));
        }
        return selectPage(query.getPage(), conditions);
    }

    List<ProjectMemberAssignmentDO> selectActiveMemberIdentityForUpdate(
            @Param("query") ProjectMemberIdentityQuery query);

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

    List<ProjectMemberAssignmentDO> selectProjectManagersForUpdate(@Param("query") ProjectManagerMemberQuery query);

    List<ProjectMemberAssignmentDO> selectActiveByUserForUpdate(
            @Param("query") ActiveProjectMemberForUpdateQuery query);

    Long selectResponsibilityNodeCount(@Param("query") ServiceManagerResponsibilityPageQuery query);

    List<ProjectMasterDO> selectResponsibilityNodePage(
            @Param("query") ServiceManagerResponsibilityPageQuery query);

    List<ProjectMemberAssignmentDO> selectCurrentServiceManagerAssignments(
            @Param("query") CurrentServiceManagerAssignmentsQuery query);

    List<ProjectMemberAssignmentDO> selectParticipantFacts(
            @Param("query") ProjectParticipantFactLookupQuery query);

    List<ProjectMemberAssignmentDO> selectParticipantFactsForUpdate(
            @Param("query") ProjectParticipantFactLockQuery query);

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
