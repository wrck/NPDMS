package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface DirectRealProductMapper {
    record RecordIdentityQuery(Long tenantId,Long projectId,Long id) {}
    @lombok.Builder
    record BusinessIdentityQuery(Long tenantId,Long projectId,String contractNo,String projectCode,String executionNo,String productCategoryName,String productName,String productSubCode,String productSubModel,String productSubName,java.math.BigDecimal quantity,String memo) {}
    record MigrationProjectQuery(Long tenantId,Long projectId) {}
    DirectRealProductRow selectRecordForUpdate(@Param("query") RecordIdentityQuery query);
    List<DirectRealProductRow> selectBusinessRecordsForUpdate(@Param("query") BusinessIdentityQuery query);
    Long countAllProjectRecords(@Param("query") MigrationProjectQuery query);
    int insert(@Param("row") DirectRealProductRow row);
}
