package cn.iocoder.yudao.module.system.dal.mysql.user;

import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.ProjectMemberRoleUserPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface ProjectMemberRoleSelectionMapper {

    Long selectCount(@Param("query") ProjectMemberRoleUserPageQuery query);

    List<AdminUserDO> selectPage(@Param("query") ProjectMemberRoleUserPageQuery query);

    Set<Long> selectDepartmentIds(@Param("query") ProjectMemberRoleUserPageQuery query);

}
