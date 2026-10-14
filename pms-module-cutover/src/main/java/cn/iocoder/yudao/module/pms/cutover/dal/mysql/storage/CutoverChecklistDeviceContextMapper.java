package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistDeviceContextDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistDeviceContextListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** P3设备及软件版本上下文：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistDeviceContextMapper extends BaseMapperX<CutoverChecklistDeviceContextDO> {

    default List<CutoverChecklistDeviceContextDO> selectListByParents(CutoverChecklistDeviceContextListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistIds() == null || query.checklistIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistDeviceContextDO>()
                .eq(CutoverChecklistDeviceContextDO::getTenantId, query.tenantId())
                .in(CutoverChecklistDeviceContextDO::getChecklistId, query.checklistIds())
                .orderByAsc(CutoverChecklistDeviceContextDO::getSortOrder)
                .orderByAsc(CutoverChecklistDeviceContextDO::getId));
    }
}
