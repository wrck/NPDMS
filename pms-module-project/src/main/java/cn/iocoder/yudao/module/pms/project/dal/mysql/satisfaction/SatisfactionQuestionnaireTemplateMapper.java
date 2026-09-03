package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTemplateCandidateQuery;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SatisfactionQuestionnaireTemplateMapper extends BaseMapperX<SatisfactionQuestionnaireTemplateDO> {
    default List<SatisfactionQuestionnaireTemplateDO> selectListByTenant(Long tenantId) {
        return selectList(new LambdaQueryWrapperX<SatisfactionQuestionnaireTemplateDO>()
                .eq(SatisfactionQuestionnaireTemplateDO::getTenantId, tenantId)
                .orderByAsc(SatisfactionQuestionnaireTemplateDO::getTemplateCode)
                .orderByAsc(SatisfactionQuestionnaireTemplateDO::getId));
    }

    SatisfactionQuestionnaireTemplateDO selectByIdForUpdate(@Param("tenantId") Long tenantId,
                                                              @Param("id") Long id);

    List<SatisfactionTemplateCandidateRecord> selectPublishedCandidates(
            @Param("query") SatisfactionTemplateCandidateQuery query);
}
