package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.service.outsource.business.OutsourceRequestBusinessService;
import cn.iocoder.yudao.module.pms.engineering.service.externalprocurement.business.ExternalProcurementBusinessService;
import cn.iocoder.yudao.module.pms.engineering.service.materialexchange.business.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeSerialDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeSerialMapper;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.product.AssetProductOfficialApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeLineFactApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineFact;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.ProjectBusinessScopeAccess;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated H2 transactions and native state/detail mappers; cross-module facts are test doubles. */
@SuppressWarnings({"rawtypes","unchecked"})
class ProcurementDefaultBusinessTest {
    final SiteSurveySpringPersistenceTest fixture=new SiteSurveySpringPersistenceTest();
    final Map<String,DefaultProjectBusinessService> services=new LinkedHashMap<>();
    final cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessDeletionGuard deletionGuard=mock(cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessDeletionGuard.class);
    @BeforeEach void open() throws Exception {
        assertFalse(SiteSurveySpringPersistenceTest.mysqlOptIn());fixture.open();var ctx=fixture.ctx;
        var callers=ctx.getBean(BusinessCallerContext.class);var scopes=ctx.getBean(ProjectScopeApi.class);
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(20L));
        ctx.getBeanFactory().registerSingleton("procurementDefaults",new BusinessDefaults(callers,new BusinessPermissions(ctx.getBean(PermissionApi.class),callers),new ProjectBusinessScopeAccess(scopes),ctx.getBean(jakarta.validation.Validator.class),new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class)),ctx.getBean(OperationExecutionStore.class),ctx.getBean(OperationAuditApi.class),ctx.getBean(BusinessEventPort.class),List.of(deletionGuard),()->mock(DefaultBusinessDeliveryApi.class)));
        var sf=ctx.getBean(SqlSessionFactory.class);var sessions=new SqlSessionTemplate(sf);
        String base="cn.iocoder.yudao.module.pms.engineering.";
        for(var item:List.of("outsource/OutsourceRequest","materialrequisition/MaterialRequisition","externalprocurement/ExternalProcurement","materialexchange/MaterialExchange")) {
            var parts=item.split("/");var prefix=parts[0]+".business."+parts[1]+"Business";
            Class model=Class.forName(base+"dal.dataobject."+prefix+"DO");SiteSurveySpringPersistenceTest.Config.schema(fixture.jdbc,model);
            Class mapper=Class.forName(base+"dal.mysql."+prefix+"Mapper");sf.getConfiguration().addMapper(mapper);ctx.getBeanFactory().registerSingleton(mapper.getSimpleName(),sessions.getMapper(mapper));
            String xml=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/mapper/"+item+"Mapper.xml")).replace("b'0'","0");
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(new java.io.StringReader(xml),sf.getConfiguration(),item,sf.getConfiguration().getSqlFragments()).parse();
            Class nativeMapper=Class.forName(base+"dal.mysql."+item.replace('/','.')+"Mapper");ctx.getBeanFactory().registerSingleton(nativeMapper.getSimpleName(),sessions.getMapper(nativeMapper));
        }
        SiteSurveySpringPersistenceTest.Config.schema(fixture.jdbc,MaterialExchangeSerialDO.class);sf.getConfiguration().addMapper(MaterialExchangeSerialMapper.class);ctx.getBeanFactory().registerSingleton("serials",sessions.getMapper(MaterialExchangeSerialMapper.class));
        registerMock(ProjectDeviceSelectionApi.class);registerMock(AssetProductOfficialApi.class);var lines=registerMock(DeliveryScopeLineFactApi.class);
        when(lines.validateSelection(eq(20L),any())).thenAnswer(call->((List<?>)call.getArgument(1)).isEmpty()?List.of():List.of(new DeliveryScopeLineFact(31L,32L,"ORDER-REAL","001","P-REAL","原设备","SWITCH","交换机",BigDecimal.TEN)));
        var attachments=registerMock(cn.iocoder.yudao.module.pms.engineering.service.attachment.NativeAttachmentRegistration.class);when(attachments.register(any(),any())).thenReturn(List.of());doCallRealMethod().when(attachments).requireLegacyUnchanged(any(),any());
        ctx.registerBean(MaterialExchangeBusinessDetails.class);
        ctx.registerBean(cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.business.SiteSurveyBusinessService.class);
        for(var item:List.of("outsource/OutsourceRequest","materialrequisition/MaterialRequisition","externalprocurement/ExternalProcurement","materialexchange/MaterialExchange")) {
            Class nativeService=Class.forName(base+"service."+item.replace('/','.')+"ServiceImpl");ctx.registerBean(nativeService);
            var parts=item.split("/");Class service=Class.forName(base+"service."+parts[0]+".business."+parts[1]+"BusinessService");ctx.registerBean(service);
            services.put(parts[1],(DefaultProjectBusinessService)ctx.getBean(service));
        }
    }
    @AfterEach void close(){fixture.close();}
    <T>T registerMock(Class<T> type){T value=mock(type,withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));fixture.ctx.getBeanFactory().registerSingleton(type.getSimpleName(),value);return value;}
    Map<String,Object> input(String kind){var value=new LinkedHashMap<String,Object>();value.put("projectId",20L);value.put("code",kind+"-1");value.put("name","现场申请");value.put("remark","原说明");
        if(kind.equals("OutsourceRequest"))value.put("workContent","现场服务");
        else if(kind.equals("MaterialExchange")){value.put("reason","物料不匹配");value.put("$business",Map.of("serials",List.of(Map.of("scopeDetailId",31,"quantity",2,"productName","FORGED","orderNo","FORGED"))));}
        else{value.put("productName","配件");value.put("quantity",2);}return value;}
    @Test void fourRootsUseInheritedConcurrencyAuthorizationAndReadonlyStatus(){
        for(var entry:services.entrySet()){
            var service=entry.getValue();var values=input(entry.getKey());var first=service.createForm(values,entry.getKey()+"-create");long id=first.entityRef().entityId();
            assertEquals(first,service.createForm(values,entry.getKey()+"-create"));assertThrows(BusinessContractException.class,()->service.input(Map.of("status",3)));
            var saved=service.updateForm(id,Map.of("remark","修改说明"),first.newConcurrencyBasis(),entry.getKey()+"-save");
            assertThrows(RuntimeException.class,()->service.updateForm(id,Map.of("code","OTHER"),saved.newConcurrencyBasis(),entry.getKey()+"-rename"));
            assertThrows(BusinessContractException.class,()->service.updateForm(id,Map.of("remark","stale"),first.newConcurrencyBasis(),entry.getKey()+"-stale"));
            when(fixture.ctx.getBean(PermissionApi.class).hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(false);
            assertThrows(BusinessContractException.class,()->service.delete(id,saved.newConcurrencyBasis(),entry.getKey()+"-denied"));
            when(fixture.ctx.getBean(PermissionApi.class).hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(true);
        }
    }
    @Test void nativeSubmissionAndDecisionKeepStateAndAuthenticatedActor(){
        var service=(ExternalProcurementBusinessService)services.get("ExternalProcurement");var created=service.createForm(input("ExternalProcurement"),"create");long id=created.entityRef().entityId();
        assertThrows(RuntimeException.class,()->service.approve(id,0L,"early","PASS","同意"));
        var submitted=service.submit(id,0L,"submit");assertEquals(1,service.get(id).getStatus());
        assertThrows(RuntimeException.class,()->service.updateForm(id,Map.of("remark","rewrite"),submitted.newConcurrencyBasis(),"locked"));
        var approved=service.approve(id,submitted.newConcurrencyBasis(),"approve","PASS","同意");assertEquals(3,service.get(id).getStatus());assertEquals(9L,service.get(id).getApproverUserId());
        assertEquals(approved,service.approve(id,submitted.newConcurrencyBasis(),"approve","PASS","同意"));
        assertThrows(RuntimeException.class,()->service.delete(id,approved.newConcurrencyBasis(),"delete-final"));
    }
    @Test void surveyAssociationIsAtomicAndKeepsTheSurveyDeletionGuard(){
        var source=fixture.content("source");source.setOutsourceRequired(true);
        var survey=fixture.commands.executeReceipt("create",null,null,source,null,"source",OperationEntryKind.INDEPENDENT);
        var service=(OutsourceRequestBusinessService)services.get("OutsourceRequest");var values=input("OutsourceRequest");values.put("triggerSource","SITE_SURVEY");values.put("triggerRefId",survey.entityRef().entityId());
        var created=service.createForm(values,"create");assertEquals(created.entityRef().entityId(),fixture.jdbc.queryForObject("SELECT outsource_request_id FROM sol_site_survey WHERE id=?",Long.class,survey.entityRef().entityId()));
        var second=new LinkedHashMap<>(values);second.put("code","SECOND");assertThrows(RuntimeException.class,()->service.createForm(second,"second"));assertEquals(1,fixture.count("res_outsource_request"));
        doThrow(new BusinessContractException("REFERENCE_IN_USE","linked material")).when(deletionGuard).requireDeletable(any(),any());
        assertThrows(BusinessContractException.class,()->service.deleteWithContext(created.entityRef().entityId(),created.newConcurrencyBasis(),"guarded-delete",null));
        assertEquals(created.entityRef().entityId(),fixture.jdbc.queryForObject("SELECT outsource_request_id FROM sol_site_survey WHERE id=?",Long.class,survey.entityRef().entityId()));
        reset(deletionGuard);service.deleteWithContext(created.entityRef().entityId(),created.newConcurrencyBasis(),"delete",null);
        assertNull(fixture.jdbc.queryForObject("SELECT outsource_request_id FROM sol_site_survey WHERE id=?",Long.class,survey.entityRef().entityId()));
    }
    @Test void surveySourceUsesTheDefaultExecutionIdentityAndRollsBackOnRejectedSourceAccess(){
        var source=fixture.content("default-source");source.setOutsourceRequired(true);
        var survey=fixture.commands.executeReceipt("create",null,null,source,null,"default-source",OperationEntryKind.INDEPENDENT);
        var execution=new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection(
                new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(20L,1L,30L,1,40L,1,50L,60L,1,1,70L,1,true,null),null);
        var guard=fixture.ctx.getBean(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.class);
        doAnswer(call->{
            var request=call.getArgument(0,cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest.class);
            if(request.selection()!=null && (!"SOL".equals(request.ownerContext()) || !"siteSurvey".equals(request.objectType())))
                throw new BusinessContractException("EXECUTION_VIEW_MISMATCH","The frozen source uses the default survey view");
            return null;
        }).when(guard).lockForWrite(any());
        var service=(OutsourceRequestBusinessService)services.get("OutsourceRequest");
        var values=input("OutsourceRequest");values.put("triggerSource","SITE_SURVEY");values.put("triggerRefId",survey.entityRef().entityId());values.put("$business",Map.of("siteSurveyExecution",execution));
        var permissions=fixture.ctx.getBean(PermissionApi.class);
        when(permissions.hasAnyPermissions(9L,"pms:sol-site-survey:update")).thenReturn(false);
        assertThrows(RuntimeException.class,()->service.createForm(values,"source-denied"));
        assertEquals(0,fixture.count("res_outsource_request"));
        when(permissions.hasAnyPermissions(9L,"pms:sol-site-survey:update")).thenReturn(true);
        var created=service.createForm(values,"source-create");
        verify(guard).lockForWrite(argThat(request->"siteSurvey".equals(request.objectType()) && execution.equals(request.selection())));
        doThrow(new BusinessContractException("EXECUTION_STALE","The source execution advanced")).when(guard).lockForWrite(argThat(request->request.selection()!=null));
        assertThrows(BusinessContractException.class,()->service.deleteWithContext(created.entityRef().entityId(),created.newConcurrencyBasis(),"source-stale-delete",execution));
        assertEquals(1,fixture.count("res_outsource_request"));
        assertEquals(created.entityRef().entityId(),fixture.jdbc.queryForObject("SELECT outsource_request_id FROM sol_site_survey WHERE id=?",Long.class,survey.entityRef().entityId()));
    }
    @Test void exchangeSnapshotsComeFromFactsAndUnchangedLinesAreNotRewritten(){
        var service=(MaterialExchangeBusinessService)services.get("MaterialExchange");var created=service.createForm(input("MaterialExchange"),"create");long id=created.entityRef().entityId();var row=service.get(id);
        assertEquals("INCOMPATIBLE",row.getExchangeType());assertThrows(BusinessContractException.class,()->service.input(Map.of("exchangeType","OTHER")));
        assertEquals(BigDecimal.valueOf(2),row.getQuantity());assertEquals("原设备",row.getSerials().getFirst().getProductName());assertEquals("ORDER-REAL",row.getSerials().getFirst().getOrderNo());assertEquals(9L,row.getApplicantUserId());
        long serial=fixture.jdbc.queryForObject("SELECT id FROM imp_eng_material_exchange_serial WHERE deleted=FALSE",Long.class);
        var saved=service.updateForm(id,Map.of("remark","保留快照"),created.newConcurrencyBasis(),"save");assertEquals(serial,fixture.jdbc.queryForObject("SELECT id FROM imp_eng_material_exchange_serial WHERE deleted=FALSE",Long.class));
        var cleared=service.updateForm(id,Map.of("$business",Map.of("serials",List.of())),saved.newConcurrencyBasis(),"clear");assertTrue(service.get(id).getSerials().isEmpty());assertEquals(BigDecimal.ZERO,service.get(id).getQuantity());
        assertThrows(BusinessContractException.class,()->service.updateForm(id,Map.of("$business",Map.of("serials",List.of(Map.of("scopeDetailId",31,"quantity",1)))),saved.newConcurrencyBasis(),"stale-lines"));
        assertEquals(cleared.newConcurrencyBasis(),service.get(id).getVersion());assertTrue(service.get(id).getSerials().isEmpty());
    }
    @Test void invalidScopeAndProductRollBackBothParentAndLines(){
        var service=(MaterialExchangeBusinessService)services.get("MaterialExchange");var invalid=input("MaterialExchange");invalid.put("$business",Map.of("serials",List.of(Map.of("scopeDetailId",31,"quantity",2,"productId",999))));
        assertThrows(RuntimeException.class,()->service.createForm(invalid,"inactive-product"));assertEquals(0,fixture.count("imp_eng_material_exchange"));assertEquals(0,fixture.count("imp_eng_material_exchange_serial"));
        doThrow(new BusinessContractException("ENTITY_SCOPE_DENIED","foreign scope")).when(fixture.ctx.getBean(DeliveryScopeLineFactApi.class)).validateSelection(eq(20L),any());
        assertThrows(RuntimeException.class,()->service.createForm(input("MaterialExchange"),"foreign-line"));assertEquals(0,fixture.count("imp_eng_material_exchange"));assertEquals(0,fixture.count("imp_eng_material_exchange_serial"));
    }
}
