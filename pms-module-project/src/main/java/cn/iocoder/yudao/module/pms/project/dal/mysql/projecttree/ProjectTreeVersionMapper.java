package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface ProjectTreeVersionMapper extends BaseMapperX<ProjectTreeVersionDO> {
    default ProjectTreeVersionDO selectLatestActive(Long rootProjectId) {
        return selectOne(new LambdaQueryWrapperX<ProjectTreeVersionDO>()
                .eq(ProjectTreeVersionDO::getRootProjectId, rootProjectId)
                .eq(ProjectTreeVersionDO::getStatus, "ACTIVE")
                .orderByDesc(ProjectTreeVersionDO::getTreeVersion)
                .last("LIMIT 1"));
    }
}
