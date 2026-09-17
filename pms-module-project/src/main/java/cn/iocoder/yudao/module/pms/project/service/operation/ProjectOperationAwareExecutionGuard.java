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
import java.util.Objects;

/** Legacy calls delegate unchanged. Only an exact operation, object and execution can reuse PRE. */
@Service
@Primary
@RequiredArgsConstructor
public class ProjectOperationAwareExecutionGuard implements ProjectBusinessExecutionApi {
    private final ObjectProvider<ProjectBusinessExecutionService> legacy;
    private final ProjectNodeExecutionApi executions;
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForWrite(WriteRequest request) {
        var frame = ProjectVerifiedOperationScope.current();
        if (request != null && request.ownerProof() != null) {
            // The proof independently checks the actual callback target and its declared command source.
            // A stale/foreign proof never falls through to either the direct or the legacy path.
            if (!request.ownerProof().matches(TenantContextHolder.getRequiredTenantId(),
                    SecurityFrameworkUtils.getLoginUserId(), request))
                throw new IllegalStateException("CONTROLLED_OPERATION_SCOPE_MISMATCH");
            if (request.selection().task() != null) executions.lockAndRevalidate(request.selection().task());
            else executions.lockAndRevalidateStage(request.selection().stage());
            return;
        }
        if (frame != null && request != null && ProjectVerifiedOperationScope.matches(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), request.projectId(), request.ownerContext(), request.objectType(),
                request.selection(), request.operationCode(), request.operationVersion(), request.objectId())) {
            if (request.selection().task() != null) executions.lockAndRevalidate(request.selection().task());
            else executions.lockAndRevalidateStage(request.selection().stage());
            return;
        }
        if (frame != null && request != null && Objects.equals(frame.ownerContext(), request.ownerContext())
                && Objects.equals(frame.objectType(), request.objectType())) {
            // A nested same-Owner write must enter its own verified command, not downgrade to the legacy guard.
            throw new IllegalStateException("CONTROLLED_OPERATION_SCOPE_MISMATCH");
        }
        legacy.getObject().lockForWrite(request);
    }
}
