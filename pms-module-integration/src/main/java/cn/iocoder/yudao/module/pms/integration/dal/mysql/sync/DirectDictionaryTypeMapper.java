package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi.Type;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DirectDictionaryTypeMapper {
    record IdentityQuery(Long targetId,String type,String name) {}
    record HistoricalInsert(Type row) {}
    List<Type> selectConflictingDefinitionsForUpdate(@Param("query") IdentityQuery query);
    int insertHistorical(@Param("command") HistoricalInsert command);
}
