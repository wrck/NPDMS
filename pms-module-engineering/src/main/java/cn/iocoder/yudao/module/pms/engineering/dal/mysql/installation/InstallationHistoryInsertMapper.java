package cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Inserts the approved historical mapping without framework audit autofill. */
@Mapper
public interface InstallationHistoryInsertMapper {
    int insertHistorical(@Param("record") InstallationDO record);
    InstallationDO selectBoundRecordForUpdate(@Param("query") BoundRecord query);
    record BoundRecord(Long tenantId,Long projectId,Long installationId) {}
}
