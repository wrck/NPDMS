package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverPlanSupportContactChangeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverPlanSupportContactChangeListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 批准后保障联系信息变更：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverPlanSupportContactChangeMapper extends BaseMapperX<CutoverPlanSupportContactChangeDO> {

    default List<CutoverPlanSupportContactChangeDO> selectListByParents(CutoverPlanSupportContactChangeListQuery query) {
        if (query == null || query.tenantId() == null || query.supportArrangementIds() == null || query.supportArrangementIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverPlanSupportContactChangeDO>()
                .eq(CutoverPlanSupportContactChangeDO::getTenantId, query.tenantId())
                .in(CutoverPlanSupportContactChangeDO::getSupportArrangementId, query.supportArrangementIds())
                .orderByAsc(CutoverPlanSupportContactChangeDO::getChangeNo)
                .orderByAsc(CutoverPlanSupportContactChangeDO::getId));
    }
}
