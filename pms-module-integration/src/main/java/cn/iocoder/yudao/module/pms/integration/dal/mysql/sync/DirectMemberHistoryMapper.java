package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectMemberHistoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/** PROJ拥有的历史成员写入；显式审计字段不经过普通自动填充。 */
@Mapper
public interface DirectMemberHistoryMapper {
    /** 非实体参数，避免MyBatis在显式历史SQL前自动填充原NULL审计字段。 */
    record HistoricalInsert(DirectMemberHistoryRow values) { }
    record ProjectQuery(Long tenantId, Long projectId) { }
    List<DirectMemberHistoryRow> selectProjectForUpdate(@Param("query") ProjectQuery query);
    int insertHistorical(@Param("entry") HistoricalInsert entry);
}
