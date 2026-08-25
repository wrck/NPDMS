package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目任务实例 Mapper（F-PM01 / V57）
 */
@Mapper
public interface ProjectTaskInstanceMapper extends BaseMapperX<ProjectTaskInstanceDO> {

    /**
     * 按项目查询任务实例（排序值升序）
     */
    default List<ProjectTaskInstanceDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectTaskInstanceDO>()
                .eq(ProjectTaskInstanceDO::getProjectId, projectId)
                .orderByAsc(ProjectTaskInstanceDO::getSortOrder)
                .orderByAsc(ProjectTaskInstanceDO::getId));
    }

    default ProjectTaskInstanceDO selectByProjectIdAndTaskCode(Long projectId, String taskCode) {
        return selectOne(new LambdaQueryWrapperX<ProjectTaskInstanceDO>()
                .eq(ProjectTaskInstanceDO::getProjectId, projectId)
                .eq(ProjectTaskInstanceDO::getTaskCode, taskCode));
    }
}
