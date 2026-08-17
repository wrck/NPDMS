package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目模板 Mapper（F-PM03 / V52）
 */
@Mapper
public interface ProjectTemplateMapper extends BaseMapperX<ProjectTemplateDO> {

    default ProjectTemplateDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapperX<ProjectTemplateDO>()
                .eq(ProjectTemplateDO::getCode, code));
    }

    /**
     * 按状态查询并按匹配优先级升序（数值小者先命中）
     */
    default List<ProjectTemplateDO> selectListByStatusOrderByPriority(String status) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateDO>()
                .eq(ProjectTemplateDO::getStatus, status)
                .orderByAsc(ProjectTemplateDO::getMatchPriority));
    }
}
