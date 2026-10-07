package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
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

/** One new typed business inherits revisions without catalog registration, a custom provider or dispatcher. */
class DirectVersionedBusinessTest extends SiteSurveySpringPersistenceTest {
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_version_note")
    @ProjectBusinessModel(ownerModule="IT",entityType="versionNote",stableCode="IT_VERSION_NOTE",name="Version note",permissionPrefix="it:version-note")
    public static class Note extends BaseProjectBusinessEntity {
        @BusinessModelField(name="标题") @jakarta.validation.constraints.NotBlank private String title;
    }
    @Data @lombok.experimental.Accessors(chain=false) @EqualsAndHashCode(callSuper=true) @TableName("it_version_note_revision")
    public static class NoteRevision extends Note implements MutableEntityRevision {
        private Long entityId;private Integer revisionNo;private Long sourceRevisionId;private Long baseEffectiveRevisionId;
        private Integer baseEntityVersion;private String changeReason;private Long frozenBy;private java.time.LocalDateTime frozenAt;
        @TableField("revision_state") private String revisionStateText;private Boolean effective;
        public EntityRef entityRef(){return new EntityRef(getTenantId(),"IT","versionNote",entityId);}
        public EntityVersionProvider.Revision.State revisionState(){return EntityVersionProvider.Revision.State.valueOf(revisionStateText);}
        public void setRevisionState(EntityVersionProvider.Revision.State state){revisionStateText=state.name();}
        public boolean effective(){return Boolean.TRUE.equals(effective);}
        public void setEffective(boolean value){effective=value;}
    }
    interface NoteMapper extends BusinessMapper<Note>{}
    interface RevisionMapper extends BusinessRevisionMapper<NoteRevision>{}
    public static class NoteService extends DefaultVersionedProjectBusinessService<NoteMapper,Note,RevisionMapper,NoteRevision>{}
    @org.springframework.web.bind.annotation.RestController @org.springframework.web.bind.annotation.RequestMapping("/it/version-notes")
    public static class NoteController extends VersionedProjectBusinessController<NoteService,Note>{}
    NoteService notes;
    DefaultBusinessDeliveryApi versionDeliveries;
    @BeforeEach void installDirectBusiness(){
        jdbc.execute("DROP TABLE IF EXISTS it_version_note_revision");jdbc.execute("DROP TABLE IF EXISTS it_version_note");
        jdbc.execute("CREATE TABLE it_version_note(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,title VARCHAR(128),version BIGINT,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE it_version_note_revision(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,title VARCHAR(128),version BIGINT,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,entity_id BIGINT,revision_no INT CHECK(revision_no>0),source_revision_id BIGINT,base_effective_revision_id BIGINT,base_entity_version INT,change_reason VARCHAR(255),frozen_by BIGINT,frozen_at TIMESTAMP,revision_state VARCHAR(16),effective BOOLEAN,UNIQUE(tenant_id,entity_id,revision_no))");
        var sessions=new SqlSessionTemplate(ctx.getBean(org.apache.ibatis.session.SqlSessionFactory.class));var pagination=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();pagination.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor());sessions.getConfiguration().addInterceptor(pagination);sessions.getConfiguration().addMapper(NoteMapper.class);sessions.getConfiguration().addMapper(RevisionMapper.class);
        ctx.getBeanFactory().registerSingleton("noteMapper",sessions.getMapper(NoteMapper.class));ctx.getBeanFactory().registerSingleton("noteRevisionMapper",sessions.getMapper(RevisionMapper.class));
        var callers=ctx.getBean(BusinessCallerContext.class);var scopes=ctx.getBean(ProjectScopeApi.class);when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(20L));
        versionDeliveries=mock(DefaultBusinessDeliveryApi.class);
        ctx.getBeanFactory().registerSingleton("versionDefaults",new BusinessDefaults(callers,new BusinessPermissions(ctx.getBean(PermissionApi.class),callers),new ProjectBusinessScopeAccess(scopes),ctx.getBean(jakarta.validation.Validator.class),new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class)),ctx.getBean(OperationExecutionStore.class),ctx.getBean(OperationAuditApi.class),ctx.getBean(BusinessEventPort.class),List.of(mock(BusinessDeletionGuard.class)),()->versionDeliveries));
        ctx.registerBean(NoteService.class);ctx.registerBean(NoteController.class);notes=ctx.getBean(NoteService.class);
    }
    @Test void emptyServiceInheritsDraftSaveFreezeActivationAndReplay(){
        var created=notes.create(notes.input(Map.of("projectId",20,"title","Original")),"create-note");long id=created.entityRef().entityId();
        jdbc.update("UPDATE it_version_note SET updater='legacy',update_time='2000-01-01 00:00:00' WHERE id=?",id);
        var draftReceipt=notes.createRevision(id,0L,null,"Correction","create-revision");
        var draft=notes.revisions(id,null,20).getFirst();assertEquals("DRAFT",draft.state().name());assertEquals("Original",notes.get(id).getTitle());
        assertEquals(draftReceipt,notes.createRevision(id,0L,null,"Correction","create-revision"));
        notes.saveRevision(id,0L,draft.ref().revisionId(),0L,Map.of("title","Revised"),"save-revision");
        assertEquals("Original",notes.get(id).getTitle());assertEquals("Revised",notes.revisionValues(id,draft.ref().revisionId()).get("title").value());
        assertThrows(RuntimeException.class,()->notes.saveRevision(id,0L,draft.ref().revisionId(),0L,Map.of("title","Stale"),"stale-revision"));
        notes.completeRevision(id,0L,draft.ref().revisionId(),1L,"complete-revision");
        assertEquals("Revised",notes.get(id).getTitle());assertEquals(1L,notes.get(id).getVersion());
        assertEquals("9",notes.get(id).getUpdater());assertTrue(notes.get(id).getUpdateTime().isAfter(java.time.LocalDateTime.of(2020,1,1,0,0)));
        var frozen=notes.revisions(id,null,20).getFirst();assertTrue(frozen.effective());assertEquals("FROZEN",frozen.state().name());
        assertThrows(RuntimeException.class,()->notes.saveRevision(id,1L,frozen.ref().revisionId(),(long)frozen.version(),Map.of("title","Tamper"),"frozen-write"));
        assertEquals(0,NoteService.class.getDeclaredMethods().length);
    }
    @Test void discardPreservesHistoryAndNewNumbersNeverReuseDeletedRows(){
        var created=notes.create(notes.input(Map.of("projectId",20,"title","History")),"create-history");long id=created.entityRef().entityId();
        notes.createRevision(id,0L,null,null,"draft-one");var first=notes.revisions(id,null,20).getFirst();
        notes.discardRevision(id,0L,first.ref().revisionId(),0L,"discard-one");
        assertEquals(1,jdbc.queryForObject("SELECT revision_no FROM it_version_note_revision WHERE id=?",Integer.class,first.ref().revisionId()));
        assertTrue(jdbc.queryForObject("SELECT deleted FROM it_version_note_revision WHERE id=?",Boolean.class,first.ref().revisionId()));
        notes.createRevision(id,0L,null,null,"draft-two");assertEquals(2,notes.revisions(id,null,20).getFirst().revisionNo());
    }
    @Test void extensionSnapshotsCopyAndCompareWithoutPerEntityProviders(){
        var actor=new EntityActor(1L,9L,"version-test");var extension=ctx.getBean(EntityExtensionApi.class);
        var definition=extension.publishDefinition(1L,"IT","versionNote",List.of(new EntityExtensionApi.Definition("flag","Flag",EntityField.Type.BOOLEAN,false,null,List.of()),new EntityExtensionApi.Definition("memo","Memo",EntityField.Type.TEXT,false,100,List.of())),actor);
        var created=notes.create(notes.input(Map.of("projectId",20,"title","Extensions")),"new-extensions");long id=created.entityRef().entityId();
        notes.saveForm(id,Map.of("$extensions",Map.of("definitionRevisionId",definition.id(),"expectedVersion",0,"values",Map.of("flag",true,"memo","keep"))),0L,"seed-extensions");
        notes.createRevision(id,1L,null,null,"draft-extensions");var draft=notes.revisions(id,null,20).getFirst();var form=notes.revisionForm(id,draft.ref().revisionId());
        assertEquals(true,form.extensions().fields().get("flag"));
        notes.saveRevision(id,1L,draft.ref().revisionId(),0L,Map.of("$extensions",Map.of("definitionRevisionId",definition.id(),"expectedVersion",form.extensions().version(),"values",Map.of("flag",false))),"edit-extension");
        notes.completeRevision(id,1L,draft.ref().revisionId(),1L,"complete-extension");assertEquals(false,notes.form(id).extensions().fields().get("flag"));assertEquals("keep",notes.form(id).extensions().fields().get("memo"));
        assertThrows(RuntimeException.class,()->notes.update(id,notes.input(Map.of("title","Bypass")),Set.of("title"),2L,"direct-bypass"));
        assertThrows(RuntimeException.class,()->notes.delete(id,2L,"delete-history"));
        notes.createRevision(id,2L,draft.ref().revisionId(),"Copy","copy-extension");var copy=notes.revisions(id,null,20).getFirst();assertEquals(draft.ref().revisionId(),copy.sourceRevisionId());
        assertEquals("keep",notes.revisionForm(id,copy.ref().revisionId()).extensions().fields().get("memo"));assertTrue(notes.compareRevisions(id,draft.ref().revisionId(),copy.ref().revisionId()).isEmpty());
    }
    @Test void inheritedHttpAndTenantAndRevisionMembershipAreEnforced() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(ctx.getBean(NoteController.class)).build();
        var created=notes.create(notes.input(Map.of("projectId",20,"title","HTTP")),"http-note");long id=created.entityRef().entityId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/it/version-notes/"+id+"/revision-create").contentType("application/json").content("{\"version\":0,\"idempotencyKey\":\"http-revision\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.operationCode").value("revision-create"));
        var draft=notes.revisions(id,null,20).getFirst();var other=notes.create(notes.input(Map.of("projectId",20,"title","Other")),"other-note");
        assertThrows(RuntimeException.class,()->notes.revisionValues(other.entityRef().entityId(),draft.ref().revisionId()));
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(2L);
        try{assertThrows(RuntimeException.class,()->notes.revisions(id,null,20));}finally{cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);}
        assertEquals(0,NoteController.class.getDeclaredMethods().length);
    }
    @Test void changedBaselineRollsBackFreezeAndLeavesDraftEditable(){
        var created=notes.create(notes.input(Map.of("projectId",20,"title","Baseline")),"baseline-note");long id=created.entityRef().entityId();
        notes.createRevision(id,0L,null,null,"baseline-draft");var draft=notes.revisions(id,null,20).getFirst();
        jdbc.update("UPDATE it_version_note SET version=1,title='External' WHERE id=?",id);
        assertThrows(RuntimeException.class,()->notes.completeRevision(id,1L,draft.ref().revisionId(),0L,"baseline-complete"));
        assertEquals("DRAFT",notes.revisions(id,null,20).getFirst().state().name());assertEquals(0,notes.revisions(id,null,20).getFirst().version());assertEquals("External",notes.get(id).getTitle());
    }
    @Test void concurrentCreationSerializesOnTheCurrentBusinessRow() throws Exception {
        var created=notes.create(notes.input(Map.of("projectId",20,"title","Concurrent")),"concurrent-note");long id=created.entityRef().entityId();
        var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var tasks=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<2;i++){String key="concurrent-draft-"+i;tasks.add(pool.submit(()->{
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);login();
                try{start.await();notes.createRevision(id,0L,null,null,key);return true;}
                catch(cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException conflict){assertEquals("REVISION_DRAFT_EXISTS",conflict.getErrorCode());return false;}
                finally{cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
            }));}
            start.countDown();int successful=0;for(var task:tasks)if(task.get(30,java.util.concurrent.TimeUnit.SECONDS))successful++;
            assertEquals(1,successful);assertEquals(1,notes.revisions(id,null,20).size());
        }finally{pool.shutdownNow();}
    }
    @Test void revisionWritesRequireTheInheritedUpdatePermission(){
        var created=notes.create(notes.input(Map.of("projectId",20,"title","Readonly")),"readonly-note");long id=created.entityRef().entityId();
        when(ctx.getBean(PermissionApi.class).hasAnyPermissions(9L,"it:version-note:update")).thenReturn(false);
        assertThrows(RuntimeException.class,()->notes.createRevision(id,0L,null,null,"denied-draft"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM it_version_note_revision",Integer.class));
        assertEquals("Readonly",notes.get(id).getTitle());
    }
}
