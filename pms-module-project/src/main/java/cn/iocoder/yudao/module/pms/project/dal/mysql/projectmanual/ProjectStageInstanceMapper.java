package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStagePairForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageTransitionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目阶段实例 Mapper（F-PM01 / V57）
 */
@Mapper
public interface ProjectStageInstanceMapper extends BaseMapperX<ProjectStageInstanceDO> {

    /**
     * 按项目查询阶段实例（阶段顺序升序）
     */
    default List<ProjectStageInstanceDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectStageInstanceDO>()
                .eq(ProjectStageInstanceDO::getProjectId, projectId)
                .orderByAsc(ProjectStageInstanceDO::getSortOrder)
                .orderByAsc(ProjectStageInstanceDO::getId));
    }

    default ProjectStageInstanceDO selectByProjectIdAndStageCode(Long projectId, String stageCode) {
        return selectOne(new LambdaQueryWrapperX<ProjectStageInstanceDO>()
                .eq(ProjectStageInstanceDO::getProjectId, projectId)
                .eq(ProjectStageInstanceDO::getStageCode, stageCode));
    }

    List<ProjectStageInstanceDO> selectStagePair(@Param("query") ProjectStagePairForUpdateQuery query);

    List<ProjectStageInstanceDO> selectStagePairForUpdate(@Param("query") ProjectStagePairForUpdateQuery query);

    List<ProjectStageInstanceDO> selectListForTransition(
            @Param("query") ProjectStageTransitionQuery query);

    int updateStatusIfMatch(@Param("query") ProjectStageStatusUpdate query);
}
