package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
@Mapper public interface DirectFactoryHistoryMapper {
    record GlobalRecord(Long tenantId,Long id) {}
    DirectFactoryHistoryRow selectGlobalRecordForUpdate(@Param("query") GlobalRecord query);
    int insert(@Param("row") DirectFactoryHistoryRow row);
}
