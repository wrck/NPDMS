package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverPlanDeviceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverPlanDeviceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 方案设备资料行：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverPlanDeviceMapper extends BaseMapperX<CutoverPlanDeviceDO> {

    default List<CutoverPlanDeviceDO> selectListByParents(CutoverPlanDeviceListQuery query) {
        if (query == null || query.tenantId() == null || query.planRevisionIds() == null || query.planRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverPlanDeviceDO>()
                .eq(CutoverPlanDeviceDO::getTenantId, query.tenantId())
                .in(CutoverPlanDeviceDO::getPlanRevisionId, query.planRevisionIds())
                .orderByAsc(CutoverPlanDeviceDO::getSortOrder)
                .orderByAsc(CutoverPlanDeviceDO::getId));
    }
}
