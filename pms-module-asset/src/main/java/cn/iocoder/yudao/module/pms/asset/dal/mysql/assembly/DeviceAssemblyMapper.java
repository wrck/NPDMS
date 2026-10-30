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

    int ensureGraphLock(@Param("query") cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyTenantLockQuery query);
    Long lockGraph(@Param("query") cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyTenantLockQuery query);
    default void acquireGraph(cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyTenantLockQuery query) {
        if (query.tenantId()==null) throw new IllegalArgumentException("装配图租户缺失");
        ensureGraphLock(query);
        if (!query.tenantId().equals(lockGraph(query))) throw new IllegalStateException("装配图锁定失败");
    }

    List<DeviceAssemblyDO> lockLogConflicts(@Param("query")
            cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceLogAssemblyConflictQuery query);

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
