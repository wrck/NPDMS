package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectDeliverableInstanceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目交付件实例 Mapper（F-PM01 / V57）
 */
@Mapper
public interface ProjectDeliverableInstanceMapper extends BaseMapperX<ProjectDeliverableInstanceDO> {

    /**
     * 按项目查询交付件实例
     */
    default List<ProjectDeliverableInstanceDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectDeliverableInstanceDO>()
                .eq(ProjectDeliverableInstanceDO::getProjectId, projectId)
                .orderByAsc(ProjectDeliverableInstanceDO::getId));
    }
}
