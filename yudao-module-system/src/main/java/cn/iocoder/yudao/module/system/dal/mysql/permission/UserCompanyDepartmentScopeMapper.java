package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.ActiveUserScopeListQuery;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.OrganizationUserCandidatePageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserCompanyDepartmentScopeMapper extends BaseMapperX<UserCompanyDepartmentScopeDO> {

    default List<UserCompanyDepartmentScopeDO> selectActiveListByUserId(ActiveUserScopeListQuery query) {
        return selectList(new LambdaQueryWrapperX<UserCompanyDepartmentScopeDO>()
                .eq(UserCompanyDepartmentScopeDO::getUserId, query.getUserId())
                .eq(UserCompanyDepartmentScopeDO::getStatus, query.getEnabledStatus())
                .le(UserCompanyDepartmentScopeDO::getEffectiveFrom, query.getCurrentTime())
                .and(wrapper -> wrapper.isNull(UserCompanyDepartmentScopeDO::getEffectiveTo)
                        .or().gt(UserCompanyDepartmentScopeDO::getEffectiveTo, query.getCurrentTime())));
    }

    Long selectActiveUserCandidateCount(@Param("query") OrganizationUserCandidatePageQuery query);

    List<OrganizationUserCandidateRespDTO> selectActiveUserCandidatePage(
            @Param("query") OrganizationUserCandidatePageQuery query);

}
