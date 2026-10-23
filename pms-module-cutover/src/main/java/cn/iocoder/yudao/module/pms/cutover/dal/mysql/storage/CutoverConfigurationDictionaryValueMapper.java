package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverConfigurationDictionaryValueDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverConfigurationDictionaryValueListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 配置冻结字典值：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverConfigurationDictionaryValueMapper extends BaseMapperX<CutoverConfigurationDictionaryValueDO> {

    default List<CutoverConfigurationDictionaryValueDO> selectListByParents(CutoverConfigurationDictionaryValueListQuery query) {
        if (query == null || query.tenantId() == null || query.configurationRevisionIds() == null || query.configurationRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverConfigurationDictionaryValueDO>()
                .eq(CutoverConfigurationDictionaryValueDO::getTenantId, query.tenantId())
                .in(CutoverConfigurationDictionaryValueDO::getConfigurationRevisionId, query.configurationRevisionIds())
                .orderByAsc(CutoverConfigurationDictionaryValueDO::getSortOrder)
                .orderByAsc(CutoverConfigurationDictionaryValueDO::getId));
    }
}
