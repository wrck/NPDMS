package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectTreeVersionMapper extends BaseMapperX<ProjectTreeVersionDO> {

    ProjectTreeVersionDO selectLatestActive(Long rootProjectId);

    ProjectTreeVersionDO selectLatest(Long rootProjectId);

    ProjectTreeVersionDO selectLatestActiveForUpdate(Long rootProjectId);

    ProjectTreeVersionDO selectLatestForUpdate(Long rootProjectId);

    default ProjectTreeVersionDO selectActiveVersion(Long rootProjectId, Long treeVersion) {
        return selectOne(new LambdaQueryWrapperX<ProjectTreeVersionDO>()
                .eq(ProjectTreeVersionDO::getRootProjectId, rootProjectId)
                .eq(ProjectTreeVersionDO::getTreeVersion, treeVersion)
                .eq(ProjectTreeVersionDO::getStatus, "ACTIVE"));
    }
}
