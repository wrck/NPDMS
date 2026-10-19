package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRows.RoleRow;
import cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi.Definition;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.RoleMigrationTenantQuery;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.RoleMigrationAuditActorQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DirectAccountRoleMapper {
    List<RoleRow> selectListForMigration(@Param("query") RoleMigrationTenantQuery query);
    List<RoleRow> selectListForMigrationForUpdate(@Param("query") RoleMigrationTenantQuery query);
    List<Long> selectListHistoricalAuditActorIds(@Param("query") RoleMigrationAuditActorQuery query);
    /** Explicit SQL preserves historical NULL actors; the normal autofill must not invent an operator. */
    record Write(RoleRow role, Definition audit) {}
    int insertHistoricalRole(@Param("write") Write write);
    int updateHistoricalDefinition(@Param("write") Write write);
}
