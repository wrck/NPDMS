package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateStageDefinitionDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目模板阶段定义 Mapper（F-PM03 / V52）
 * <p>
 * 定义行仅存在于草稿编辑与已发布快照：草稿保存为整体替换（物理删除+重插，规避
 * uk(template_revision_id, stage_code) 与逻辑删除并存冲突）；已发布版本行只读不删。
 */
@Mapper
public interface ProjectTemplateStageDefinitionMapper extends BaseMapperX<ProjectTemplateStageDefinitionDO> {

    default List<ProjectTemplateStageDefinitionDO> selectListByRevisionId(Long templateRevisionId) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateStageDefinitionDO>()
                .eq(ProjectTemplateStageDefinitionDO::getTemplateRevisionId, templateRevisionId)
                .orderByAsc(ProjectTemplateStageDefinitionDO::getSortOrder));
    }

    @Delete("DELETE FROM proj_project_template_stage_definition WHERE template_revision_id = #{templateRevisionId}")
    int physicallyDeleteByRevisionId(Long templateRevisionId);
}
