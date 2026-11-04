package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.query.EnabledDepartmentOptionsQuery;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DepartmentOptionsReqVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnabledDepartmentOptionsService {
    private final DeptMapper departments;

    // 部门选项不构成业务数据授权；仅跳过组织数据范围，保留租户隔离。
    @DataPermission(enable = false)
    public List<DeptDO> getOptions(DepartmentOptionsReqVO request) {
        var prefixes = request.getCodePrefixes() == null ? List.<String>of()
                : request.getCodePrefixes().stream().map(this::normalizePrefix).distinct().toList();
        return departments.selectEnabledDepartmentOptions(
                new EnabledDepartmentOptionsQuery(TenantContextHolder.getTenantId(), prefixes));
    }

    private String normalizePrefix(String value) {
        String prefix = value == null ? "" : value.trim();
        if (prefix.endsWith("*")) prefix = prefix.substring(0, prefix.length() - 1);
        if (prefix.isBlank() || prefix.contains("*")) {
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException(
                    "部门编码前缀不能为空，且仅允许末尾使用*");
        }
        return prefix;
    }
}
