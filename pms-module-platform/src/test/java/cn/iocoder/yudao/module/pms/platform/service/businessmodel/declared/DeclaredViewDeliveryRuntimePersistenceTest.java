package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.*;
import cn.iocoder.yudao.module.pms.platform.service.businessview.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import cn.iocoder.yudao.module.pms.platform.support.persistence.*;
import cn.iocoder.yudao.module.pms.project.api.scope.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApiImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfSystemProperty(named="npdms.declared.exclusive",matches="true")
class DeclaredViewDeliveryRuntimePersistenceTest {
    DeclaredBusinessRuntimePersistenceTest.Runtime runtime;TransactionTemplate transaction;
    ProjectAcceptanceContextApi contexts=mock(ProjectAcceptanceContextApi.class);DeliveryOwnerAccess owner;
    @BeforeEach void start() throws Exception {
        runtime=new DeclaredBusinessRuntimePersistenceTest.Runtime(true,0,false,false,"DECLARED_NOTE",true);
        DeclaredBusinessRuntimePersistenceTest.initializeExclusiveSchema(runtime);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(runtime.source));login(7,880001);
        var persistence=new BusinessEntityPersistenceRegistry(runtime.context.getBeanProvider(BusinessModelContributor.class));
        var bridge=new DeclaredBusinessDeliveryBridge(persistence,runtime.dispatcher,runtime.access,runtime.projectApi,contexts);
        owner=new DeliveryOwnerAccess(List.of(),persistence,runtime.guard,runtime.context.getBeanProvider(EntityFieldProvider.class),runtime.projectApi,contexts,bridge);
        var active=new ProjectAcceptanceContextApi.Context(99L,99L,0L,1L,"ACTIVE");when(contexts.inspect(any())).thenReturn(active);when(contexts.lock(any(),any(),any())).thenReturn(active);
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();TenantContextHolder.clear();if(runtime!=null)runtime.close();}
    private void login(long tenant,long id){TenantContextHolder.setTenantId(tenant);var user=new LoginUser();user.setTenantId(tenant);user.setId(id);user.setUserType(2);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));}
    private EntityRef create(){return runtime.dispatcher.dispatch(new BusinessOperationRequest("create",1,null,"IT","declaredNote",Map.of("projectRef",99L,"title","shared"),"bridge-create",null,OperationEntryKind.INDEPENDENT,null)).entityRef();}
    @Test void declarationIdentityAliasNeedsNoServiceBeanAndPreservesLargeId(){
        var identities=runtime.context.getBean(cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntityIdentityResolver.class);
        var ref=new EntityRef(7L,"IT","declaredNote",9007199254740993L);
        assertEquals("DECLARED_NOTE",identities.nativeRef(ref).entityType());assertEquals(ref,identities.declaredRef(identities.nativeRef(ref)));
        assertEquals(0,runtime.context.getBeanNamesForType(cn.iocoder.yudao.module.pms.platform.support.service.AbstractBusinessApplicationService.class).length);
    }
    @Test void projectRefDeliveryReadAndWriteUseIndependentOperationPermissionAndActiveScope(){
        var ref=create();assertEquals(99L,owner.projectId("IT","declaredNote",ref.entityId()));
        login(7,880002);assertEquals(1L,owner.require("IT","declaredNote",ref.entityId(),"IT",false,false));
        assertThrows(BusinessContractException.class,()->owner.require("IT","declaredNote",ref.entityId(),"IT",true,false));
        login(7,880001);runtime.jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id=980002");
        assertEquals(1L,owner.require("IT","declaredNote",ref.entityId(),"IT",true,false));
        assertEquals(Long.valueOf(1),transaction.execute(status->owner.require("IT","declaredNote",ref.entityId(),"IT",true,true)));
        assertThrows(BusinessContractException.class,()->owner.require(7L,880001L,"IT","declaredNote",ref.entityId(),"IT",true,false,2L));
        verify(runtime.projectApi,atLeastOnce()).resolveCurrent(new ProjectCurrentScopeQuery(7L,880001L,99L,ProjectScopeApi.ACTION_MANAGE));
        assertEquals(1,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record",Integer.class));
    }
    @Test void closedProjectEmptyScopeCrossTenantAndScopeChangeDenyDelivery(){
        var ref=create();when(contexts.inspect(any())).thenReturn(new ProjectAcceptanceContextApi.Context(99L,99L,0L,1L,"CLOSED"));
        assertThrows(BusinessContractException.class,()->owner.require("IT","declaredNote",ref.entityId(),"IT",true,false));
        doReturn(new ProjectScopeResult(99L,1L,Set.of(),Set.of())).when(runtime.projectApi).resolveCurrent(any());
        assertThrows(BusinessContractException.class,()->owner.require("IT","declaredNote",ref.entityId(),"IT",false,false));
        login(8,880001);assertThrows(RuntimeException.class,()->owner.require("IT","declaredNote",ref.entityId(),"IT",false,false));
        login(7,880001);doReturn(new ProjectScopeResult(99L,1L,Set.of(99L),Set.of())).when(runtime.projectApi).resolveCurrent(any());
        doReturn(new ProjectScopeResult(99L,2L,Set.of(99L),Set.of())).when(runtime.projectApi).lockAndRevalidate(any());
        assertThrows(BusinessContractException.class,()->transaction.execute(status->owner.require("IT","declaredNote",ref.entityId(),"IT",true,true)));
    }
    @Test void runtimeViewDirectoryIsGeneratedWithoutEntityProviderAndDoesNotAuthorizeAnObject(){
        var api=new PermissionApiImpl();ReflectionTestUtils.setField(api,"permissionService",runtime.context.getBean(cn.iocoder.yudao.module.system.service.permission.PermissionServiceImpl.class));
        var access=new BusinessViewAccess(api,new org.springframework.core.env.StandardEnvironment());
        var persistence=new BusinessEntityPersistenceRegistry(runtime.context.getBeanProvider(BusinessModelContributor.class));
        var factory=new DeclaredBusinessViewProviderFactory(persistence,runtime.dispatcher,access);var registry=new BusinessViewComponentRegistry(factory.providers());
        assertTrue(registry.components(new Context(7L,880001L)).isEmpty());
        runtime.jdbc.update("INSERT INTO system_menu(id,name,permission,type,sort,parent_id,status) VALUES(980009,'IT view','pms:business-view:query',3,9,0,0)");
        runtime.jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(701,980009,7)");
        var component=registry.components(new Context(7L,880001L)).getFirst();
        assertEquals("DECLARED_BUSINESS_IT_DECLARED_NOTE",component.componentKey());assertEquals("declaredNote",component.entityType());
        assertTrue(component.supportedActions().toString().contains("save"));
        var provider=factory.providers().getFirst();assertThrows(RuntimeException.class,()->provider.validateConfiguration(new Context(7L,880002L),null,ValidationMode.INSPECT));
        assertThrows(RuntimeException.class,()->provider.validateConfiguration(new Context(7L,880001L),77L,ValidationMode.INSPECT));
        var ref=create();doReturn(new ProjectScopeResult(99L,1L,Set.of(),Set.of())).when(runtime.projectApi).resolveCurrent(any());
        assertThrows(BusinessContractException.class,()->runtime.access.read(EntityDataRef.current(ref),new EntityActor(7L,880001L,"IT"),"detail"));
    }
}
