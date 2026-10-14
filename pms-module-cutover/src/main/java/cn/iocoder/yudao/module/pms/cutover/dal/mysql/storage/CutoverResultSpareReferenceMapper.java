package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultSpareReferenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultSpareReferenceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 重大割接备件判断答案：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultSpareReferenceMapper extends BaseMapperX<CutoverResultSpareReferenceDO> {

    default List<CutoverResultSpareReferenceDO> selectListByParents(CutoverResultSpareReferenceListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultSpareReferenceDO>()
                .eq(CutoverResultSpareReferenceDO::getTenantId, query.tenantId())
                .in(CutoverResultSpareReferenceDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultSpareReferenceDO::getId));
    }
}
