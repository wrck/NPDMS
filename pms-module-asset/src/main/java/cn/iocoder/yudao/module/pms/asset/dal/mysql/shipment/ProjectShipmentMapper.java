package cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.ProjectShipmentPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ProjectShipmentMapper {
    long count(@Param("query") ProjectShipmentPageQuery query);
    List<DeviceShipmentDO> selectPage(@Param("query") ProjectShipmentPageQuery query);
}
