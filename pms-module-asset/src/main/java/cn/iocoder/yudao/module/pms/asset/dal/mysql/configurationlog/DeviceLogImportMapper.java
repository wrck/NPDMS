package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogImportDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogImportLockQuery;
import org.apache.ibatis.annotations.*;
@Mapper public interface DeviceLogImportMapper extends BaseMapperX<DeviceLogImportDO> {
    default DeviceLogImportDO findImport(DeviceLogImportLockQuery query) {
        return selectOne(new LambdaQueryWrapperX<DeviceLogImportDO>().eq(DeviceLogImportDO::getTenantId, query.tenantId())
                .eq(DeviceLogImportDO::getSourceKey, query.sourceKey()));
    }
    DeviceLogImportDO lockImport(@Param("query") DeviceLogImportLockQuery query);
}
