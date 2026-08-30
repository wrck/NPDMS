package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTemplateCandidateQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SatisfactionQuestionnaireTemplateMapper extends BaseMapperX<SatisfactionQuestionnaireTemplateDO> {
    List<SatisfactionTemplateCandidateRecord> selectPublishedCandidates(
            @Param("query") SatisfactionTemplateCandidateQuery query);
}
