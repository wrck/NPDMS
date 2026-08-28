package cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.query.DeviceWarrantyRecordPageQuery;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceWarrantyRecordMapper extends BaseMapperX<DeviceWarrantyRecordDO> {

    default PageResult<DeviceWarrantyRecordDO> selectPage(DeviceWarrantyRecordPageQuery query) {
        PageParam page = new PageParam();
        page.setPageNo(Math.toIntExact(query.pageNo()));
        page.setPageSize(Math.toIntExact(query.pageSize()));
        return selectPage(page, new LambdaQueryWrapperX<DeviceWarrantyRecordDO>()
                .eq(DeviceWarrantyRecordDO::getTenantId, query.tenantId())
                .eq(DeviceWarrantyRecordDO::getDeviceSn, query.deviceSn())
                .orderByDesc(DeviceWarrantyRecordDO::getWarrantyStartDate)
                .orderByDesc(DeviceWarrantyRecordDO::getId));
    }
}
