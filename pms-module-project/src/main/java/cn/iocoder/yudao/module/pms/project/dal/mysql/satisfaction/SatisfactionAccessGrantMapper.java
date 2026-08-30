package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionAccessGrantDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionGrantDigestQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SatisfactionAccessGrantMapper extends BaseMapperX<SatisfactionAccessGrantDO> {
    SatisfactionAccessGrantDO selectByDigest(@Param("query") SatisfactionGrantDigestQuery query);
    SatisfactionAccessGrantDO selectByDigestForUpdate(@Param("query") SatisfactionGrantDigestQuery query);
    Integer selectNextVersion(@Param("tenantId") Long tenantId, @Param("questionnaireId") Long questionnaireId);
}
