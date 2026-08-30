package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResultDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResultIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResultArchiveProjectionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SatisfactionResultMapper extends BaseMapperX<SatisfactionResultDO> {
    SatisfactionResultFactRecord selectFact(@Param("query") SatisfactionResultIdentityQuery query);
    SatisfactionResultFactRecord selectFactForUpdate(@Param("query") SatisfactionResultIdentityQuery query);
    SatisfactionResultDO selectByIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);
    int updateArchiveProjection(@Param("query") SatisfactionResultArchiveProjectionUpdate query);
}
