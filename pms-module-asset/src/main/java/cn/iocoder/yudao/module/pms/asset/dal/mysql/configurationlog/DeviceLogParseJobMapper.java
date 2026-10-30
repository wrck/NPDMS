package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogParseJobDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface DeviceLogParseJobMapper extends BaseMapperX<DeviceLogParseJobDO> {
    DeviceLogParseJobDO lock(@org.apache.ibatis.annotations.Param("query")
                            cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogParseJobLockQuery query);
    default java.util.List<DeviceLogParseJobDO> pending(cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogParsePendingQuery query) {
        return selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeviceLogParseJobDO>(1,query.limit(),false),
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<DeviceLogParseJobDO>()
                        .eq(DeviceLogParseJobDO::getTenantId,query.tenantId()).eq(DeviceLogParseJobDO::getProjected,false)
                        .notIn(DeviceLogParseJobDO::getState,"FAILED","CANCELLED")
                        .orderByAsc(DeviceLogParseJobDO::getUpdateTime).orderByAsc(DeviceLogParseJobDO::getId)).getRecords();
    }
}
