package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.business.SiteSurveyBusinessService;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.business.SiteSurveyBusinessController;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalAttemptDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.BusinessDeletionProtectionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper;
import cn.iocoder.yudao.module.pms.platform.service.business.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeclaredBusinessDeliveryBridge;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.platform.controller.admin.business.ProjectBusinessDeliveryController;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultRecordingApi;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production survey/default-material/file chain; project/auth/form-layout/storage providers are fixture ports. */
@EnabledIfSystemProperty(named="native.delivery.mysql",matches="true")
class SurveyInheritedDeliveryMySqlTest extends NativeAttachmentDeliveryMySqlTest {
    @Override List<Class<?>> extraMapperTypes(){return List.of(SiteSurveyEntityMapper.class,SiteSurveyDetailMapper.class,BusinessDeletionProtectionMapper.class,EntityCapabilityMapper.class);}
    @Override List<Class<?>> extraSchemaTypes(){return List.of(SiteSurveyEntityDO.class,SiteSurveyConditionDO.class,SiteSurveyMaterialDO.class,ApprovalAttemptDO.class,EntityExtensionDefinitionDO.class,EntityExtensionValueDO.class,EntityFormBindingDO.class);}
    @Override List<String> extraMapperPaths(){return List.of("sitesurvey/entity/*.xml","entity/EntityCapabilityMapper.xml");}
    @Override void registerExtraBeans(SqlSessionTemplate sessions) {
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(20L));
        var pagination=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();pagination.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor());
        sessions.getConfiguration().addInterceptor(pagination);
        var catalog=mock(BusinessModelCatalog.class);when(catalog.all()).thenReturn(List.of());
        context.getBeanFactory().registerSingleton("directCatalog",catalog);
        context.getBeanFactory().registerSingleton("legacyDeliveryBridge",mock(DeclaredBusinessDeliveryBridge.class));
        context.registerBean(BusinessAccessGuard.class,()->new PermissionBusinessAccessGuard(permissions));
        context.registerBean(ProjectBusinessExecutionApi.class,()->mock(ProjectBusinessExecutionApi.class));
        context.registerBean(ProjectBusinessResultRecordingApi.class,()->mock(ProjectBusinessResultRecordingApi.class));
        context.registerBean(DynamicFormBusinessInstanceApi.class,()->mock(DynamicFormBusinessInstanceApi.class));
        context.registerBean(EntityFormApi.class,()->mock(EntityFormApi.class));
        context.registerBean(EntityExtensionApi.class,()->{
            var extensions=mock(EntityExtensionApi.class);when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(null,Map.of(),0));return extensions;
        });
        context.registerBean(jakarta.validation.Validator.class,()->jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
        context.register(PlatformOperationExecutionStore.class,OutboxBusinessEventPort.class,PlatformBusinessDeletionGuard.class,ProjectBusinessConfiguration.class,
                DirectBusinessOwners.class,DefaultBusinessDeliveryService.class,DefaultBusinessDeliveryFilePolicy.class,ProjectBusinessDeliveryController.class,
                SiteSurveyBusinessService.class,SiteSurveyBusinessController.class,SiteSurveyEntityWriteAccess.class,SiteSurveyDetails.class,SiteSurveyEntityFormService.class,EngineeringRuleReevaluationEvents.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessScopeAccess.class,()->new ProjectBusinessScopeAccess(scopes));
        when(context.getBeanFactory().getBean(EngineeringRecordCodeGenerator.class).next(anyLong(),anyString(),any())).thenReturn("SV-TEST");
    }
    @Test void inheritedSurveyUsesOneMaterialTableAndStateProtectedRealSql() throws Exception {
        var service=context.getBean(SiteSurveyBusinessService.class);
        var mvc=MockMvcBuilders.standaloneSetup(context.getBean(SiteSurveyBusinessController.class),context.getBean(ProjectBusinessDeliveryController.class)).build();
        var created=service.create(service.input(Map.of("projectId",20,"name","MySQL工勘","location","机房","powerTypes",List.of("AC"))),"survey-create");
        Long id=created.entityRef().entityId();assertTrue(id>9007199254740991L);
        mvc.perform(multipart("/api/v1/pms/site-survey-business/"+id+"/deliverables")
                .file(new org.springframework.mock.web.MockMultipartFile("file","survey.txt","text/plain","survey\n".getBytes()))
                .param("projectId","20").param("businessType","SOL_SITE_SURVEY").param("businessEntityKey",id.toString()).param("deliverableType","ATTACHMENT")
                .header("Idempotency-Key","survey-upload")).andExpect(status().isOk()).andExpect(jsonPath("$.data.businessType").value("SOL_SITE_SURVEY"));
        assertTrue(service.deliveryCompletion(id,"ATTACHMENT").completed());
        mvc.perform(get("/api/v1/pms/business-deliverables").param("projectId","20")).andExpect(jsonPath("$.data.total").value(1));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plt_delivery_material WHERE entity_id=? AND business_type_code='SOL_SITE_SURVEY'",Integer.class,id));
        assertThrows(RuntimeException.class,()->service.delete(id,0L,"with-file"));
        service.confirm(id,0L,"confirm");assertEquals(1,service.get(id).getStatus());
        assertThrows(RuntimeException.class,()->service.requireDeliveryAccess(id,true,false));
        assertTrue(service.deliveryCompletion(id,"ATTACHMENT").completed());
        verify(context.getBean(DeclaredBusinessDeliveryBridge.class),never()).requireDefault(any(),any(),any(),any(),any(),anyBoolean(),anyBoolean(),any());
    }
}
