package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.module.system.dal.mysql.permission.query.ExplicitPermissionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 仅查询System自有授权表，不复用缓存或超级管理员角色判断。 */
@Mapper
public interface ExplicitPermissionMapper {

    boolean existsExplicitPermission(@Param("query") ExplicitPermissionQuery query);

    Long selectActiveUserForUpdate(@Param("query") ExplicitPermissionQuery query);

    List<Long> selectRoleIdsForUpdate(@Param("query") ExplicitPermissionQuery query);

    List<Long> selectUserRoleIdsForUpdate(@Param("query") ExplicitPermissionQuery query);

    List<Long> selectRoleMenuIdsForUpdate(@Param("query") ExplicitPermissionQuery query);

    List<Long> selectPermittedMenuIdsForUpdate(@Param("query") ExplicitPermissionQuery query);
}
