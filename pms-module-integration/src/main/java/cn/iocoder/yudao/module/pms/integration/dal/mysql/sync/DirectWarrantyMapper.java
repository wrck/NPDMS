package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
@Mapper
public interface DirectWarrantyMapper {
    record Identity(Long tenantId,Long id) {}
    record Write(DirectWarrantyRow row,Integer expectedVersion) {}
    DirectWarrantyRow selectForUpdate(@Param("query") Identity query);
    int insertRecord(@Param("row") DirectWarrantyRow row);
    int updateRecord(@Param("command") Write command);
}
