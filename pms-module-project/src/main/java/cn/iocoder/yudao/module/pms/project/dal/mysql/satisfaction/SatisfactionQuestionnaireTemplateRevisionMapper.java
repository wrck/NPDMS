package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTemplateRevisionQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTemplateApplicabilityQuery;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SatisfactionQuestionnaireTemplateRevisionMapper
        extends BaseMapperX<SatisfactionQuestionnaireTemplateRevisionDO> {
    default java.util.List<SatisfactionQuestionnaireTemplateRevisionDO> selectListByTemplate(
            Long tenantId, Long templateId) {
        return selectList(new LambdaQueryWrapperX<SatisfactionQuestionnaireTemplateRevisionDO>()
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getTenantId, tenantId)
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getTemplateId, templateId)
                .orderByAsc(SatisfactionQuestionnaireTemplateRevisionDO::getRevisionNo)
                .orderByAsc(SatisfactionQuestionnaireTemplateRevisionDO::getId));
    }

    default java.util.List<SatisfactionQuestionnaireTemplateRevisionDO> selectPublishedByApplicability(
            SatisfactionTemplateApplicabilityQuery query) {
        return selectList(new LambdaQueryWrapperX<SatisfactionQuestionnaireTemplateRevisionDO>()
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getTenantId, query.tenantId())
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getProjectType, query.projectType())
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getSigningMode, query.signingMode())
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getImplementationMode, query.implementationMode())
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getBusinessPurposeCode, query.businessPurposeCode())
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getApplicableTimingCode, query.applicableTimingCode())
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getPriority, query.priority())
                .eq(SatisfactionQuestionnaireTemplateRevisionDO::getRevisionStatus, "PUBLISHED")
                .orderByAsc(SatisfactionQuestionnaireTemplateRevisionDO::getId));
    }

    SatisfactionQuestionnaireTemplateRevisionDO selectByIdForUpdate(@org.apache.ibatis.annotations.Param("tenantId") Long tenantId,
                                                                      @org.apache.ibatis.annotations.Param("id") Long id);

    SatisfactionQuestionnaireTemplateRevisionDO selectFrozenRevision(
            @Param("query") SatisfactionTemplateRevisionQuery query);
}
