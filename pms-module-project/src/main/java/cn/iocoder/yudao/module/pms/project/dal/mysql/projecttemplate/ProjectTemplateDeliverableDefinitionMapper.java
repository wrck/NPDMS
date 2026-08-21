package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDeliverableDefinitionDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目模板交付件定义 Mapper（F-PM03 / V52）
 * <p>
 * 草稿保存为整体替换（物理删除+重插，规避 uk 与逻辑删除并存冲突）。
 */
@Mapper
public interface ProjectTemplateDeliverableDefinitionMapper extends BaseMapperX<ProjectTemplateDeliverableDefinitionDO> {

    default List<ProjectTemplateDeliverableDefinitionDO> selectListByRevisionId(Long templateRevisionId) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateDeliverableDefinitionDO>()
                .eq(ProjectTemplateDeliverableDefinitionDO::getTemplateRevisionId, templateRevisionId));
    }

    @Delete("DELETE FROM proj_project_template_deliverable_definition WHERE template_revision_id = #{templateRevisionId}")
    int physicallyDeleteByRevisionId(Long templateRevisionId);
}
