package cn.iocoder.yudao.module.pms.project.dal.mysql.phase;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhasePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * PMS 项目阶段 Mapper（FR-PROJ-017 / FR-PROJ-016 / FR-PROJ-019）。
 * <p>
 * 唯一索引 {@code uk_pms_project_phase (project_id, code)} 保证项目内阶段编码唯一。
 */
@Mapper
public interface ProjectPhaseMapper extends BaseMapperX<ProjectPhaseDO> {

    default PageResult<ProjectPhaseDO> selectPage(ProjectPhasePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectPhaseDO>()
                .eqIfPresent(ProjectPhaseDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ProjectPhaseDO::getName, reqVO.getName())
                .eqIfPresent(ProjectPhaseDO::getCode, reqVO.getCode())
                .eqIfPresent(ProjectPhaseDO::getStatus, reqVO.getStatus())
                .orderByAsc(ProjectPhaseDO::getSort)
                .orderByAsc(ProjectPhaseDO::getId));
    }

    /**
     * 查询项目下全部阶段（按 sort、id 升序）。
     */
    default List<ProjectPhaseDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectPhaseDO>()
                .eq(ProjectPhaseDO::getProjectId, projectId)
                .orderByAsc(ProjectPhaseDO::getSort)
                .orderByAsc(ProjectPhaseDO::getId));
    }

    /**
     * 按模板编号统计引用次数（用于模板删除前校验）。
     */
    default Long selectCountByTemplateId(Long templateId) {
        return selectCount(new LambdaQueryWrapperX<ProjectPhaseDO>()
                .eq(ProjectPhaseDO::getTemplateId, templateId));
    }

    /**
     * 查询项目内指定编码的阶段。
     */
    default ProjectPhaseDO selectByProjectAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<ProjectPhaseDO>()
                .eq(ProjectPhaseDO::getProjectId, projectId)
                .eq(ProjectPhaseDO::getCode, code));
    }

    /**
     * 查询超期阶段：plan_end_time < now 且状态不在已完成(2)/已跳过(3)。
     */
    default List<ProjectPhaseDO> selectOverdueList(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<ProjectPhaseDO>()
                .lt(ProjectPhaseDO::getPlanEndTime, now)
                .notIn(ProjectPhaseDO::getStatus, Arrays.asList(2, 3))
                .orderByAsc(ProjectPhaseDO::getPlanEndTime));
    }

    /**
     * 查询临近截止阶段：plan_end_time 在 [from, to] 区间且状态不在已完成(2)/已跳过(3)。
     */
    default List<ProjectPhaseDO> selectUpcomingList(LocalDateTime from, LocalDateTime to) {
        return selectList(new LambdaQueryWrapperX<ProjectPhaseDO>()
                .ge(ProjectPhaseDO::getPlanEndTime, from)
                .le(ProjectPhaseDO::getPlanEndTime, to)
                .notIn(ProjectPhaseDO::getStatus, Arrays.asList(2, 3))
                .orderByAsc(ProjectPhaseDO::getPlanEndTime));
    }

    /**
     * 统计项目下指定状态阶段数。
     */
    default Long selectCountByProjectAndStatus(Long projectId, Integer status) {
        return selectCount(new LambdaQueryWrapperX<ProjectPhaseDO>()
                .eq(ProjectPhaseDO::getProjectId, projectId)
                .eq(ProjectPhaseDO::getStatus, status));
    }
}
