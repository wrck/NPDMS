package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRows.ScopeRow;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserScopeMigrationReferenceQuery;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserScopeMigrationTenantQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DirectAccountScopeMapper {
    record DirectoryReference(Long id, String code, String name) {}
    record RoleReference(Long userId, String code) {}
    List<ScopeRow> selectRelations(@Param("query") UserScopeMigrationTenantQuery query);
    List<ScopeRow> selectRelationsForUpdate(@Param("query") UserScopeMigrationTenantQuery query);
    List<Long> selectUserIds(@Param("query") UserScopeMigrationReferenceQuery query);
    List<Long> selectUserIdsForUpdate(@Param("query") UserScopeMigrationReferenceQuery query);
    List<DirectoryReference> selectCompanies(@Param("query") UserScopeMigrationReferenceQuery query);
    List<DirectoryReference> selectCompaniesForUpdate(@Param("query") UserScopeMigrationReferenceQuery query);
    List<DirectoryReference> selectDepartments(@Param("query") UserScopeMigrationReferenceQuery query);
    List<DirectoryReference> selectDepartmentsForUpdate(@Param("query") UserScopeMigrationReferenceQuery query);
    List<RoleReference> selectRoles(@Param("query") UserScopeMigrationReferenceQuery query);
    List<RoleReference> selectRolesForUpdate(@Param("query") UserScopeMigrationReferenceQuery query);
    record Write(ScopeRow relation, cn.iocoder.yudao.module.system.api.migration.SystemUserScopeMigrationApi.Audit audit, Integer expectedVersion) {}
    int insertHistoricalRelation(@Param("write") Write write);
    int updateHistoricalRelation(@Param("write") Write write);
}
