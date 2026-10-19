package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectContactHistoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DirectContactHistoryMapper {
    record ProjectQuery(Long tenantId, Long projectId) { }
    // 不将实体作为参数：框架自动填充不能修改原NULL操作者或原审计日期。
    record HistoricalInsert(DirectContactHistoryRow values) { }
    List<DirectContactHistoryRow> selectProjectForUpdate(@Param("query") ProjectQuery query);
    int insertHistorical(@Param("entry") HistoricalInsert entry);
}
