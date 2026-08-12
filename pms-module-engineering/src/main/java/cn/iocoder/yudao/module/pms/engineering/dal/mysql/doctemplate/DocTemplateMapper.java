package cn.iocoder.yudao.module.pms.engineering.dal.mysql.doctemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate.DocTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DocTemplateMapper extends BaseMapperX<DocTemplateDO> {

    /**
     * 按编号查询，用于全局唯一性校验
     */
    default DocTemplateDO selectByCode(String code) {
        return selectOne(DocTemplateDO::getCode, code);
    }

    /**
     * 按文档类别查询模板列表
     */
    default List<DocTemplateDO> selectListByDocCategory(String docCategory) {
        return selectList(new LambdaQueryWrapperX<DocTemplateDO>()
                .eqIfPresent(DocTemplateDO::getDocCategory, docCategory)
                .orderByDesc(DocTemplateDO::getId));
    }

    /**
     * 按文档类别查询已发布模板列表
     */
    default List<DocTemplateDO> selectPublishedList(String docCategory) {
        return selectList(new LambdaQueryWrapperX<DocTemplateDO>()
                .eqIfPresent(DocTemplateDO::getDocCategory, docCategory)
                .eq(DocTemplateDO::getStatus, 1)
                .orderByDesc(DocTemplateDO::getId));
    }

    /**
     * 分页查询
     */
    default PageResult<DocTemplateDO> selectPage(DocTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DocTemplateDO>()
                .likeIfPresent(DocTemplateDO::getCode, reqVO.getCode())
                .likeIfPresent(DocTemplateDO::getName, reqVO.getName())
                .eqIfPresent(DocTemplateDO::getDocCategory, reqVO.getDocCategory())
                .eqIfPresent(DocTemplateDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(DocTemplateDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DocTemplateDO::getId));
    }

}
