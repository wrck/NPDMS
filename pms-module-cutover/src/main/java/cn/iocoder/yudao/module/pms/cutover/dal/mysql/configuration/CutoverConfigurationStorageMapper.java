package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationStorageQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverDraftFieldDeleteQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 只清理已锁定草稿的可编辑明细，发布后所有删除均无匹配行。 */
@Mapper
public interface CutoverConfigurationStorageMapper {
    int deleteDraftOptions(@Param("query") CutoverConfigurationStorageQuery query);
    int deleteDraftField(@Param("query") CutoverDraftFieldDeleteQuery query);
    int deleteDraftMappings(@Param("query") CutoverConfigurationStorageQuery query);
    int deleteDraftConditions(@Param("query") CutoverConfigurationStorageQuery query);
    int deleteDraftDimensions(@Param("query") CutoverConfigurationStorageQuery query);
    int deleteDraftDictionaryValues(@Param("query") CutoverConfigurationStorageQuery query);
    int deleteDraftSectionScopes(@Param("query") CutoverConfigurationStorageQuery query);
    int deleteDraftSections(@Param("query") CutoverConfigurationStorageQuery query);
}
