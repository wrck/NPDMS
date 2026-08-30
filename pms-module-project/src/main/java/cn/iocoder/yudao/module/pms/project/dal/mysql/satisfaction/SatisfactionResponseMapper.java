package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResponseDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResponseIdentityQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SatisfactionResponseMapper extends BaseMapperX<SatisfactionResponseDO> {
    SatisfactionResponseDO selectByIdentityForUpdate(@Param("query") SatisfactionResponseIdentityQuery query);
    Integer selectNextResponseNo(@Param("tenantId") Long tenantId,
                                 @Param("questionnaireId") Long questionnaireId);
}
