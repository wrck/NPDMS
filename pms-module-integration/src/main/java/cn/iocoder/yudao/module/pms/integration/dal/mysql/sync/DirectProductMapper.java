package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DirectProductMapper {
    record CodeQuery(Long tenantId,String productCode) {}
    DirectProductRow selectByProductCodeForUpdate(@Param("query") CodeQuery query);
    int insertProduct(@Param("row") DirectProductRow row);
}
