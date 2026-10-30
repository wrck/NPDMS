package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceCurrentModelQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DeviceCurrentModelMapper {
    List<Model> selectCurrentModels(@Param("query") DeviceCurrentModelQuery query);
    record Model(Long deviceId, String productModel) {}
}
