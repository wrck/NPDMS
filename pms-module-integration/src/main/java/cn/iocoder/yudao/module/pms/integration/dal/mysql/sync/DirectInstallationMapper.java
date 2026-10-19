package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectInstallationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Inserts the approved historical mapping without framework audit autofill. */
@Mapper
public interface DirectInstallationMapper {
    record CurrentRecord(Long tenantId,Long equipmentId) {}
    record CodeScope(Long tenantId,Long projectId) {}
    DirectInstallationRow selectCurrentRecordForUpdate(@Param("query") CurrentRecord query);
    java.util.List<String> selectRecordCodesIncludeDeleted(@Param("query") CodeScope query);
    int insertHistorical(@Param("record") DirectInstallationRow record);
    DirectInstallationRow selectBoundRecordForUpdate(@Param("query") BoundRecord query);
    record BoundRecord(Long tenantId,Long projectId,Long installationId) {}
}
