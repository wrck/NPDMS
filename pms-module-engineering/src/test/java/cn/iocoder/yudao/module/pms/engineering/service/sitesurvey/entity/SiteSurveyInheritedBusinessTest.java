package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.business.SiteSurveyBusinessService;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.business.SiteSurveyBusinessController;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessDeletionGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.ProjectBusinessScopeAccess;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Shared inherited CRUD on the existing physical survey and child tables, alongside unchanged old entry tests. */
class SiteSurveyInheritedBusinessTest extends SiteSurveySpringPersistenceTest {
    SiteSurveyBusinessService business;
    DefaultBusinessDeliveryApi delivery;
    BusinessDeletionGuard protection;
    @BeforeEach void inherited() {
        var pagination=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));
        ctx.getBean(org.apache.ibatis.session.SqlSessionFactory.class).getConfiguration().addInterceptor(pagination);
        var callers=ctx.getBean(BusinessCallerContext.class);var scopes=ctx.getBean(ProjectScopeApi.class);
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(20L));
        delivery=mock(DefaultBusinessDeliveryApi.class);protection=mock(BusinessDeletionGuard.class);
        var defaults=new BusinessDefaults(callers,new BusinessPermissions(ctx.getBean(PermissionApi.class),callers),
                new ProjectBusinessScopeAccess(scopes),ctx.getBean(jakarta.validation.Validator.class),
                new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class)),ctx.getBean(OperationExecutionStore.class),
                ctx.getBean(OperationAuditApi.class),ctx.getBean(BusinessEventPort.class),List.of(protection),()->delivery);
        ctx.getBeanFactory().registerSingleton("inheritedDefaults",defaults);
        ctx.registerBean(SiteSurveyBusinessService.class);ctx.registerBean(SiteSurveyBusinessController.class);
        business=ctx.getBean(SiteSurveyBusinessService.class);
    }
    Map<String,Object> values(String name){return Map.of("projectId",20,"name",name,"location","机房", "powerTypes",List.of("AC"),"selectedMaterials",List.of(Map.of("projectId",20,"sn","SN-DIRECT","reason","原有子表")));}
    @Test void inheritedCrudPersistsExistingMainAndChildTablesAndReadsLegacyRows() {
        var legacy=create();assertEquals("survey",business.get(legacy.entityRef().entityId()).getName());
        var created=business.create(business.input(values("direct")),"direct-create");long id=created.entityRef().entityId();
        assertEquals("sol_site_survey",business.get(id).getClass().getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
        assertEquals(List.of("AC"),business.get(id).getPowerTypes());assertEquals("SN-DIRECT",business.get(id).getSelectedMaterials().getFirst().getSn());
        var patch=Map.<String,Object>of("name","updated","powerTypes",List.of("DC"));
        var saved=business.update(id,business.input(patch),patch.keySet(),created.newConcurrencyBasis(),"direct-save");
        assertEquals("updated",business.get(id).getName());assertEquals(List.of("DC"),business.get(id).getPowerTypes());
        assertEquals("SN-DIRECT",business.get(id).getSelectedMaterials().getFirst().getSn());
        assertEquals(2,business.page(new BusinessPageQuery()).getTotal());
        assertEquals(saved,business.update(id,business.input(patch),patch.keySet(),created.newConcurrencyBasis(),"direct-save"));
        assertThrows(BusinessContractException.class,()->business.update(id,business.input(patch),patch.keySet(),created.newConcurrencyBasis(),"stale"));
    }
    @Test void nativeStateAndScopeRemainProtectedAndDeliveriesAreInherited() {
        var created=business.create(business.input(values("state")),"state-create");long id=created.entityRef().entityId();
        assertThrows(BusinessContractException.class,()->business.input(Map.of("status",3)));
        assertThrows(RuntimeException.class,()->business.update(id,business.input(Map.of("projectId",21)),Set.of("projectId"),0L,"move"));
        var file=new DefaultBusinessDeliveryApi.UploadFile("survey.txt","text/plain",1L,()->new java.io.ByteArrayInputStream(new byte[]{1}));
        business.uploadDelivery(id,"ATTACHMENT",file,"upload");
        verify(delivery).upload(new DefaultBusinessDeliveryApi.Scope(20L,"SOL_SITE_SURVEY",Long.toString(id),"ATTACHMENT"),file,"upload");
        var confirmed=business.confirm(id,created.newConcurrencyBasis(),"confirm");assertEquals(1,business.get(id).getStatus());
        assertThrows(RuntimeException.class,()->business.update(id,business.input(Map.of("name","forbidden")),Set.of("name"),confirmed.newConcurrencyBasis(),"after-confirm"));
        assertThrows(RuntimeException.class,()->business.requireDeliveryAccess(id,true,false));
        business.archive(id,confirmed.newConcurrencyBasis(),"archive");assertEquals(3,business.get(id).getStatus());
        assertEquals(created,business.create(business.input(values("state")),"state-create"));
    }
    @Test void nativeReferenceAliasesAlsoProtectInheritedDelete() {
        var created=business.create(business.input(values("protected")),"protected-create");long id=created.entityRef().entityId();
        doThrow(new BusinessContractException("DELETE_REFERENCED_ENTITY","native history")).when(protection).requireDeletable(
                argThat(ref->ref.entityType().equals("SITE_SURVEY")),any());
        assertThrows(BusinessContractException.class,()->business.delete(id,created.newConcurrencyBasis(),"delete"));
        assertEquals("protected",business.get(id).getName());
        verify(protection).requireDeletable(argThat(ref->ref.entityType().equals("siteSurvey")),any());
    }
}
