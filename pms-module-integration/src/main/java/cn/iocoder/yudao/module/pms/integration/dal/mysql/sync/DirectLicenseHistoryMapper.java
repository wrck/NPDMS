package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
@Mapper public interface DirectLicenseHistoryMapper {
    record RecordQuery(Long tenantId,Long id) {}
    record DeviceIdentity(Long tenantId,String sn) {}
    record ContractIdentity(Long tenantId,String contractNo) {}
    DirectLicenseHistoryRow selectRecordForUpdate(@Param("query") RecordQuery query);
    DirectDeviceBaseRow selectDevice(@Param("query") DeviceIdentity query);
    java.util.List<Long> selectContractIds(@Param("query") ContractIdentity query);
    int insert(@Param("row") DirectLicenseHistoryRow row);
}
