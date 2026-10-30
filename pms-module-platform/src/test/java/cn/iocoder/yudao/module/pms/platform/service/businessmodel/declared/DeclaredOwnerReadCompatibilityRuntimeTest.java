package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.access.*;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.BusinessModelRegistry;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual SQL read isolation and production permission guard for explicit Owner catalog contracts. */
@EnabledIfSystemProperty(named="npdms.declared.exclusive",matches="true")
class DeclaredOwnerReadCompatibilityRuntimeTest {
    DeclaredBusinessRuntimePersistenceTest.Runtime runtime;
    @BeforeEach void start() throws Exception {
        runtime=new DeclaredBusinessRuntimePersistenceTest.Runtime();DeclaredBusinessRuntimePersistenceTest.initializeExclusiveSchema(runtime);login(7,880002);
        runtime.jdbc.update("INSERT INTO it_declared_note(id,tenant_id,project_ref,title,internal_memo,version) VALUES(980101,7,100,'tenant catalog','hidden',0),(980102,8,100,'foreign tenant','foreign hidden',0)");
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();TenantContextHolder.clear();runtime.close();}
    private void login(long tenant,long user){TenantContextHolder.setTenantId(tenant);var principal=new LoginUser();principal.setTenantId(tenant);principal.setId(user);principal.setUserType(2);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));}
    private EntityActor actor(){return new EntityActor(TenantContextHolder.getRequiredTenantId(),((LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId(),"owner-read");}
    @Test void explicitTenantCatalogKeepsNativePermissionTenantIsolationAndReadableProjection() {
        var source=runtime.declaration.descriptor();
        var model=new BusinessModelDescriptor(source.ownerModule(),source.entityType(),source.stableCode(),source.contractVersion(),source.kind(),source.title(),source.authorizationPolicyRef(),source.fields(),source.relations(),List.of(),List.of(),null,null);
        var declaration=new BusinessModelDeclaration(model,DeclaredNoteDO.class,runtime.declaration.mapper(),null);
        try(var context=new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("catalog",(BusinessModelContributor)()->List.of(declaration));context.refresh();
            var catalog=new BusinessModelRegistry(context.getBeanProvider(BusinessModelContributor.class));
            var persistence=new BusinessEntityPersistenceRegistry(context.getBeanProvider(BusinessModelContributor.class));
            var policy=new OwnerTenantReadScopePolicy(Set.of("IT/declaredNote"),catalog,persistence);
            var access=new DefaultBusinessEntityAccess(catalog,persistence,runtime.guard,null,List.of(policy));
            var target=EntityDataRef.current(new EntityRef(7L,"IT","declaredNote",980101L));
            var page=new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),10,null);
            var read=access.read(target,actor(),"detail");assertEquals("tenant catalog",read.fieldValues().get("title"));assertFalse(read.fieldValues().containsKey("internalMemo"));
            assertEquals(List.of(980101L),access.query(page,actor()).members().stream().map(row->row.ref().entityId()).toList());
            assertFalse(access.query(page,actor()).members().getFirst().fieldValues().containsKey("internalMemo"));
            assertThrows(RuntimeException.class,()->access.read(EntityDataRef.current(new EntityRef(7L,"IT","declaredNote",980102L)),actor(),"detail"));
            login(7,880003);assertEquals("ACCESS_DENIED",assertThrows(BusinessContractException.class,()->access.query(page,actor())).getErrorCode());
            login(8,880002);assertEquals(List.of(980102L),access.query(page,actor()).members().stream().map(row->row.ref().entityId()).toList());
            assertThrows(RuntimeException.class,()->access.read(target,actor(),"detail"));
            login(7,880002);
            assertEquals("SCOPE_POLICY_NOT_DECLARED",assertThrows(BusinessContractException.class,()->new DefaultBusinessEntityAccess(catalog,persistence,runtime.guard,null).query(page,actor())).getErrorCode());
            assertEquals(0,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record",Integer.class));
        }
    }
    @Test void tenantCompatibilityCannotOverrideNewDeclaredProjectScope() {
        var catalog=runtime.context.getBeanProvider(BusinessModelContributor.class);
        var models=new BusinessModelRegistry(catalog);var persistence=new BusinessEntityPersistenceRegistry(catalog);
        var compatibility=new OwnerTenantReadScopePolicy(Set.of("IT/declaredNote"),models,persistence);
        assertFalse(compatibility.supports("IT","declaredNote"));
        assertEquals("ENTITY_SCOPE_DENIED",assertThrows(BusinessContractException.class,()->runtime.access.read(EntityDataRef.current(new EntityRef(7L,"IT","declaredNote",980101L)),actor(),"detail")).getErrorCode());
    }
}
