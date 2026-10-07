package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@EnabledIfSystemProperty(named="npdms.declared.exclusive",matches="true")
class DeclaredOperationRecoveryRuntimePersistenceTest {
    private DeclaredBusinessRuntimePersistenceTest.Runtime runtime;
    @BeforeEach void start() throws Exception {runtime=new DeclaredBusinessRuntimePersistenceTest.Runtime();DeclaredBusinessRuntimePersistenceTest.initializeExclusiveSchema(runtime);login(7L,880001L);}
    @AfterEach void close(){SecurityContextHolder.clearContext();TenantContextHolder.clear();if(runtime!=null)runtime.close();}
    private void login(long tenant,long user){TenantContextHolder.setTenantId(tenant);var principal=new LoginUser();principal.setTenantId(tenant);principal.setId(user);principal.setUserType(2);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));}
    private BusinessOperationRequest create(String key){return new BusinessOperationRequest("create",1,null,"IT","declaredNote",Map.of("projectRef",99L,"title",key),key,null,OperationEntryKind.INDEPENDENT,null);}
    private BusinessOperationReceipt recover(String operation,String key){return new TransactionTemplate(new DataSourceTransactionManager(runtime.source)).execute(status->runtime.dispatcher.recoverReceipt("IT","declaredNote",operation,1,key));}
    private void reject(String code,Runnable action){assertEquals(code,assertThrows(BusinessContractException.class,action::run).getErrorCode());}
    private List<Long> counts(){return List.of("it_declared_note","plt_idempotency_record","plt_operation_audit","plt_outbox_event").stream().map(table->runtime.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class)).toList();}
    @Test void unknownOrCommittedIntentRecoveryNeverReservesOrRepeatsTheCommand() throws Exception {
        assertNull(recover("create","not-sent"));assertEquals(List.of(0L,0L,0L,0L),counts());
        var request=create("lost-response");var receipt=runtime.dispatcher.dispatch(request);
        assertEquals("create",receipt.operationCode());assertEquals(1,receipt.operationVersion());
        runtime.close();runtime=new DeclaredBusinessRuntimePersistenceTest.Runtime();
        assertEquals(receipt,recover("create","lost-response"));assertEquals(List.of(1L,1L,1L,1L),counts());
        assertEquals(receipt,runtime.dispatcher.dispatch(request));assertEquals(List.of(1L,1L,1L,1L),counts());
    }
    @Test void exactOriginalOperationPermissionAndActorTenantRemainRequired(){
        var original=runtime.dispatcher.dispatch(create("origin"));
        reject("IDEMPOTENCY_INTENT_MISMATCH",()->recover("save","origin"));
        login(7,880002);reject("ACCESS_DENIED",()->recover("create","origin"));
        login(8,880001);assertNull(recover("create","origin"));
        login(7,880001);runtime.jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id=980003");
        reject("ACCESS_DENIED",()->recover("create","origin"));assertEquals(List.of(1L,1L,1L,1L),counts());
    }
    @Test void recoveredOldReceiptUsesObjectsCurrentOwnership(){
        var original=runtime.dispatcher.dispatch(create("moved-origin"));
        runtime.dispatcher.dispatch(new BusinessOperationRequest("save",1,EntityDataRef.current(original.entityRef()),null,null,
                Map.of("projectRef",101L),"move",0L,OperationEntryKind.INDEPENDENT,null));
        doReturn(new ProjectScopeResult(99L,1L,Set.of(99L),Set.of())).when(runtime.projectApi).resolveCurrent(any());
        reject("ENTITY_SCOPE_DENIED",()->recover("create","moved-origin"));
        doReturn(new ProjectScopeResult(101L,1L,Set.of(101L),Set.of())).when(runtime.projectApi).resolveCurrent(any());
        assertEquals(original,recover("create","moved-origin"));assertEquals(0L,original.newConcurrencyBasis());
        assertEquals(List.of(1L,2L,2L,2L),counts());
    }
}
