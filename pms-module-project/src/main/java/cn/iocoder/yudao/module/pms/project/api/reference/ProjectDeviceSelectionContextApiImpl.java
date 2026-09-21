package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class ProjectDeviceSelectionContextApiImpl implements ProjectDeviceSelectionContextApi {
    private final ProjectMasterMapper mapper;
    private final ProjectScopeApi scopeApi;
    private final ProjectContractQueryApi contractApi;

    @Override
    public Set<String> getContractNumbers(Long projectId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long userId = getLoginUserId();
        if (projectId == null || projectId <= 0 || userId == null) throw exception(PROJECT_NOT_EXISTS);
        var scope = scopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, userId, projectId, ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        var project = mapper.selectById(projectId);
        if (project == null || !Objects.equals(tenantId, project.getTenantId())) throw exception(PROJECT_NOT_EXISTS);
        Set<String> contracts = contractApi.getCurrentContractNumbers(projectId);
        // 正式商业关系优先；尚未建立正式关系的手工项目使用其登记合同号。
        if (contracts.isEmpty() && project.getContractNo() != null && !project.getContractNo().isBlank()) {
            return Set.of(project.getContractNo().trim());
        }
        return contracts;
    }
}
