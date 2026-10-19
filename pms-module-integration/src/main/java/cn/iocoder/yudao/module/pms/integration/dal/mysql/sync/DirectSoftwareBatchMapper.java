package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
@Mapper public interface DirectSoftwareBatchMapper {
    record RecordIdentity(Long tenantId,Long projectId,Long recordId) {}
    record HistoricalWrite(DirectSoftwareBatchRow values) {}
    DirectSoftwareBatchRow selectRecordForUpdate(@Param("query") RecordIdentity query);
    int insertHistorical(@Param("row") HistoricalWrite row);
}
