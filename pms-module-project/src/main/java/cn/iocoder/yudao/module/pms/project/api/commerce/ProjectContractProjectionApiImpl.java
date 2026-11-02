package cn.iocoder.yudao.module.pms.project.api.commerce;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectContractProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectContractProjectionApiImpl implements ProjectContractProjectionApi {
    private final ProjectContractProjectionService service;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void refresh(ProjectContractProjectionRefresh request) {
        if (request == null || request.projectId() == null || request.projectId() <= 0
                || !Objects.equals(request.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("项目合同号投影请求无效");
        }
        service.refreshProject(request.projectId());
    }
}
