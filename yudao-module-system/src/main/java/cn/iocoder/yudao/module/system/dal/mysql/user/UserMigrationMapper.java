package cn.iocoder.yudao.module.system.dal.mysql.user;

import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.Audit;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.UserMigrationTenantQuery;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.UserMigrationReferenceQuery;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.UserHistoricalUpdaterCorrection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMigrationMapper {
    List<cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.MemberIdentity> selectHistoricalMemberIdentities(
            @Param("query") cn.iocoder.yudao.module.system.dal.mysql.user.query.UserHistoricalUsernameQuery query);
    List<cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.HistoricalIdentity> selectHistoricalIdentities(@Param("query") UserMigrationReferenceQuery query);
    List<cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.HistoricalIdentity> selectHistoricalIdentitiesByUsername(
            @Param("query") cn.iocoder.yudao.module.system.dal.mysql.user.query.UserHistoricalUsernameQuery query);
    List<AdminUserDO> selectProfiles(@Param("query") UserMigrationTenantQuery query);
    List<AdminUserDO> selectProfilesForUpdate(@Param("query") UserMigrationTenantQuery query);
    List<Long> selectDepartmentIds(@Param("query") UserMigrationReferenceQuery query);
    List<Long> selectPostIds(@Param("query") UserMigrationReferenceQuery query);
    int insertHistoricalUser(@Param("user") AdminUserDO user, @Param("audit") Audit audit);
    int updateHistoricalProfile(@Param("user") AdminUserDO user, @Param("audit") Audit audit);
    int restoreNullHistoricalUpdater(@Param("correction") UserHistoricalUpdaterCorrection correction);
}
