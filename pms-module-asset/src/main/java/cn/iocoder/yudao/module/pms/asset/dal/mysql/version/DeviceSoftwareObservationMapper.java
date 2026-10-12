package cn.iocoder.yudao.module.pms.asset.dal.mysql.version;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareObservationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface DeviceSoftwareObservationMapper extends BaseMapperX<DeviceSoftwareObservationDO> {
    DeviceSoftwareObservationDO selectLegacyForUpdate(@Param("query") DeviceSoftwareLegacyRecordQuery query);
    DeviceSoftwareObservationDO selectSourceForUpdate(@Param("query") DeviceSoftwareSourceIdentityQuery query);
    default PageResult<DeviceSoftwareObservationDO> selectPage(DeviceSoftwareObservationPageQuery query) {
        PageParam page=new PageParam();page.setPageNo(query.pageNo());page.setPageSize(query.pageSize());
        return selectPage(page,new LambdaQueryWrapperX<DeviceSoftwareObservationDO>()
                .eq(DeviceSoftwareObservationDO::getTenantId,query.tenantId())
                .eq(DeviceSoftwareObservationDO::getProjectId,query.projectId())
                .eqIfPresent(DeviceSoftwareObservationDO::getBarCode,query.deviceSn())
                .orderByDesc(DeviceSoftwareObservationDO::getSourceId).orderByDesc(DeviceSoftwareObservationDO::getId));
    }
}
