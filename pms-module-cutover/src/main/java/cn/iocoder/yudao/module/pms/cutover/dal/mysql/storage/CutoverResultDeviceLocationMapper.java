package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultDeviceLocationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultDeviceLocationListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 设备部署位置结果行：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultDeviceLocationMapper extends BaseMapperX<CutoverResultDeviceLocationDO> {

    default List<CutoverResultDeviceLocationDO> selectListByParents(CutoverResultDeviceLocationListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultDeviceLocationDO>()
                .eq(CutoverResultDeviceLocationDO::getTenantId, query.tenantId())
                .in(CutoverResultDeviceLocationDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultDeviceLocationDO::getRowNo)
                .orderByAsc(CutoverResultDeviceLocationDO::getId));
    }
}
