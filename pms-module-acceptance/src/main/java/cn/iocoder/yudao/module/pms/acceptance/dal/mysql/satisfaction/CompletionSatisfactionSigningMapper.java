package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.CompletionSatisfactionSigningDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.CompletionSatisfactionSigningQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CompletionSatisfactionSigningMapper extends BaseMapperX<CompletionSatisfactionSigningDO> {
    default CompletionSatisfactionSigningDO selectByQuestionnaire(
            cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.CompletionSigningQuestionnaireQuery query){
        return selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<CompletionSatisfactionSigningDO>()
                .eq(CompletionSatisfactionSigningDO::getTenantId,query.tenantId())
                .eq(CompletionSatisfactionSigningDO::getBundleId,query.bundleId())
                .eq(CompletionSatisfactionSigningDO::getQuestionnaireId,query.questionnaireId()));
    }
    CompletionSatisfactionSigningDO selectFirstSigning(@Param("query") CompletionSatisfactionSigningQuery query);
}
