package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionAccessGrantDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionGrantDigestQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionGrantConsumeUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SatisfactionAccessGrantMapper extends BaseMapperX<SatisfactionAccessGrantDO> {
    SatisfactionAccessGrantDO selectByDigest(@Param("query") SatisfactionGrantDigestQuery query);
    SatisfactionAccessGrantDO selectByDigestForUpdate(@Param("query") SatisfactionGrantDigestQuery query);
    SatisfactionAccessGrantDO selectByIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);
    Integer selectNextVersion(@Param("tenantId") Long tenantId, @Param("questionnaireId") Long questionnaireId);
    int consumeIfActive(@Param("query") SatisfactionGrantConsumeUpdate query);
}
