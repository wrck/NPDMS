package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateTransitionDefinitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.query.TemplateRevisionRowsQuery;
import org.apache.ibatis.annotations.*;
import java.util.List;
/** PM-03: explicit template edges, draft-only replacement. */
@Mapper
public interface ProjectTemplateTransitionDefinitionMapper extends BaseMapperX<ProjectTemplateTransitionDefinitionDO> {
    default List<ProjectTemplateTransitionDefinitionDO> selectRows(TemplateRevisionRowsQuery query) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateTransitionDefinitionDO>()
                .eq(ProjectTemplateTransitionDefinitionDO::getTenantId, query.tenantId())
                .eq(ProjectTemplateTransitionDefinitionDO::getTemplateRevisionId, query.templateRevisionId())
                .orderByAsc(ProjectTemplateTransitionDefinitionDO::getTransitionCode));
    }
    int physicallyDeleteByRevisionId(@Param("query") TemplateRevisionRowsQuery query);
}
