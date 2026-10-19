package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryDataMigrationApi.Item;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi.Type;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DirectDictionaryDataMapper {
    record IdentityQuery(Long targetId,String dictType,String value) {}
    record TypeQuery(String type) {}
    record HistoricalInsert(Item row) {}
    List<Item> selectConflictingValuesForUpdate(@Param("query") IdentityQuery query);
    List<Type> selectTypesForUpdate(@Param("query") TypeQuery query);
    int insertHistorical(@Param("command") HistoricalInsert command);
}
