package cn.iocoder.yudao.module.pms.project.dal.mysql.phasetemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo.ProjectPhaseTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phasetemplate.ProjectPhaseTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目阶段模板 Mapper
 */
@Mapper
public interface ProjectPhaseTemplateMapper extends BaseMapperX<ProjectPhaseTemplateDO> {

    default PageResult<ProjectPhaseTemplateDO> selectPage(ProjectPhaseTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectPhaseTemplateDO>()
                .likeIfPresent(ProjectPhaseTemplateDO::getCode, reqVO.getCode())
                .likeIfPresent(ProjectPhaseTemplateDO::getName, reqVO.getName())
                .eqIfPresent(ProjectPhaseTemplateDO::getProjectType, reqVO.getProjectType())
                .eqIfPresent(ProjectPhaseTemplateDO::getStatus, reqVO.getStatus())
                .orderByAsc(ProjectPhaseTemplateDO::getSort)
                .orderByDesc(ProjectPhaseTemplateDO::getId));
    }

    default ProjectPhaseTemplateDO selectByCode(String code) {
        return selectOne(ProjectPhaseTemplateDO::getCode, code);
    }

    default List<ProjectPhaseTemplateDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<ProjectPhaseTemplateDO>()
                .eq(ProjectPhaseTemplateDO::getStatus, 0)
                .orderByAsc(ProjectPhaseTemplateDO::getSort)
                .orderByDesc(ProjectPhaseTemplateDO::getId));
    }

    default List<ProjectPhaseTemplateDO> selectEnabledListByType(String projectType) {
        return selectList(new LambdaQueryWrapperX<ProjectPhaseTemplateDO>()
                .eq(ProjectPhaseTemplateDO::getStatus, 0)
                .eq(ProjectPhaseTemplateDO::getProjectType, projectType)
                .orderByAsc(ProjectPhaseTemplateDO::getSort)
                .orderByDesc(ProjectPhaseTemplateDO::getId));
    }

}
