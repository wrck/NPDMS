package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DirectCustomerMapper {
    record SourceQuery(Long tenantId,String sourceKey) {}
    record CustomerQuery(Long tenantId,Long customerId) {}
    record CodeQuery(Long tenantId,String code) {}
    record CustomerWrite(DirectCustomerRow row,Long expectedVersion) {}
    record MappingWrite(Long tenantId,Long mappingId,String expectedVersion,String newVersion) {}
    DirectCustomerMappingRow selectCurrentMapping(@Param("query") SourceQuery query);
    DirectCustomerRow selectCustomerForUpdate(@Param("query") CustomerQuery query);
    DirectCustomerRow selectCodeForUpdate(@Param("query") CodeQuery query);
    int insertCustomer(@Param("row") DirectCustomerRow row);
    int insertMapping(@Param("row") DirectCustomerMappingRow row);
    int updateCustomer(@Param("command") CustomerWrite command);
    int updateMapping(@Param("command") MappingWrite command);
}
