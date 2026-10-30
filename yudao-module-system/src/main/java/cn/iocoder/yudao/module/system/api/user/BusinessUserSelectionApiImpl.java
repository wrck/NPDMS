package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID_ARGUMENT;

@Service
@RequiredArgsConstructor
public class BusinessUserSelectionApiImpl implements BusinessUserSelectionApi {
    private final AdminUserService users;
    private final DeptService departments;

    @Override
    @DataPermission(enable = true)
    public PageResult<Option> page(Query query) {
        if (query == null || query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100
                || query.keyword() != null && query.keyword().length() > 64
                || query.userIds() != null && (query.userIds().size() > 100
                || query.userIds().stream().anyMatch(id -> id <= 0)))
            throw exception(ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        var actor = SecurityFrameworkUtils.getLoginUser();
        var tenant = TenantContextHolder.getTenantId();
        if (actor == null || actor.getId() == null || actor.getId() <= 0 || tenant == null
                || !tenant.equals(actor.getTenantId())) throw exception(FORBIDDEN);
        if (query.userIds() != null && query.userIds().isEmpty()) return PageResult.empty();

        // Exactly the original simple-list (without deptId) services, with ordinary SYSTEM
        // user/department data permissions and tenant filtering. Never enrich through an ignoring API.
        var candidates = users.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus());
        if (candidates.isEmpty()) return PageResult.empty();
        var deptMap = departments.getDeptMap(candidates.stream().map(user -> user.getDeptId()).toList());
        String keyword = query.keyword() == null ? "" : query.keyword().trim().toLowerCase(Locale.ROOT);
        var matches = candidates.stream().filter(user -> query.userIds() == null || query.userIds().contains(user.getId())).filter(user -> {
            DeptDO dept = user.getDeptId() == null ? null : deptMap.get(user.getDeptId());
            return keyword.isEmpty() || matches(user.getNickname(), keyword) || matches(user.getUsername(), keyword)
                    || matches(user.getEmail(), keyword) || matches(dept == null ? null : dept.getName(), keyword);
        }).toList();
        long offset = ((long) query.pageNo() - 1) * query.pageSize();
        var list = matches.stream().skip(offset).limit(query.pageSize()).map(user -> {
            DeptDO dept = user.getDeptId() == null ? null : deptMap.get(user.getDeptId());
            return new Option(user.getId(), user.getNickname(), user.getDeptId(), dept == null ? null : dept.getName());
        }).toList();
        return new PageResult<>(list, (long) matches.size());
    }

    private static boolean matches(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
