package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationItemHistoryQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverChecklistItemDefinitionRevisionMapper
        extends BaseMapperX<CutoverChecklistItemDefinitionRevisionDO> {

    default List<CutoverChecklistItemDefinitionRevisionDO> selectListByRevision(
            CutoverConfigurationChildrenQuery query) {
        return selectList(new LambdaQueryWrapperX<CutoverChecklistItemDefinitionRevisionDO>()
                .eq(CutoverChecklistItemDefinitionRevisionDO::getConfigurationRevisionId,
                        query.configurationRevisionId())
                .orderByAsc(CutoverChecklistItemDefinitionRevisionDO::getSortOrder)
                .orderByAsc(CutoverChecklistItemDefinitionRevisionDO::getId));
    }

    int hardDeleteByRevisionId(@Param("query") CutoverConfigurationChildrenQuery query);

    List<CutoverChecklistItemDefinitionRevisionDO> selectHistoryByStableKeys(
            @Param("query") CutoverConfigurationItemHistoryQuery query);
}
