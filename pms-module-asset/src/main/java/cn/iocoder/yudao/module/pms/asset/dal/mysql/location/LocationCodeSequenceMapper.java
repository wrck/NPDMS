package cn.iocoder.yudao.module.pms.asset.dal.mysql.location;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LocationCodeSequenceMapper {
    void initialize(@Param("tenantId") Long tenantId, @Param("namespace") String namespace);
    Long lockValue(@Param("tenantId") Long tenantId, @Param("namespace") String namespace);
    void advance(@Param("tenantId") Long tenantId, @Param("namespace") String namespace);
}
