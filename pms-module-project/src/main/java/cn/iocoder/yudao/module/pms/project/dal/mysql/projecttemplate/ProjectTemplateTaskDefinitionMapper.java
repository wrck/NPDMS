package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateTaskDefinitionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProjectTemplateTaskDefinitionMapper extends BaseMapperX<ProjectTemplateTaskDefinitionDO> {

    default List<ProjectTemplateTaskDefinitionDO> selectByRevisionId(Long tenantId, Long revisionId) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateTaskDefinitionDO>()
                .eq(ProjectTemplateTaskDefinitionDO::getTenantId, tenantId)
                .eq(ProjectTemplateTaskDefinitionDO::getTemplateRevisionId, revisionId)
                .orderByAsc(ProjectTemplateTaskDefinitionDO::getSortOrder)
                .orderByAsc(ProjectTemplateTaskDefinitionDO::getId));
    }
}
