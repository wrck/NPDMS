package cn.iocoder.yudao.module.pms.project.dal.mysql.phase;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhasePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseRetiredDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * PMS 项目阶段 Mapper（FR-PROJ-017 / FR-PROJ-016 / FR-PROJ-019）。
 * <p>
 * 唯一索引 {@code uk_pms_project_phase (project_id, code)} 保证项目内阶段编码唯一。
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Mapper
@Deprecated
public interface ProjectPhaseMapper extends BaseMapperX<ProjectPhaseRetiredDO> {

    default PageResult<ProjectPhaseRetiredDO> selectPageRetired(ProjectPhasePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectPhaseRetiredDO>()
                .eqIfPresent(ProjectPhaseRetiredDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ProjectPhaseRetiredDO::getName, reqVO.getName())
                .eqIfPresent(ProjectPhaseRetiredDO::getCode, reqVO.getCode())
                .eqIfPresent(ProjectPhaseRetiredDO::getStatus, reqVO.getStatus())
                .orderByAsc(ProjectPhaseRetiredDO::getSort)
                .orderByAsc(ProjectPhaseRetiredDO::getId));
    }

    /**
     * 查询项目下全部阶段（按 sort、id 升序）。
     */
    default List<ProjectPhaseRetiredDO> selectListByProjectIdRetired(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectPhaseRetiredDO>()
                .eq(ProjectPhaseRetiredDO::getProjectId, projectId)
                .orderByAsc(ProjectPhaseRetiredDO::getSort)
                .orderByAsc(ProjectPhaseRetiredDO::getId));
    }

    /**
     * 按模板编号统计引用次数（用于模板删除前校验）。
     */
    default Long selectCountByTemplateIdRetired(Long templateId) {
        return selectCount(new LambdaQueryWrapperX<ProjectPhaseRetiredDO>()
                .eq(ProjectPhaseRetiredDO::getTemplateId, templateId));
    }

    /**
     * 查询项目内指定编码的阶段。
     */
    default ProjectPhaseRetiredDO selectByProjectAndCodeRetired(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<ProjectPhaseRetiredDO>()
                .eq(ProjectPhaseRetiredDO::getProjectId, projectId)
                .eq(ProjectPhaseRetiredDO::getCode, code));
    }

    /**
     * 查询超期阶段：plan_end_time < now 且状态不在已完成(2)/已跳过(3)。
     */
    default List<ProjectPhaseRetiredDO> selectOverdueListRetired(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<ProjectPhaseRetiredDO>()
                .lt(ProjectPhaseRetiredDO::getPlanEndTime, now)
                .notIn(ProjectPhaseRetiredDO::getStatus, Arrays.asList(2, 3))
                .orderByAsc(ProjectPhaseRetiredDO::getPlanEndTime));
    }

    /**
     * 查询临近截止阶段：plan_end_time 在 [from, to] 区间且状态不在已完成(2)/已跳过(3)。
     */
    default List<ProjectPhaseRetiredDO> selectUpcomingListRetired(LocalDateTime from, LocalDateTime to) {
        return selectList(new LambdaQueryWrapperX<ProjectPhaseRetiredDO>()
                .ge(ProjectPhaseRetiredDO::getPlanEndTime, from)
                .le(ProjectPhaseRetiredDO::getPlanEndTime, to)
                .notIn(ProjectPhaseRetiredDO::getStatus, Arrays.asList(2, 3))
                .orderByAsc(ProjectPhaseRetiredDO::getPlanEndTime));
    }

    /**
     * 统计项目下指定状态阶段数。
     */
    default Long selectCountByProjectAndStatusRetired(Long projectId, Integer status) {
        return selectCount(new LambdaQueryWrapperX<ProjectPhaseRetiredDO>()
                .eq(ProjectPhaseRetiredDO::getProjectId, projectId)
                .eq(ProjectPhaseRetiredDO::getStatus, status));
    }
}
