package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface DirectExecutionOrderMapper {
    record IncomingQuery(Long tenantId,String sourceSystem,List<String> executionNumbers) {}
    List<DirectExecutionOrderRow> selectIncoming(@Param("query") IncomingQuery query);
    int insertRow(@Param("row") DirectExecutionOrderRow row);
    int updateRow(@Param("row") DirectExecutionOrderRow row);
}
