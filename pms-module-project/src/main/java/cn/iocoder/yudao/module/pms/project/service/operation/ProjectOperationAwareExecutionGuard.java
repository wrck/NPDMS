package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectVerifiedOperationScope;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Legacy calls delegate unchanged. Only a verified, exact controlled invocation replaces the view-based guard. */
@Service
@Primary
@RequiredArgsConstructor
public class ProjectOperationAwareExecutionGuard implements ProjectBusinessExecutionApi {
    private final ObjectProvider<ProjectBusinessExecutionService> legacy;
    private final ProjectNodeExecutionApi executions;
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForWrite(WriteRequest request) {
        if (request != null && ProjectVerifiedOperationScope.matches(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), request.projectId(), request.ownerContext(), request.objectType(), request.selection())) {
            // The common executor already checked the actual operation/Owner and PRE in this transaction.
            // Recheck the execution identity; registry page enablement is no longer an extra business authority.
            if (request.selection().task() != null) executions.lockAndRevalidate(request.selection().task());
            else executions.lockAndRevalidateStage(request.selection().stage());
            return;
        }
        legacy.getObject().lockForWrite(request);
    }
}
