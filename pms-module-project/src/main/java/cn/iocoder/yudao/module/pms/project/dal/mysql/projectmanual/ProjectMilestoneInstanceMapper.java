package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目里程碑实例 Mapper（F-PM01 / V57）
 */
@Mapper
public interface ProjectMilestoneInstanceMapper extends BaseMapperX<ProjectMilestoneInstanceDO> {

    /**
     * 按项目查询里程碑实例
     */
    default List<ProjectMilestoneInstanceDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectMilestoneInstanceDO>()
                .eq(ProjectMilestoneInstanceDO::getProjectId, projectId)
                .orderByAsc(ProjectMilestoneInstanceDO::getId));
    }
}
