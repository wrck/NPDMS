package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogFileDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface DeviceLogFileMapper extends BaseMapperX<DeviceLogFileDO> {
    DeviceLogFileDO lockFile(@Param("query") DeviceLogFileLockQuery query);
    List<DeviceLogFileDO> lockImportFiles(@Param("query") DeviceLogImportFilesQuery query);
    default List<DeviceLogFileDO> projectFiles(DeviceLogProjectQuery query) {
        return selectList(new LambdaQueryWrapperX<DeviceLogFileDO>().eq(DeviceLogFileDO::getTenantId, query.tenantId())
                .eq(DeviceLogFileDO::getProjectId, query.projectId()).isNotNull(DeviceLogFileDO::getDeviceId)
                .isNotNull(DeviceLogFileDO::getMaterialId).orderByAsc(DeviceLogFileDO::getDeviceId)
                .orderByDesc(DeviceLogFileDO::getDeviceVersion));
    }
    default List<DeviceLogFileDO> history(DeviceLogHistoryQuery query) {
        return selectList(new LambdaQueryWrapperX<DeviceLogFileDO>().eq(DeviceLogFileDO::getTenantId, query.tenantId())
                .eq(DeviceLogFileDO::getProjectId, query.projectId()).eq(DeviceLogFileDO::getDeviceId, query.deviceId())
                .isNotNull(DeviceLogFileDO::getMaterialId).orderByDesc(DeviceLogFileDO::getDeviceVersion));
    }
    default List<DeviceLogFileDO> importFiles(DeviceLogImportFilesQuery query) {
        return selectList(new LambdaQueryWrapperX<DeviceLogFileDO>().eq(DeviceLogFileDO::getTenantId, query.tenantId())
                .eq(DeviceLogFileDO::getImportId, query.importId())
                .orderByAsc(DeviceLogFileDO::getDeviceId));
    }
}
