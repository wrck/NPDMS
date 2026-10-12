package cn.iocoder.yudao.module.pms.asset.dal.mysql.version;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareChangeBatchDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface DeviceSoftwareChangeBatchMapper extends BaseMapperX<DeviceSoftwareChangeBatchDO> {
    DeviceSoftwareChangeBatchDO selectRecordForUpdate(@Param("query") DeviceSoftwareBatchIdentityQuery query);
    cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareLegacyBatchDO selectLegacyForUpdate(@Param("query") DeviceSoftwareLegacyRecordQuery query);
    int correctHistoricalAudit(@Param("row") DeviceSoftwareBatchHistoricalWrite row);
    int insertHistorical(@Param("row") DeviceSoftwareBatchHistoricalWrite row);
    default PageResult<DeviceSoftwareChangeBatchDO> selectPage(DeviceSoftwareBatchPageQuery query) {
        PageParam page=new PageParam();page.setPageNo(query.pageNo());page.setPageSize(query.pageSize());
        return selectPage(page,new LambdaQueryWrapperX<DeviceSoftwareChangeBatchDO>()
                .eq(DeviceSoftwareChangeBatchDO::getTenantId,query.tenantId())
                .eq(DeviceSoftwareChangeBatchDO::getProjectId,query.projectId())
                .orderByDesc(DeviceSoftwareChangeBatchDO::getCreateTime).orderByDesc(DeviceSoftwareChangeBatchDO::getId));
    }
}
