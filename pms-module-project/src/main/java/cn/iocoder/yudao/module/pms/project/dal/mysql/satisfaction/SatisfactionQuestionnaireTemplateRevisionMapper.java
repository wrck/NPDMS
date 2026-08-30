package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTemplateRevisionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SatisfactionQuestionnaireTemplateRevisionMapper
        extends BaseMapperX<SatisfactionQuestionnaireTemplateRevisionDO> {
    SatisfactionQuestionnaireTemplateRevisionDO selectFrozenRevision(
            @Param("query") SatisfactionTemplateRevisionQuery query);
}
