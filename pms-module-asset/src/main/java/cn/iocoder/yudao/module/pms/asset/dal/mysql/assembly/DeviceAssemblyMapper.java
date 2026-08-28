package cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceAssemblyDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyPathQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblySourceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyTreeQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeviceAssemblyMapper extends BaseMapperX<DeviceAssemblyDO> {

    boolean existsBySource(@Param("query") DeviceAssemblySourceQuery query);

    boolean existsPath(@Param("query") DeviceAssemblyPathQuery query);

    List<DeviceAssemblyDO> selectCurrentTree(@Param("query") DeviceAssemblyTreeQuery query);

    int closeCurrentByChild(
            @Param("tenantId") Long tenantId,
            @Param("childDeviceSn") String childDeviceSn,
            @Param("effectiveAt") LocalDateTime effectiveAt);

    int closeCurrentByPosition(
            @Param("tenantId") Long tenantId,
            @Param("parentDeviceSn") String parentDeviceSn,
            @Param("positionCode") String positionCode,
            @Param("effectiveAt") LocalDateTime effectiveAt);
}
