package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi.Definition;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.RoleHistoricalAuditCorrection;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.RoleMigrationTenantQuery;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.RoleMigrationAuditActorQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleMigrationMapper {
    List<RoleDO> selectListForMigration(@Param("query") RoleMigrationTenantQuery query);
    List<RoleDO> selectListForMigrationForUpdate(@Param("query") RoleMigrationTenantQuery query);
    List<Long> selectListHistoricalAuditActorIds(@Param("query") RoleMigrationAuditActorQuery query);
    /** Explicit SQL preserves historical NULL actors; the normal autofill must not invent an operator. */
    int insertHistoricalRole(@Param("role") RoleDO role, @Param("audit") Definition audit);
    int updateHistoricalDefinition(@Param("role") RoleDO role, @Param("audit") Definition audit);
    int restoreHistoricalAudit(@Param("correction") RoleHistoricalAuditCorrection correction);
}
