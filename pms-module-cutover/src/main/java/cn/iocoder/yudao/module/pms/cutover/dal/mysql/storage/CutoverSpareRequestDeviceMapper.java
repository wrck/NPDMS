package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverSpareRequestDeviceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverSpareRequestDeviceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 备件请求设备范围：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverSpareRequestDeviceMapper extends BaseMapperX<CutoverSpareRequestDeviceDO> {

    default List<CutoverSpareRequestDeviceDO> selectListByParents(CutoverSpareRequestDeviceListQuery query) {
        if (query == null || query.tenantId() == null || query.applicationReferenceIds() == null || query.applicationReferenceIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverSpareRequestDeviceDO>()
                .eq(CutoverSpareRequestDeviceDO::getTenantId, query.tenantId())
                .in(CutoverSpareRequestDeviceDO::getApplicationReferenceId, query.applicationReferenceIds())
                .orderByAsc(CutoverSpareRequestDeviceDO::getSortOrder)
                .orderByAsc(CutoverSpareRequestDeviceDO::getId));
    }
}
