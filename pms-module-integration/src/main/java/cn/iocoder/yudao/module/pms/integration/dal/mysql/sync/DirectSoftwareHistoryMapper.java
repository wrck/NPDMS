package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
@Mapper public interface DirectSoftwareHistoryMapper {
    record RecordLock(Long tenantId,Long recordId) {}
    record HistoricalWrite(DirectSoftwareHistoryRow values) {}
    DirectSoftwareHistoryRow selectForUpdate(@Param("query") RecordLock query);
    int insertHistorical(@Param("row") HistoricalWrite row);
}
