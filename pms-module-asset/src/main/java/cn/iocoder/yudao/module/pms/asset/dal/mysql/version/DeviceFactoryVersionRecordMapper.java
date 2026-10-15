package cn.iocoder.yudao.module.pms.asset.dal.mysql.version;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceFactoryVersionRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface DeviceFactoryVersionRecordMapper extends BaseMapperX<DeviceFactoryVersionRecordDO> {
    DeviceFactoryVersionRecordDO selectGlobalRecordForUpdate(@Param("query") DeviceFactoryGlobalRecordQuery query);
    DeviceFactoryVersionRecordDO selectByIdForUpdate(@Param("query") DeviceFactoryVersionRecordIdentityQuery query);
    default PageResult<DeviceFactoryVersionRecordDO> selectPage(DeviceFactoryVersionRecordPageQuery query) {
        PageParam page=new PageParam();page.setPageNo(query.pageNo());page.setPageSize(query.pageSize());
        return selectPage(page,new LambdaQueryWrapperX<DeviceFactoryVersionRecordDO>()
                .eq(DeviceFactoryVersionRecordDO::getTenantId,query.tenantId())
                .eq(DeviceFactoryVersionRecordDO::getProjectId,query.projectId())
                .eqIfPresent(DeviceFactoryVersionRecordDO::getDeviceSn,query.deviceSn())
                .orderByDesc(DeviceFactoryVersionRecordDO::getId));
    }
}
