package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DeviceBootstrapSourceMapper {
    record Page(Long tenantId,List<String> selectedSns,String afterSn) {}
    record Latest(Long tenantId,List<String> deviceSns) {}
    List<String> selectSerialPage(@Param("query") Page query);
    List<DeviceShipmentDO> selectLatestShipments(@Param("query") Latest query);
}
