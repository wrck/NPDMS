package cn.iocoder.yudao.module.pms.platform.service.delivery;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import org.springframework.beans.factory.ObjectProvider;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFieldProvider;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DeliveryOwnerAccessTest {
    static class Row extends BaseBusinessEntity { Long projectId=42L; }
    BusinessEntityPersistenceRegistry registry=mock(BusinessEntityPersistenceRegistry.class);
    BusinessAccessGuard permissions=mock(BusinessAccessGuard.class);
    @SuppressWarnings("unchecked") ObjectProvider<EntityFieldProvider> entities=mock(ObjectProvider.class);
    ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    @SuppressWarnings("unchecked") BaseMapper<BaseBusinessEntity> mapper=mock(BaseMapper.class);
    Row row=new Row();
    cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi contexts=mock(cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.class);
    DeliveryOwnerAccess access=new DeliveryOwnerAccess(List.of(),registry,permissions,entities,scopes,contexts);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); row.setTenantId(1L);row.setId(9L);row.setVersion(1L);
        var declaration=new BusinessModelDeclaration(mock(BusinessModelDescriptor.class),Row.class,mapper,null);
        when(registry.require("SOL","solution")).thenReturn(declaration);
        when(registry.<BaseBusinessEntity>mapperOf(declaration)).thenReturn(mapper);
        when(mapper.selectById(9L)).thenReturn(row);
    }
    @AfterEach void clear() {TenantContextHolder.clear();}
    @Test void functionPermissionDoesNotGrantForeignProjectRead() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(),Set.of(42L)));
        assertThrows(BusinessContractException.class,()->access.require(1L,7L,"SOL","solution",9L,null,false,false,null));
    }
    @Test void ownerActualProjectUsedForAuthorizedRead() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        assertEquals(5L,access.require(1L,7L,"SOL","solution",9L,null,false,false,null));
        verify(scopes).resolveCurrent(new ProjectCurrentScopeQuery(1L,7L,42L,ProjectScopeApi.ACTION_VIEW));
    }
    @Test void changedScopeDuringWriteRejected() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(42L,6L,Set.of(42L),Set.of()));
        assertThrows(BusinessContractException.class,()->access.require(1L,7L,"SOL","solution",9L,null,true,true,5L));
    }
    @Test void tenantMismatchAndUnauthenticatedActorRejectedBeforeOwnerRead() {
        assertThrows(BusinessContractException.class,()->access.require(1L,null,"SOL","solution",9L,null,false,false,null));
        assertThrows(BusinessContractException.class,()->access.require(2L,7L,"SOL","solution",9L,null,false,false,null));
        verifyNoInteractions(registry);
    }
    @Test void foreignTenantOwnerRejected() {
        row.setTenantId(2L);
        assertThrows(BusinessContractException.class,()->access.require(1L,7L,"SOL","solution",9L,null,false,false,null));
        verifyNoInteractions(scopes);
    }
    @Test void projectLinkedSolutionCannotWriteAfterProjectClosed() {
        var query=new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Query(1L,42L,7L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        when(contexts.inspect(query)).thenReturn(new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Context(42L,42L,2L,5L,"CLOSED"));
        when(contexts.lock(query,2L,5L)).thenReturn(new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Context(42L,42L,2L,5L,"CLOSED"));
        assertThrows(BusinessContractException.class,()->access.require(1L,7L,"SOL","solution",9L,null,true,true,null));
    }
    @Test void lifecycleUsesLockedCurrentFactRatherThanEarlierActiveSnapshot() {
        var query=new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Query(1L,42L,7L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        when(contexts.inspect(query)).thenReturn(new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Context(42L,42L,2L,5L,"ACTIVE"));
        when(contexts.lock(query,2L,5L)).thenReturn(new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Context(42L,42L,3L,5L,"CLOSED"));
        assertThrows(BusinessContractException.class,()->access.require(1L,7L,"SOL","solution",9L,null,true,true,null));
        var order=inOrder(scopes,contexts);order.verify(scopes).lockAndRevalidate(any());
        order.verify(contexts).inspect(query);order.verify(contexts).lock(query,2L,5L);
    }
    @Test void activeProjectLinkedWriteRetainsAuthorizedPath() {
        var query=new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Query(1L,42L,7L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        var active=new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Context(42L,42L,2L,5L,"ACTIVE");
        when(contexts.inspect(query)).thenReturn(active);when(contexts.lock(query,2L,5L)).thenReturn(active);
        assertEquals(5L,access.require(1L,7L,"SOL","solution",9L,null,true,true,null));
    }

    @Test void initialUploadChecksActiveProjectWithoutTakingCompletionLock() {
        var query=new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Query(1L,42L,7L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(42L,5L,Set.of(42L),Set.of()));
        when(contexts.inspect(query)).thenReturn(new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Context(42L,42L,2L,5L,"ACTIVE"));
        assertEquals(5L,access.require(1L,7L,"SOL","solution",9L,null,true,false,null));
        verify(contexts,never()).lock(any(),any(),any());
        verify(scopes,never()).lockAndRevalidate(any());
    }
}
