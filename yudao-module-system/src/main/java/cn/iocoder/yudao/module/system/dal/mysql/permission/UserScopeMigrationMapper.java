package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.UserScopeMigrationReferenceQuery;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.UserScopeMigrationTenantQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserScopeMigrationMapper {
    record DirectoryReference(Long id, String code, String name) {}
    record RoleReference(Long userId, String code) {}
    List<UserCompanyDepartmentScopeDO> selectRelations(@Param("query") UserScopeMigrationTenantQuery query);
    List<UserCompanyDepartmentScopeDO> selectRelationsForUpdate(@Param("query") UserScopeMigrationTenantQuery query);
    List<Long> selectUserIds(@Param("query") UserScopeMigrationReferenceQuery query);
    List<Long> selectUserIdsForUpdate(@Param("query") UserScopeMigrationReferenceQuery query);
    List<DirectoryReference> selectCompanies(@Param("query") UserScopeMigrationReferenceQuery query);
    List<DirectoryReference> selectCompaniesForUpdate(@Param("query") UserScopeMigrationReferenceQuery query);
    List<DirectoryReference> selectDepartments(@Param("query") UserScopeMigrationReferenceQuery query);
    List<DirectoryReference> selectDepartmentsForUpdate(@Param("query") UserScopeMigrationReferenceQuery query);
    List<RoleReference> selectRoles(@Param("query") UserScopeMigrationReferenceQuery query);
    List<RoleReference> selectRolesForUpdate(@Param("query") UserScopeMigrationReferenceQuery query);
    int insertHistoricalRelation(@Param("relation") UserCompanyDepartmentScopeDO relation,
                                 @Param("audit") cn.iocoder.yudao.module.system.api.migration.SystemUserScopeMigrationApi.Audit audit);
    int updateHistoricalRelation(@Param("relation") UserCompanyDepartmentScopeDO relation,
                                 @Param("audit") cn.iocoder.yudao.module.system.api.migration.SystemUserScopeMigrationApi.Audit audit,
                                 @Param("expectedVersion") Integer expectedVersion);
}
