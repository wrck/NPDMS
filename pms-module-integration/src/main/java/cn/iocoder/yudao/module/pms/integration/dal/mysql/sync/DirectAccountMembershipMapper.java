package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.system.api.migration.SystemUserRoleMigrationApi.Relation;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.RoleMigrationTenantQuery;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserRoleMigrationQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DirectAccountMembershipMapper {
    record RoleReference(Long id, String code) {}
    List<Relation> selectRelations(@Param("query") RoleMigrationTenantQuery query);
    List<Relation> selectRelationsForUpdate(@Param("query") RoleMigrationTenantQuery query);
    List<Long> selectUserIds(@Param("query") UserRoleMigrationQuery query);
    List<Long> selectUserIdsForUpdate(@Param("query") UserRoleMigrationQuery query);
    List<RoleReference> selectRoles(@Param("query") UserRoleMigrationQuery query);
    List<RoleReference> selectRolesForUpdate(@Param("query") UserRoleMigrationQuery query);
    record Write(Long tenantId, Relation relation) {}
    int insertHistoricalRelation(@Param("write") Write write);
}
