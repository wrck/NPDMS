package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserCompanyDepartmentScopeMapper extends BaseMapperX<UserCompanyDepartmentScopeDO> {

    default List<UserCompanyDepartmentScopeDO> selectActiveListByUserId(Long userId, LocalDateTime currentTime,
                                                                          Integer enabledStatus) {
        return selectList(new LambdaQueryWrapperX<UserCompanyDepartmentScopeDO>()
                .eq(UserCompanyDepartmentScopeDO::getUserId, userId)
                .eq(UserCompanyDepartmentScopeDO::getStatus, enabledStatus)
                .le(UserCompanyDepartmentScopeDO::getEffectiveFrom, currentTime)
                .and(wrapper -> wrapper.isNull(UserCompanyDepartmentScopeDO::getEffectiveTo)
                        .or().gt(UserCompanyDepartmentScopeDO::getEffectiveTo, currentTime)));
    }

}
