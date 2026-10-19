package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface DirectProjectPartyMapper {
 record MigrationSourceQuery(Long tenantId,String sourceTable,String sourceKey) {}
 record MigrationWrite(DirectProjectPartyRow row,String creator,String updater) {}
 List<DirectProjectPartyRow> selectMigrationSourceForUpdate(@Param("query") MigrationSourceQuery query);
 int insertMigrationParty(@Param("command") MigrationWrite command);
 int updateMigrationParty(@Param("command") MigrationWrite command);
}
