package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistFieldOptionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistFieldOptionListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 字段选项：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistFieldOptionMapper extends BaseMapperX<CutoverChecklistFieldOptionDO> {

    default List<CutoverChecklistFieldOptionDO> selectListByParents(CutoverChecklistFieldOptionListQuery query) {
        if (query == null || query.tenantId() == null || query.fieldDefinitionIds() == null || query.fieldDefinitionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistFieldOptionDO>()
                .eq(CutoverChecklistFieldOptionDO::getTenantId, query.tenantId())
                .in(CutoverChecklistFieldOptionDO::getFieldDefinitionId, query.fieldDefinitionIds())
                .orderByAsc(CutoverChecklistFieldOptionDO::getOptionNo)
                .orderByAsc(CutoverChecklistFieldOptionDO::getId));
    }
}
