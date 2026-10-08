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
        delivery=mock(DefaultBusinessDeliveryApi.class,withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));protection=mock(BusinessDeletionGuard.class,withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
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
    @Test void inheritedCreateFormStoresExtensionsAtomicallyAndReplaysTheCompleteIntent() {
        var input=new LinkedHashMap<String,Object>(values("form-create"));
        input.put("$extensions",Map.of("definitionRevisionId",definition,"expectedVersion",0,"values",Map.of("extra_flag",false)));
        var receipt=business.createForm(input,"create-with-form");
        assertEquals(false,business.form(receipt.entityRef().entityId()).extensions().fields().get("extra_flag"));
        assertEquals(receipt,business.createForm(input,"create-with-form"));assertEquals(1,count("sol_site_survey"));
        input.put("$extensions",Map.of("definitionRevisionId",definition,"expectedVersion",0,"values",Map.of("extra_flag",true)));
        assertThrows(BusinessContractException.class,()->business.createForm(input,"create-with-form"));
        assertEquals(false,business.form(receipt.entityRef().entityId()).extensions().fields().get("extra_flag"));
    }
    @Test void firstExtensionWriteUsesTheExistingFormBindingWithoutAValueRow() {
        var created=business.create(business.input(values("bound-empty-extension")),"bound-empty-create");
        long id=created.entityRef().entityId();
        var forms=ctx.getBean(cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi.class);
        when(forms.read(any(),any())).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi.Binding(99L,definition,Map.of("extra_flag","extra_flag"),1));
        assertNull(business.form(id).extensions().definitionRevisionId());
        var patch=Map.<String,Object>of("$extensions",Map.of("expectedVersion",0,"values",Map.of("extra_flag",false)));
        var saved=business.saveForm(id,patch,created.newConcurrencyBasis(),"first-bound-extension");
        assertEquals(definition,business.form(id).extensions().definitionRevisionId());
        assertEquals(false,business.form(id).extensions().fields().get("extra_flag"));
        assertEquals(saved,business.saveForm(id,patch,created.newConcurrencyBasis(),"first-bound-extension"));
        var invalid=Map.<String,Object>of("name","must rollback","$extensions",Map.of("expectedVersion",1,"values",Map.of("extra_flag","invalid boolean")));
        assertThrows(RuntimeException.class,()->business.saveForm(id,invalid,saved.newConcurrencyBasis(),"invalid-bound-extension"));
        assertEquals("bound-empty-extension",business.get(id).getName());
        assertEquals(false,business.form(id).extensions().fields().get("extra_flag"));
    }
    @Test void failedInitialFormBindingRollsBackTheInsertedBusinessAndChildren() {
        var input=new LinkedHashMap<String,Object>(values("bad-binding"));
        input.put("$binding",Map.of("expectedVersion",0,"formRevisionId",99L,"fieldBindings",Map.of("name","name"),"bindRemainingFields",true));
        doThrow(new BusinessContractException("FORM_REFUSED","refused")).when(ctx.getBean(cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi.class)).bind(any());
        assertThrows(BusinessContractException.class,()->business.createForm(input,"bad-initial-binding"));
        assertEquals(0,count("sol_site_survey"));assertEquals(0,count("sol_site_survey_condition"));assertEquals(0,count("sol_site_survey_material"));
    }
    @Test void captureDeadlineIsAnExplicitProjectCommandNotAnOpenBodyField() {
        var input=new LinkedHashMap<String,Object>(values("deadline-form"));
        input.put("$business",Map.of("requiredEndDate","2026-11-30","projectVersion",2));
        var receipt=business.createForm(input,"deadline-form-create");
        assertEquals(java.time.LocalDate.of(2026,11,30),business.get(receipt.entityRef().entityId()).getRequiredEndDate());
        verify(ctx.getBean(cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateApi.class)).updateFromSurvey(argThat(command->command.projectId().equals(20L)&&command.expectedProjectVersion().equals(2L)));
        assertThrows(BusinessContractException.class,()->business.input(Map.of("requiredEndDate","2026-12-01")));
        input.put("$business",Map.of("projectId",21L));
        assertThrows(BusinessContractException.class,()->business.createForm(input,"forged-form-command"));assertEquals(1,count("sol_site_survey"));
    }
    @Test void defaultCaptureFormUsesTheCurrentPublishedSchemaAndNativeFieldBindings() {
        var field=new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor("extra_cabinetReady","radio",false,false,"boolean",null,null,null,List.of());
        var schema=new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact(1L,
                new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey("SOL","SITE_SURVEY"),993109090006L,992209220346L,2,1,"SITE_SURVEY",
                cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction.REVISION_FROZEN_USE,"FORM_CREATE_ELEMENT_PLUS","3","3","{}","[]",List.of(field),null);
        when(ctx.getBean(cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi.class).inspectCurrentRevisionForUsage(any())).thenReturn(schema);
        var form=business.formDefaults(20L);
        assertEquals(992209220346L,form.layout().binding().formRevisionId());assertEquals(2,form.layout().revisionNo());
        assertEquals("cabinetReady",form.layout().binding().fieldBindings().get("extra_cabinetReady"));
        assertEquals(0,count("sol_site_survey"));
    }
    @Test void presentationStatusIsReadOnlyAndInternalOutsourceReferenceStaysPrivate() {
        assertFalse(business.definition().fields().stream().anyMatch(field->field.code().equals("outsourceRequestId")));
        assertThrows(BusinessContractException.class,()->business.input(Map.of("outsourceRequestId",99L)));
        for(String code:List.of("locationResolutionStatus")) {
            var field=business.definition().fields().stream().filter(value->value.code().equals(code)).findFirst().orElseThrow();
            assertTrue(field.readable());assertFalse(field.writable());
            assertThrows(BusinessContractException.class,()->business.input(Map.of(code,code.equals("outsourceRequestId")?99L:"RESOLVED")));
        }
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
        assertEquals(20L,business.requireDeliveryAccess(id,true,false));
        business.archive(id,confirmed.newConcurrencyBasis(),"archive");assertEquals(3,business.get(id).getStatus());
        assertThrows(RuntimeException.class,()->business.requireDeliveryAccess(id,true,false));
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
    @Test void inheritedFormSaveKeepsNativeExtensionsAndChildrenAtomic() {
        var legacy=create();long id=legacy.entityRef().entityId();var form=business.form(id);
        assertEquals(false,form.extensions().fields().get("extra_flag"));
        var patch=Map.<String,Object>of("name","with extension","$extensions",Map.of("definitionRevisionId",definition,"expectedVersion",form.extensions().version(),"values",Map.of("extra_flag",true)));
        var saved=business.saveForm(id,patch,legacy.newConcurrencyBasis(),"form-save");
        assertEquals(true,business.form(id).extensions().fields().get("extra_flag"));assertEquals("with extension",business.get(id).getName());
        assertEquals("SN-1",business.get(id).getSelectedMaterials().getFirst().getSn());
        assertEquals(saved,business.saveForm(id,patch,legacy.newConcurrencyBasis(),"form-save"));
        var invalid=Map.<String,Object>of("name","must rollback","$extensions",Map.of("definitionRevisionId",definition,"expectedVersion",business.form(id).extensions().version(),"values",Map.of("extra_flag","invalid boolean")));
        assertThrows(RuntimeException.class,()->business.saveForm(id,invalid,saved.newConcurrencyBasis(),"bad-form"));
        assertEquals("with extension",business.get(id).getName());assertEquals(true,business.form(id).extensions().fields().get("extra_flag"));
        var frozen=business.confirm(id,saved.newConcurrencyBasis(),"form-confirm");
        assertThrows(RuntimeException.class,()->business.saveForm(id,Map.of("name","frozen"),frozen.newConcurrencyBasis(),"frozen-form"));
    }
    @Test void inheritedLocationAndDeadlineKeepDomainPortsAndStateGuards() {
        var created=business.create(business.input(values("location")),"location-create");long id=created.entityRef().entityId();
        var locations=ctx.getBean(cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService.class);
        when(locations.maintain(any(),any(),any(),any(),any(),any())).thenReturn(new cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService.LocationFact(10L,2L,11L,3L,12L,4L,"RESOLVED","{}","{}"));
        var command=new cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand(999L,null,null,null,"ignored","ignored","ignored","ignored",List.of());
        var located=business.maintainLocation(id,0L,"location-change","新机房",command);
        assertEquals(10L,business.get(id).getAddressId());assertEquals("新机房",business.get(id).getLocation());
        verify(locations).maintain(eq(20L),eq("SITE_SURVEY"),eq(id),eq(0L),eq("新机房"),eq(command));
        assertThrows(RuntimeException.class,()->business.update(id,business.input(Map.of("location","raw")),Set.of("location"),located.newConcurrencyBasis(),"raw-location"));
        var date=java.time.LocalDate.of(2027,1,15);var deadline=business.updateDeadline(id,located.newConcurrencyBasis(),"deadline-change",8L,date);
        verify(ctx.getBean(cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateApi.class)).updateFromSurvey(argThat(c->c.projectId().equals(20L)&&c.expectedProjectVersion().equals(8L)&&c.endDate().equals(date)));
        assertEquals(date,business.get(id).getRequiredEndDate());
        var confirmed=business.confirm(id,deadline.newConcurrencyBasis(),"loc-confirm");
        assertThrows(RuntimeException.class,()->business.updateDeadline(id,confirmed.newConcurrencyBasis(),"locked-deadline",8L,date));
    }
}
