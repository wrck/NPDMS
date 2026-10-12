package cn.iocoder.yudao.module.system.dal.mysql.dept;

import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi.Department;
import cn.iocoder.yudao.module.system.dal.mysql.dept.query.DepartmentMigrationTenantQuery;
import cn.iocoder.yudao.module.system.dal.mysql.dept.query.DepartmentMigrationLeaderQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DepartmentMigrationMapper {
    List<Department> selectCatalogue(@Param("query") DepartmentMigrationTenantQuery query);
    List<Department> selectCatalogueForUpdate(@Param("query") DepartmentMigrationTenantQuery query);
    int insertHistoricalDepartment(@Param("tenantId") Long tenantId,@Param("department") Department department);
    List<Long> selectLeaderUserIds(@Param("query") DepartmentMigrationLeaderQuery query);
    int completeHistoricalLeader(@Param("tenantId") Long tenantId,@Param("before") Department before,@Param("after") Department after);
}
