package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectSiteDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProjectSiteMapper extends BaseMapperX<ProjectSiteDO> {
    default List<ProjectSiteDO> selectActiveByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectSiteDO>()
                .eq(ProjectSiteDO::getProjectId, projectId)
                .isNull(ProjectSiteDO::getEffectiveTo)
                .orderByDesc(ProjectSiteDO::getPrimarySite)
                .orderByAsc(ProjectSiteDO::getId));
    }
}
