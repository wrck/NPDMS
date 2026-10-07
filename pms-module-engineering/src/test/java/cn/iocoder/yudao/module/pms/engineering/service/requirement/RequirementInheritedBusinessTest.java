package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.service.requirement.business.RequirementRevisionBusinessService;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.business.RequirementRevisionBusinessMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessDeletionGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.ProjectBusinessScopeAccess;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Existing current/revision SQL and existing completion/copy policy with inherited normal CRUD. */
class RequirementInheritedBusinessTest extends RequirementAnalysisSpringPersistenceTest {
    RequirementRevisionBusinessService business;
    DefaultBusinessDeliveryApi delivery;
    @BeforeEach void inherited() throws Exception {
        if(mysqlOptIn()) {
            try(var connection=ctx.getBean(javax.sql.DataSource.class).getConnection()){
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("../sql/migrations/V399__requirement_revision_active_identity_uniqueness.sql"));
            }
        } else {
            jdbc.execute("DROP INDEX draft_key");
            jdbc.execute("ALTER TABLE sol_requirement_analysis_revision ADD active_draft_marker INT GENERATED ALWAYS AS (CASE WHEN deleted=FALSE THEN draft_marker ELSE NULL END)");
            jdbc.execute("ALTER TABLE sol_requirement_analysis_revision ADD active_revision_no INT GENERATED ALWAYS AS (CASE WHEN deleted=FALSE THEN revision_no ELSE NULL END)");
            jdbc.execute("CREATE UNIQUE INDEX draft_key ON sol_requirement_analysis_revision(tenant_id,project_id,active_draft_marker)");
            jdbc.execute("CREATE UNIQUE INDEX revision_key ON sol_requirement_analysis_revision(tenant_id,project_id,active_revision_no)");
        }
        var sessions=new SqlSessionTemplate(ctx.getBean(org.apache.ibatis.session.SqlSessionFactory.class));
        var pagination=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();pagination.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor());sessions.getConfiguration().addInterceptor(pagination);
        sessions.getConfiguration().addMapper(RequirementRevisionBusinessMapper.class);
        ctx.getBeanFactory().registerSingleton("directRevisionMapper",sessions.getMapper(RequirementRevisionBusinessMapper.class));
        var callers=ctx.getBean(BusinessCallerContext.class);var scopes=ctx.getBean(ProjectScopeApi.class);when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(20L));
        delivery=mock(DefaultBusinessDeliveryApi.class);
        var defaults=new BusinessDefaults(callers,new BusinessPermissions(ctx.getBean(PermissionApi.class),callers),new ProjectBusinessScopeAccess(scopes),
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator(),new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class)),ctx.getBean(OperationExecutionStore.class),
                ctx.getBean(OperationAuditApi.class),ctx.getBean(BusinessEventPort.class),List.of(mock(BusinessDeletionGuard.class)),()->delivery);
        ctx.getBeanFactory().registerSingleton("revisionDefaults",defaults);ctx.registerBean(RequirementRevisionBusinessService.class);business=ctx.getBean(RequirementRevisionBusinessService.class);
    }
    @Test void inheritedDraftSaveCompletionAndCopyRetainCurrentAndFrozenSemantics() {
        var draft=business.create(business.input(Map.of("projectId",20)),"direct-create");Long id=draft.entityRef().entityId();
        assertTrue(id>9007199254740991L);assertEquals(0,count("sol_requirement_analysis"));assertEquals(1L,draft.newConcurrencyBasis());
        assertThrows(RuntimeException.class,()->business.input(Map.of("revisionState","FROZEN")));
        var fields=required();fields.put("businessDeviceDetails",List.of(Map.of("deviceName","Device","serialNumber","SN-RA")));
        var saved=business.update(id,business.input(fields),fields.keySet(),1L,"direct-save");
        assertEquals("SN-RA",business.get(id).getBusinessDeviceDetails().getFirst().getSerialNumber());
        var completed=business.complete(id,saved.newConcurrencyBasis(),"direct-complete");
        assertEquals("FROZEN",business.get(id).getRevisionState());assertEquals(1,count("sol_requirement_analysis"));
        assertEquals(1,business.get(id).getEffectiveMarker());
        assertThrows(RuntimeException.class,()->business.update(id,business.input(Map.of("projectBackground","overwrite")),Set.of("projectBackground"),completed.newConcurrencyBasis(),"frozen-save"));
        assertThrows(RuntimeException.class,()->business.delete(id,completed.newConcurrencyBasis(),"frozen-delete"));
        assertEquals(completed,business.complete(id,saved.newConcurrencyBasis(),"direct-complete"));
        var copied=business.copyRevision(id,completed.newConcurrencyBasis(),"direct-copy","follow-up");
        var next=business.get(copied.entityRef().entityId());assertEquals("DRAFT",next.getRevisionState());assertEquals(id,next.getSourceRevisionId());
        assertEquals(business.get(id).getEntityId(),next.getEntityId());assertEquals("SN-RA",next.getBusinessDeviceDetails().getFirst().getSerialNumber());
    }
    @Test void discardReleasesActiveSlotsWithoutRewritingRevisionHistory() {
        var first=business.create(business.input(Map.of("projectId",20)),"first");long id=first.entityRef().entityId();
        business.delete(id,1L,"discard");
        assertEquals(1,jdbc.queryForObject("SELECT revision_no FROM sol_requirement_analysis_revision WHERE id=?",Integer.class,id));
        assertTrue(jdbc.queryForObject("SELECT deleted FROM sol_requirement_analysis_revision WHERE id=?",Boolean.class,id));
        var next=business.create(business.input(Map.of("projectId",20)),"replacement");assertNotEquals(id,next.entityRef().entityId());
        assertEquals(2,count("sol_requirement_analysis_revision"));
    }
    @Test void privateDraftIsNotListedToAnOrdinaryProjectReader() {
        var draft=business.create(business.input(Map.of("projectId",20)),"private");
        when(ctx.getBean(ProjectParticipantFactApi.class).inspect(any())).thenReturn(null);
        assertEquals(0,business.page(new BusinessPageQuery()).getTotal());
        assertThrows(RuntimeException.class,()->business.get(draft.entityRef().entityId()));
        assertThrows(RuntimeException.class,()->business.receipt("create","private"));
    }
    @Test void existingLegacyDraftIsVisibleThroughTheNewIdentityWithoutChangingItsLogicalKey() {
        var legacy=create();var row=business.get(legacy.ref().revisionId());
        assertEquals(legacy.ref().entity().entityId(),row.getEntityId());assertEquals(legacy.ref().revisionId(),row.getId());
        assertEquals("SOL_REQUIREMENT_ANALYSIS_REVISION",business.definition().stableCode());
        assertEquals(row.getId().toString(),business.deliveryScope(row.getId(),"ATTACHMENT").businessEntityKey());
    }
    @Test void inheritedFormUsesLogicalRevisionExtensionIdentityAndRollsBackInvalidInput() {
        var draft=business.create(business.input(Map.of("projectId",20)),"form-create");long id=draft.entityRef().entityId();
        var input=Map.<String,Object>of("projectBackground","body","$extensions",Map.of("definitionRevisionId",definition,"expectedVersion",0,"values",Map.of("CUSTOM_FLAG",true)));
        var saved=business.saveForm(id,input,draft.newConcurrencyBasis(),"form-save");
        assertEquals(true,business.form(id).extensions().fields().get("CUSTOM_FLAG"));
        assertEquals(business.get(id).getEntityId(),jdbc.queryForObject("SELECT entity_id FROM plt_entity_extension_value",Long.class));
        assertEquals(id,jdbc.queryForObject("SELECT revision_id FROM plt_entity_extension_value",Long.class));
        var invalid=Map.<String,Object>of("projectBackground","must rollback","$extensions",Map.of("definitionRevisionId",definition,"expectedVersion",business.form(id).extensions().version(),"values",Map.of("CUSTOM_FLAG","invalid")));
        assertThrows(RuntimeException.class,()->business.saveForm(id,invalid,saved.newConcurrencyBasis(),"bad-form"));
        assertEquals("body",business.get(id).getProjectBackground());assertEquals(true,business.form(id).extensions().fields().get("CUSTOM_FLAG"));
    }
}
