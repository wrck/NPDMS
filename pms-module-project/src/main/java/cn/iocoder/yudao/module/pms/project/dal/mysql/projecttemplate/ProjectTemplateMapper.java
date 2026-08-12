package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目模板 Mapper
 */
@Mapper
public interface ProjectTemplateMapper extends BaseMapperX<ProjectTemplateDO> {

    default ProjectTemplateDO selectByCode(String code) {
        return selectOne(ProjectTemplateDO::getCode, code);
    }

    default List<ProjectTemplateDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateDO>()
                .eq(ProjectTemplateDO::getStatus, 0)
                .orderByAsc(ProjectTemplateDO::getSort)
                .orderByDesc(ProjectTemplateDO::getId));
    }

    default List<ProjectTemplateDO> selectEnabledListByType(String projectType) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateDO>()
                .eq(ProjectTemplateDO::getStatus, 0)
                .eqIfPresent(ProjectTemplateDO::getProjectType, projectType)
                .orderByAsc(ProjectTemplateDO::getSort)
                .orderByDesc(ProjectTemplateDO::getId));
    }

    default PageResult<ProjectTemplateDO> selectPage(ProjectTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectTemplateDO>()
                .likeIfPresent(ProjectTemplateDO::getCode, reqVO.getCode())
                .likeIfPresent(ProjectTemplateDO::getName, reqVO.getName())
                .eqIfPresent(ProjectTemplateDO::getProjectType, reqVO.getProjectType())
                .eqIfPresent(ProjectTemplateDO::getStatus, reqVO.getStatus())
                .orderByAsc(ProjectTemplateDO::getSort)
                .orderByDesc(ProjectTemplateDO::getId));
    }
}
