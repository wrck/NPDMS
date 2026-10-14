package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverSpareNeedSourceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverSpareNeedSourceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 备件需求来源：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverSpareNeedSourceMapper extends BaseMapperX<CutoverSpareNeedSourceDO> {

    default List<CutoverSpareNeedSourceDO> selectListByParents(CutoverSpareNeedSourceListQuery query) {
        if (query == null || query.tenantId() == null || query.applicationReferenceIds() == null || query.applicationReferenceIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverSpareNeedSourceDO>()
                .eq(CutoverSpareNeedSourceDO::getTenantId, query.tenantId())
                .in(CutoverSpareNeedSourceDO::getApplicationReferenceId, query.applicationReferenceIds())
                .orderByAsc(CutoverSpareNeedSourceDO::getSortOrder)
                .orderByAsc(CutoverSpareNeedSourceDO::getId));
    }
}
