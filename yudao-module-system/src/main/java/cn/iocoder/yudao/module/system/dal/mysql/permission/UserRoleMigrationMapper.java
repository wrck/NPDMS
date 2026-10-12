package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.module.system.api.migration.SystemUserRoleMigrationApi.Relation;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.RoleMigrationTenantQuery;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.UserRoleMigrationQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserRoleMigrationMapper {
    record RoleReference(Long id, String code) {}
    List<Relation> selectRelations(@Param("query") RoleMigrationTenantQuery query);
    List<Relation> selectRelationsForUpdate(@Param("query") RoleMigrationTenantQuery query);
    List<Long> selectUserIds(@Param("query") UserRoleMigrationQuery query);
    List<Long> selectUserIdsForUpdate(@Param("query") UserRoleMigrationQuery query);
    List<RoleReference> selectRoles(@Param("query") UserRoleMigrationQuery query);
    List<RoleReference> selectRolesForUpdate(@Param("query") UserRoleMigrationQuery query);
    int insertHistoricalRelation(@Param("tenantId") Long tenantId, @Param("relation") Relation relation);
}
