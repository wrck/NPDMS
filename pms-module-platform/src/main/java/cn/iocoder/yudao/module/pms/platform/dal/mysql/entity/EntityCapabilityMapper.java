package cn.iocoder.yudao.module.pms.platform.dal.mysql.entity;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EntityCapabilityMapper {
    EntityExtensionDefinitionDO selectDefinition(Long id);
    EntityExtensionDefinitionDO selectDefinitionBySource(@Param("query") EntityDefinitionSourceQuery query);
    Integer selectMaxDefinitionRevision(@Param("query") EntityDefinitionScopeQuery query);
    int insertDefinition(@Param("row") EntityExtensionDefinitionDO row);
    EntityExtensionValueDO selectValues(@Param("query") EntityValueQuery query);
    EntityExtensionValueDO lockValues(@Param("query") EntityValueQuery query);
    int insertValues(@Param("row") EntityExtensionValueDO row);
    int updateValues(@Param("row") EntityExtensionValueDO row);
    int deleteValues(@Param("row") EntityExtensionValueDO row);
    EntityFormBindingDO selectBinding(@Param("query") EntityValueQuery query);
    EntityFormBindingDO lockBinding(@Param("query") EntityValueQuery query);
    int insertBinding(@Param("row") EntityFormBindingDO row);
    int updateBinding(@Param("row") EntityFormBindingDO row);
    int deleteBinding(@Param("row") EntityFormBindingDO row);
}
