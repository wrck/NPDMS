package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectExitGateForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateStatusUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目门禁实例 Mapper（F-PM01 / V57）
 */
@Mapper
public interface ProjectGateInstanceMapper extends BaseMapperX<ProjectGateInstanceDO> {

    /**
     * 按项目查询门禁实例（阶段顺序→门禁类型→id）
     */
    default List<ProjectGateInstanceDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectGateInstanceDO>()
                .eq(ProjectGateInstanceDO::getProjectId, projectId)
                .orderByAsc(ProjectGateInstanceDO::getStageCode)
                .orderByAsc(ProjectGateInstanceDO::getGateType)
                .orderByAsc(ProjectGateInstanceDO::getId));
    }

    ProjectGateInstanceDO selectByCodeForUpdate(@Param("query") ProjectGateForUpdateQuery query);

    List<ProjectGateInstanceDO> selectExitGates(@Param("query") ProjectExitGateForUpdateQuery query);

    List<ProjectGateInstanceDO> selectExitGatesForUpdate(@Param("query") ProjectExitGateForUpdateQuery query);

    int updateStatusIfMatch(@Param("query") ProjectGateStatusUpdate query);
}
