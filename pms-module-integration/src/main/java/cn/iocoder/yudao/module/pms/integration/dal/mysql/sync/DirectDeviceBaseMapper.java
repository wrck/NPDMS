package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper public interface DirectDeviceBaseMapper {
    record DeviceIdentityQuery(Long tenantId,String deviceSn) {}
    record Latest(Long tenantId,List<String> deviceSns) {}
    record Products(Long tenantId,Set<String> productCodes) {}
    DirectDeviceBaseRow selectIdentityForUpdate(@Param("query") DeviceIdentityQuery query);
    List<DirectBootstrapShipmentRow> selectLatestShipments(@Param("query") Latest query);
    List<DirectProductRow> selectShipmentProducts(@Param("query") Products query);
    int insert(@Param("row") DirectDeviceBaseRow row);
}
