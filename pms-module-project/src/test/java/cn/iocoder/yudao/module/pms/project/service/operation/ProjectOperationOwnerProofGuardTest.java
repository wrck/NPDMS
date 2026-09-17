package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "try"})
class ProjectOperationOwnerProofGuardTest {
    @Test void actualGuardAcceptsProofButStillRevalidatesExecutionAndRejectsReuse() {
        for (boolean stage : new boolean[]{false,true}) {
            ObjectProvider<ProjectBusinessExecutionService> legacy = mock(ObjectProvider.class);
            var executions = mock(ProjectNodeExecutionApi.class);
            var guard = new ProjectOperationAwareExecutionGuard(legacy,executions);
            var selection = stage ? new ProjectBusinessExecutionSelection(null,new ProjectStageExecutionContext(3L,1,4L,1,5L,1,6L,7L,1,1,true))
                    : new ProjectBusinessExecutionSelection(new ProjectTaskExecutionContext(3L,1,4L,1,5L,1,6L,7L,1,1,8L,1,true,null),null);
            try (var tenants = mockStatic(TenantContextHolder.class); var actors = mockStatic(SecurityFrameworkUtils.class)) {
                tenants.when(TenantContextHolder::getRequiredTenantId).thenReturn(1L);
                actors.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
                var frame = new ProjectVerifiedOperationScope.Frame(1L,2L,3L,"SOL","ENTITY","SOL.ENTITY.COPY",1,"40",selection);
                WriteRequest target;
                try (var verified = ProjectVerifiedOperationScope.open(frame);
                     var owner = ProjectOwnerOperationScope.open(new ProjectOwnerOperationScope.Declaration(1L,2L,3L,"SOL","ENTITY","SOL.ENTITY.COPY",1,"40",selection))) {
                    ProjectOwnerOperationScope.registerCreated(1L,2L,3L,"SOL","ENTITY","SOL.ENTITY.COPY",1,"40","41");
                    target = ProjectOwnerOperationScope.writeRequest(1L,2L,3L,"SOL","ENTITY","41",selection);
                    guard.lockForWrite(target);
                    if (stage) verify(executions).lockAndRevalidateStage(selection.stage());
                    else verify(executions).lockAndRevalidate(selection.task());
                    assertThrows(IllegalStateException.class, () -> guard.lockForWrite(new WriteRequest(3L,"SOL","ENTITY",selection)));
                    var forged = new WriteRequest(3L,"SOL","ENTITY",selection,"SOL.ENTITY.COPY",1,"42",target.ownerProof());
                    assertThrows(IllegalStateException.class, () -> guard.lockForWrite(forged));
                    verifyNoInteractions(legacy);
                }
                assertThrows(IllegalStateException.class, () -> guard.lockForWrite(target));
                verifyNoInteractions(legacy);
            }
        }
    }
}
