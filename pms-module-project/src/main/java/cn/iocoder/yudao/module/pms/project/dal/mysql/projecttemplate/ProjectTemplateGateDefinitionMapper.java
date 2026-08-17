package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateGateDefinitionDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目模板门禁定义 Mapper（F-PM03 / V52）
 * <p>
 * 草稿保存为整体替换（物理删除+重插，规避 uk 与逻辑删除并存冲突）。
 */
@Mapper
public interface ProjectTemplateGateDefinitionMapper extends BaseMapperX<ProjectTemplateGateDefinitionDO> {

    default List<ProjectTemplateGateDefinitionDO> selectListByRevisionId(Long templateRevisionId) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateGateDefinitionDO>()
                .eq(ProjectTemplateGateDefinitionDO::getTemplateRevisionId, templateRevisionId));
    }

    @Delete("DELETE FROM proj_project_template_gate_definition WHERE template_revision_id = #{templateRevisionId}")
    int physicallyDeleteByRevisionId(Long templateRevisionId);
}
