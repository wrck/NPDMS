package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目模板版本 Mapper（F-PM03 / V52）
 */
@Mapper
public interface ProjectTemplateRevisionMapper extends BaseMapperX<ProjectTemplateRevisionDO> {

    default ProjectTemplateRevisionDO selectDraftByTemplateId(Long templateId) {
        return selectOne(new LambdaQueryWrapperX<ProjectTemplateRevisionDO>()
                .eq(ProjectTemplateRevisionDO::getTemplateId, templateId)
                .eq(ProjectTemplateRevisionDO::getStatus, "DRAFT"));
    }

    default ProjectTemplateRevisionDO selectByTemplateIdAndRevisionNo(Long templateId, Integer revisionNo) {
        return selectOne(new LambdaQueryWrapperX<ProjectTemplateRevisionDO>()
                .eq(ProjectTemplateRevisionDO::getTemplateId, templateId)
                .eq(ProjectTemplateRevisionDO::getRevisionNo, revisionNo));
    }

    /**
     * 模板全部版本（版本号倒序，首个即最新）
     */
    default List<ProjectTemplateRevisionDO> selectListByTemplateId(Long templateId) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateRevisionDO>()
                .eq(ProjectTemplateRevisionDO::getTemplateId, templateId)
                .orderByDesc(ProjectTemplateRevisionDO::getRevisionNo));
    }

    default List<ProjectTemplateRevisionDO> selectPublishedListByTemplateId(Long templateId) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateRevisionDO>()
                .eq(ProjectTemplateRevisionDO::getTemplateId, templateId)
                .eq(ProjectTemplateRevisionDO::getStatus, "PUBLISHED")
                .orderByDesc(ProjectTemplateRevisionDO::getRevisionNo));
    }
}
