package cn.iocoder.yudao.module.pms.engineering.dal.mysql.formtemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo.FormTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.formtemplate.FormTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FormTemplateMapper extends BaseMapperX<FormTemplateDO> {

    default PageResult<FormTemplateDO> selectPage(FormTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FormTemplateDO>()
                .likeIfPresent(FormTemplateDO::getCode, reqVO.getCode())
                .likeIfPresent(FormTemplateDO::getName, reqVO.getName())
                .eqIfPresent(FormTemplateDO::getProductType, reqVO.getProductType())
                .eqIfPresent(FormTemplateDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(FormTemplateDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(FormTemplateDO::getId));
    }

    /**
     * 按编号查询，用于全局唯一性校验
     */
    default FormTemplateDO selectByCode(String code) {
        return selectOne(FormTemplateDO::getCode, code);
    }

    /**
     * 按产品类型查询已发布模板列表（供实例创建时下拉选择）
     */
    default List<FormTemplateDO> selectListByProductType(String productType) {
        return selectList(new LambdaQueryWrapperX<FormTemplateDO>()
                .eqIfPresent(FormTemplateDO::getProductType, productType)
                .eq(FormTemplateDO::getStatus, 1)
                .orderByDesc(FormTemplateDO::getId));
    }

    /**
     * 查询所有已发布模板（供实例创建时下拉选择）
     */
    default List<FormTemplateDO> selectPublishedList() {
        return selectList(new LambdaQueryWrapperX<FormTemplateDO>()
                .eq(FormTemplateDO::getStatus, 1)
                .orderByDesc(FormTemplateDO::getId));
    }

}
