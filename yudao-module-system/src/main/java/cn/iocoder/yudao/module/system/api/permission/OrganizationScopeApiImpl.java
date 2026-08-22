package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class OrganizationScopeApiImpl implements OrganizationScopeApi {

    @Resource
    private UserCompanyDepartmentScopeMapper scopeMapper;

    @Override
    public List<UserCompanyDepartmentScopeRespDTO> getActiveScopes(Long userId) {
        List<UserCompanyDepartmentScopeDO> scopes = scopeMapper.selectActiveListByUserId(
                userId, LocalDateTime.now(), CommonStatusEnum.ENABLE.getStatus());
        return BeanUtils.toBean(scopes, UserCompanyDepartmentScopeRespDTO.class);
    }

    @Override
    public boolean hasScope(Long userId, Long companyId, Long departmentId) {
        return getActiveScopes(userId).stream().anyMatch(scope ->
                Objects.equals(scope.getCompanyId(), companyId)
                        && Objects.equals(scope.getDepartmentId(), departmentId));
    }

}
