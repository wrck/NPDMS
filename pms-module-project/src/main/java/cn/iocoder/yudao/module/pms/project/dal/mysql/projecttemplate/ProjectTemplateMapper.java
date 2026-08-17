package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
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
     * 分页查询：状态精确、编码/名称模糊，优先级升序
     */
    default PageResult<ProjectTemplateDO> selectPage(ProjectTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectTemplateDO>()
                .eqIfPresent(ProjectTemplateDO::getStatus, reqVO.getStatus())
                .likeIfPresent(ProjectTemplateDO::getCode, reqVO.getCode())
                .likeIfPresent(ProjectTemplateDO::getName, reqVO.getName())
                .orderByAsc(ProjectTemplateDO::getMatchPriority)
                .orderByDesc(ProjectTemplateDO::getId));
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
