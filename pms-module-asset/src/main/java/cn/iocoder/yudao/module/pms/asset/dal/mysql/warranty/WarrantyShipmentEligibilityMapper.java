package cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface WarrantyShipmentEligibilityMapper {
    record Query(Long tenantId,List<String> deviceSns) {}
    record Shipment(String deviceSn,String packageSourceKey,String rmaNo) {}
    List<Shipment> selectShipments(@Param("query") Query query);
}
