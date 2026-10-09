package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.ProjectBusinessScopeAccess;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SQL/transactions; no project runtime, business adapters or special collection save service. */
class DefaultBusinessAggregateTest extends SiteSurveySpringPersistenceTest {
    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
    }
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_aggregate")
    @ProjectBusinessModel(ownerModule="IT",entityType="aggregate",stableCode="IT_AGGREGATE",name="Aggregate",permissionPrefix="it:aggregate",
            completionCondition="{\"kind\":\"ALL\",\"children\":[{\"kind\":\"FIELD\",\"fieldCode\":\"title\",\"operator\":\"EQ\",\"values\":[\"done\"]},{\"kind\":\"DELIVERY\",\"fieldCode\":\"REPORT\",\"operator\":\"EQ\",\"values\":[true]}]}")
    public static class Aggregate extends BaseProjectBusinessEntity {
        @BusinessModelField @jakarta.validation.constraints.NotBlank private String title;
        @BusinessModelField @TableField(exist=false) @BusinessOwnedDetails(mapper=LineMapper.class) private List<Line> lines;
    }
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_aggregate_line")
    public static class Line extends OwnedBusinessDetailEntity {
        @BusinessModelField @jakarta.validation.constraints.NotBlank private String description;
        @BusinessModelField private Boolean enabled;
        @BusinessModelField private java.math.BigDecimal quantity;
    }
    interface AggregateMapper extends BusinessMapper<Aggregate>{}
    interface LineMapper extends BusinessDetailMapper<Line>{}
    public static class AggregateService extends DefaultProjectBusinessService<AggregateMapper,Aggregate>{}
    public static class HookService extends AggregateService {
        int calls;
        @Override protected void afterSave(Aggregate before,Aggregate row,String operation){
            calls++;
            if(!form(row.getId()).extensions().fields().containsKey("flag") || get(row.getId()).getLines().isEmpty())throw new IllegalStateException("Aggregate not ready");
            if("fail".equals(row.getTitle()))throw new IllegalStateException("post-save domain failure");
        }
    }
    public static class OverrideService extends AggregateService {
        @Override @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
        public cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt createForm(Map<String,Object> values,String key){
            var saved=super.createForm(values,key);
            if("fail-super".equals(values.get("title")))throw new IllegalStateException("after super failed");
            return saved;
        }
    }
    @Data @lombok.experimental.Accessors(chain=false) @EqualsAndHashCode(callSuper=true) @TableName("it_aggregate_revision")
    public static class AggregateRevision extends Aggregate implements MutableEntityRevision {
        private Long entityId;private Integer revisionNo;private Long sourceRevisionId;private Long baseEffectiveRevisionId;
        private Integer baseEntityVersion;private String changeReason;private Long frozenBy;private java.time.LocalDateTime frozenAt;
        @TableField("revision_state") private String revisionStateText;private Boolean effective;
        public EntityRef entityRef(){return new EntityRef(getTenantId(),"IT","aggregate",entityId);}
        public EntityVersionProvider.Revision.State revisionState(){return EntityVersionProvider.Revision.State.valueOf(revisionStateText);}
        public void setRevisionState(EntityVersionProvider.Revision.State state){revisionStateText=state.name();}
        public boolean effective(){return Boolean.TRUE.equals(effective);}
        public void setEffective(boolean value){effective=value;}
    }
    interface RevisionMapper extends BusinessRevisionMapper<AggregateRevision>{}
    public static class VersionedAggregateService extends DefaultVersionedProjectBusinessService<AggregateMapper,Aggregate,RevisionMapper,AggregateRevision>{
        boolean failActivation;
        @Override protected void beforeSave(Aggregate before,Aggregate proposed,String operation){
            if(failActivation && "revision-complete".equals(operation))throw new IllegalStateException("activation domain failure");
        }
    }
    AggregateService aggregates;
    @BeforeEach void install(){
        jdbc.execute("CREATE TABLE it_aggregate(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,title VARCHAR(128),version BIGINT,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE it_aggregate_line(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,aggregate_type VARCHAR(255),aggregate_id BIGINT,aggregate_revision_id BIGINT,detail_order INT,description VARCHAR(128),enabled BOOLEAN,quantity DECIMAL(20,4),version BIGINT,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE)");
        var sessions=new SqlSessionTemplate(ctx.getBean(org.apache.ibatis.session.SqlSessionFactory.class));
        sessions.getConfiguration().addMapper(AggregateMapper.class);sessions.getConfiguration().addMapper(LineMapper.class);
        ctx.getBeanFactory().registerSingleton("aggregateMapper",sessions.getMapper(AggregateMapper.class));ctx.getBeanFactory().registerSingleton("lineMapper",sessions.getMapper(LineMapper.class));
        var callers=ctx.getBean(BusinessCallerContext.class);var scopes=ctx.getBean(ProjectScopeApi.class);
        ctx.getBeanFactory().registerSingleton("aggregateDefaults",new BusinessDefaults(callers,new BusinessPermissions(ctx.getBean(PermissionApi.class),callers),new ProjectBusinessScopeAccess(scopes),ctx.getBean(jakarta.validation.Validator.class),new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class)),ctx.getBean(OperationExecutionStore.class),ctx.getBean(OperationAuditApi.class),ctx.getBean(BusinessEventPort.class),List.of(mock(BusinessDeletionGuard.class)),()->mock(DefaultBusinessDeliveryApi.class)));
        ctx.registerBean(AggregateService.class);aggregates=ctx.getBean(AggregateService.class);
    }
    private Map<String,Object> input(String title){return Map.of("projectId",20,"title",title,"lines",List.of(Map.of("description","one","enabled",false,"quantity",0)));}

    @Test void thinAggregatePersistsReadsUpdatesAndRemovesOwnedRowsWithoutPrivateFields(){
        var created=aggregates.createForm(input("body"),"aggregate-create");long id=created.entityRef().entityId();
        var first=aggregates.get(id);assertEquals(false,first.getLines().getFirst().getEnabled());assertEquals(0,first.getLines().getFirst().getQuantity().signum());
        var values=aggregates.readableValues(first);assertFalse(values.toString().contains("aggregateId"));
        var line=new LinkedHashMap<>(((List<Map<String,Object>>)values.get("lines")).getFirst());line.put("description","two");
        var saved=aggregates.updateForm(id,Map.of("title","changed","lines",List.of(line)),0L,"aggregate-save");
        assertEquals("two",aggregates.get(id).getLines().getFirst().getDescription());
        assertEquals(saved,aggregates.updateForm(id,Map.of("title","changed","lines",List.of(line)),0L,"aggregate-save"));
        assertThrows(RuntimeException.class,()->aggregates.updateForm(id,Map.of("lines",List.of(line)),saved.newConcurrencyBasis(),"stale-child"));
        aggregates.updateForm(id,Map.of("lines",List.of()),saved.newConcurrencyBasis(),"remove-child");
        assertTrue(aggregates.get(id).getLines().isEmpty());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM it_aggregate_line WHERE deleted=TRUE",Integer.class));
    }
    @Test void parentRollbackRejectsForgedOwnershipAndInvalidChild(){
        var a=aggregates.createForm(input("a"),"a");var b=aggregates.createForm(input("b"),"b");
        var foreign=((List<?>)aggregates.readableValues(aggregates.get(b.entityRef().entityId())).get("lines"));
        assertThrows(RuntimeException.class,()->aggregates.updateForm(a.entityRef().entityId(),Map.of("title","forged","lines",foreign),0L,"foreign"));
        assertEquals("a",aggregates.get(a.entityRef().entityId()).getTitle());
        assertThrows(RuntimeException.class,()->aggregates.createForm(Map.of("projectId",20,"title","bad","lines",List.of(Map.of("description","","aggregateId",99))),"forged-parent"));
        assertThrows(RuntimeException.class,()->aggregates.createForm(Map.of("projectId",20,"title","bad","lines",List.of(Map.of("description",""))),"invalid-child"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM it_aggregate",Integer.class));
    }
    @Test void completeAggregatePostHookRunsOnceAndFailureRollsBackBodyDetailsExtensionsAndReceipt(){
        ctx.removeBeanDefinition(ctx.getBeanNamesForType(AggregateService.class)[0]);
        ctx.registerBean(HookService.class);var service=ctx.getBean(HookService.class);
        var definition=ctx.getBean(EntityExtensionApi.class).publishDefinition(1L,"IT","aggregate",List.of(new EntityExtensionApi.Definition("flag","Flag",EntityField.Type.BOOLEAN,false,null,List.of())),new EntityActor(1L,9L,null));
        var values=new LinkedHashMap<>(input("success"));values.put("$extensions",Map.of("definitionRevisionId",definition.id(),"expectedVersion",0,"values",Map.of("flag",false)));
        var result=service.createForm(values,"hook-save");assertEquals(1,service.calls);assertEquals(result,service.createForm(values,"hook-save"));assertEquals(1,service.calls);
        var before=counts();values.put("title","fail");assertThrows(IllegalStateException.class,()->service.createForm(values,"hook-fail"));assertEquals(before,counts());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM it_aggregate",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM it_aggregate_line",Integer.class));
        assertNull(service.receipt("create","hook-fail"));
    }
    @Test void overrideCallingSuperSharesTheOuterTransaction(){
        ctx.removeBeanDefinition(ctx.getBeanNamesForType(AggregateService.class)[0]);ctx.registerBean(OverrideService.class);
        var service=ctx.getBean(OverrideService.class);var before=counts();
        assertThrows(IllegalStateException.class,()->service.createForm(input("fail-super"),"outer-rollback"));
        assertEquals(before,counts());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM it_aggregate",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM it_aggregate_line",Integer.class));assertNull(service.receipt("create","outer-rollback"));
    }
    @Test void conditionsReadStoredBodyOwnedRowsExtensionsAndSharedDeliveryWithoutAnEntityProvider(){
        var owners=mock(cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessOwners.class);
        when(owners.byIdentity("IT","aggregate")).thenReturn(Optional.of(aggregates));
        var materials=mock(cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper.class);
        var files=mock(cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.class);
        var evidence=new cn.iocoder.yudao.module.pms.platform.service.business.DefaultBusinessEvidenceService(owners,
                ctx.getBean(cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper.class),ctx.getBean(cn.iocoder.yudao.module.pms.platform.service.entity.EntityProviderRegistry.class),materials,files,ctx.getBean(PlatformTransactionManager.class));
        ctx.getBeanFactory().registerSingleton("aggregateConditionEvidence",evidence);
        when(materials.selectDefaultDeliveryList(any())).thenReturn(List.of());
        var definition=ctx.getBean(EntityExtensionApi.class).publishDefinition(1L,"IT","aggregate",List.of(new EntityExtensionApi.Definition("flag","Flag",EntityField.Type.BOOLEAN,false,null,List.of())),new EntityActor(1L,9L,null));
        var values=new LinkedHashMap<>(input("done"));values.put("$extensions",Map.of("definitionRevisionId",definition.id(),"expectedVersion",0,"values",Map.of("flag",false)));
        var result=aggregates.createForm(values,"condition-create");long id=result.entityRef().entityId();
        var condition=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject("{\"kind\":\"ALL\",\"children\":[{\"kind\":\"FIELD\",\"fieldCode\":\"flag\",\"operator\":\"EQ\",\"values\":[false]},{\"kind\":\"COUNT\",\"fieldCode\":\"lines\",\"operator\":\"EQ\",\"values\":[1],\"children\":[{\"kind\":\"FIELD\",\"fieldCode\":\"quantity\",\"operator\":\"EQ\",\"values\":[0]}]}]}",cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.BusinessCondition.class);
        assertTrue(aggregates.evaluate(id,condition).verdict().satisfied());
        var query=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Query(1L,20L,new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Type("IT","aggregate"),id);
        var missing=aggregates.runtimeObservation(query,false);assertFalse(missing.handlingCompleted());
        var material=new cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO();
        material.setId(101L);material.setTenantId(1L);material.setProjectId(20L);material.setEntityId(id);material.setBusinessTypeCode("IT_AGGREGATE");material.setOwnerModule("IT");material.setEntityType("aggregate");material.setTypeCode("REPORT");material.setFileReferenceId(201L);material.setFileArtifactId(301L);material.setFileVersionNo(1);material.setFileSha256("sha");material.setVersion(0L);material.setStatus("ACTIVE");material.setDeleted(false);
        when(materials.selectDefaultDeliveryList(any())).thenReturn(List.of(material));
        when(files.inspectDocument(1L,201L)).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.Document(201L,"PLT","DEFAULT_BUSINESS_DELIVERY","20:IT:aggregate:"+id,"REPORT","reference",301L,1,"sha","file",true));
        var ready=aggregates.runtimeObservation(query,false);assertTrue(ready.handlingCompleted());assertNotEquals(missing.factVersion(),ready.factVersion());
        material.setDeleted(true);assertFalse(aggregates.runtimeObservation(query,false).handlingCompleted());
        when(ctx.getBean(PermissionApi.class).hasAnyPermissions(9L,"it:aggregate:query")).thenReturn(false);
        assertThrows(RuntimeException.class,()->aggregates.evaluate(id,condition));
        assertThrows(RuntimeException.class,()->evidence.inspect(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.BusinessConditionEvidenceApi.Query(result.entityRef(),21L,Set.of("flag"),Set.of(),false)));
    }
    @Test void ownedDetailsCopyToRevisionsAndFrozenRowsRemainImmutable(){
        jdbc.execute("CREATE TABLE it_aggregate_revision(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,title VARCHAR(128),version BIGINT,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,entity_id BIGINT,revision_no INT,source_revision_id BIGINT,base_effective_revision_id BIGINT,base_entity_version INT,change_reason VARCHAR(255),frozen_by BIGINT,frozen_at TIMESTAMP,revision_state VARCHAR(16),effective BOOLEAN)");
        var sessions=new SqlSessionTemplate(ctx.getBean(org.apache.ibatis.session.SqlSessionFactory.class));sessions.getConfiguration().addMapper(RevisionMapper.class);ctx.getBeanFactory().registerSingleton("aggregateRevisionMapper",sessions.getMapper(RevisionMapper.class));
        ctx.removeBeanDefinition(ctx.getBeanNamesForType(AggregateService.class)[0]);ctx.registerBean(VersionedAggregateService.class);var service=ctx.getBean(VersionedAggregateService.class);
        var created=service.createForm(input("versioned"),"versioned-aggregate");long id=created.entityRef().entityId();
        Long currentLine=service.get(id).getLines().getFirst().getId();service.createRevision(id,0L,null,null,"aggregate-draft");var draft=service.revisions(id,null,20).getFirst();
        var lines=(List<Map<String,Object>>)service.revisionValues(id,draft.ref().revisionId()).get("lines").value();
        assertNotEquals(currentLine,lines.getFirst().get("id"));var edited=new LinkedHashMap<>(lines.getFirst());edited.put("description","revision detail");
        service.saveRevision(id,0L,draft.ref().revisionId(),0L,Map.of("lines",List.of(edited)),"save-aggregate-draft");
        assertEquals("one",service.get(id).getLines().getFirst().getDescription());
        service.failActivation=true;
        assertThrows(IllegalStateException.class,()->service.completeRevision(id,0L,draft.ref().revisionId(),1L,"reject-aggregate-activation"));
        assertEquals(EntityVersionProvider.Revision.State.DRAFT,service.revisions(id,null,20).getFirst().state());
        assertEquals(0L,service.get(id).getVersion());
        service.failActivation=false;
        service.completeRevision(id,0L,draft.ref().revisionId(),1L,"activate-aggregate-draft");assertEquals("revision detail",service.get(id).getLines().getFirst().getDescription());
        var frozen=service.revisions(id,null,20).getFirst();
        assertThrows(RuntimeException.class,()->service.saveRevision(id,1L,frozen.ref().revisionId(),(long)frozen.version(),Map.of("lines",List.of()),"tamper-frozen"));
        service.createRevision(id,1L,frozen.ref().revisionId(),null,"copy-aggregate-history");var copy=service.revisions(id,null,20).getFirst();
        service.saveRevision(id,1L,copy.ref().revisionId(),0L,Map.of("lines",List.of()),"remove-draft-details");
        assertEquals(1,((List<?>)service.revisionValues(id,frozen.ref().revisionId()).get("lines").value()).size());assertEquals(1,service.get(id).getLines().size());
    }
}
