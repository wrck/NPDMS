package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRows.UserRow;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.Audit;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserMigrationTenantQuery;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserMigrationReferenceQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DirectAccountUserMapper {
    List<UserRow> selectProfiles(@Param("query") UserMigrationTenantQuery query);
    List<UserRow> selectProfilesForUpdate(@Param("query") UserMigrationTenantQuery query);
    List<Long> selectDepartmentIds(@Param("query") UserMigrationReferenceQuery query);
    List<Long> selectPostIds(@Param("query") UserMigrationReferenceQuery query);
    record Write(UserRow user, Audit audit) {}
    int insertHistoricalUser(@Param("write") Write write);
    int updateHistoricalProfile(@Param("write") Write write);
}
