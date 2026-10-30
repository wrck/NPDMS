package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverClosureDeviceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverClosureDeviceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 闭环冻结设备范围：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverClosureDeviceMapper extends BaseMapperX<CutoverClosureDeviceDO> {

    default List<CutoverClosureDeviceDO> selectListByParents(CutoverClosureDeviceListQuery query) {
        if (query == null || query.tenantId() == null || query.closureIds() == null || query.closureIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverClosureDeviceDO>()
                .eq(CutoverClosureDeviceDO::getTenantId, query.tenantId())
                .in(CutoverClosureDeviceDO::getClosureId, query.closureIds())
                .orderByAsc(CutoverClosureDeviceDO::getSortOrder)
                .orderByAsc(CutoverClosureDeviceDO::getId));
    }
}
