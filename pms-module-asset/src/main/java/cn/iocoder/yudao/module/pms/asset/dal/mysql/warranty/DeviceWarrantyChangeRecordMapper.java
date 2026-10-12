package cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyChangeRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DeviceWarrantyChangeRecordMapper extends BaseMapperX<DeviceWarrantyChangeRecordDO> {
    DeviceWarrantyChangeRecordDO selectSourceForUpdate(@Param("query") WarrantyFactSourceIdentityQuery query);
    List<DeviceWarrantyChangeRecordDO> selectProjectPage(@Param("query") WarrantyFactProjectPageQuery query);
    Long countProject(@Param("query") WarrantyFactProjectPageQuery query);
}
