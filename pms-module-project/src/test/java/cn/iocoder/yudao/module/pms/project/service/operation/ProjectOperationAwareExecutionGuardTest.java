package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectVerifiedOperationScope;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectOperationAwareExecutionGuardTest {
    @Test @SuppressWarnings({"unchecked", "try"})
    void onlyExactOwnerCommandCanReplaceLegacyViewGuard() {
        var old = mock(ProjectBusinessExecutionService.class);
        ObjectProvider<ProjectBusinessExecutionService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(old);
        var execution = mock(ProjectNodeExecutionApi.class);
        var guard = new ProjectOperationAwareExecutionGuard(provider,execution);
        var selection = new ProjectBusinessExecutionSelection(null,
                new ProjectStageExecutionContext(3L,1,4L,1,5L,1,6L,7L,1,1,true));
        var legacy = new WriteRequest(3L,"SOL","SITE_SURVEY",selection);
        guard.lockForWrite(legacy);
        verify(old).lockForWrite(legacy);
        clearInvocations(old,provider);
        try (var tenant = mockStatic(TenantContextHolder.class); var security = mockStatic(SecurityFrameworkUtils.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(1L);
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            try (var scope = ProjectVerifiedOperationScope.open(new ProjectVerifiedOperationScope.Frame(
                    1L,2L,3L,"SOL","SITE_SURVEY","CONFIRM",1,"11",selection))) {
                guard.lockForWrite(new WriteRequest(3L,"SOL","SITE_SURVEY",selection,"CONFIRM",1,"11"));
                verify(execution).lockAndRevalidateStage(selection.stage());
                assertThrows(IllegalStateException.class,() -> guard.lockForWrite(legacy));
                assertThrows(IllegalStateException.class,() -> guard.lockForWrite(new WriteRequest(3L,"SOL","SITE_SURVEY",selection,"REJECT",1,"11")));
                assertThrows(IllegalStateException.class,() -> guard.lockForWrite(new WriteRequest(3L,"SOL","SITE_SURVEY",selection,"CONFIRM",1,"12")));
                assertThrows(IllegalStateException.class,() -> guard.lockForWrite(new WriteRequest(3L,"SOL","SITE_SURVEY",selection,"CONFIRM",2,"11")));
                verifyNoInteractions(old,provider);
            }
        }
        assertNull(ProjectVerifiedOperationScope.current());
    }
}
