package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.business;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.business.AcceptanceBusinessDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.business.AcceptanceBusinessMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Test-owned H2: real default root and native report/version/attachment SQL; external ports are doubles. */
@SuppressWarnings({"rawtypes","unchecked"})
class AcceptanceBusinessServiceTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    AcceptanceBusinessService service;
    ProjectAcceptanceContextApi projects;
    FileArtifactApi files;
    cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    @BeforeEach void open() throws Exception {
        TenantContextHolder.setTenantId(1L);context=new AnnotationConfigApplicationContext();
        var ds=new DriverManagerDataSource("jdbc:h2:mem:acceptance_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);
        for(var type:List.of(AcceptanceBusinessDO.class,AcceptanceReportVersionDO.class,AcceptanceReportAttachmentDO.class))schema(type);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        var interceptors=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();interceptors.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());interceptors.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));config.addInterceptor(interceptors);factory.setConfiguration(config);
        var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());factory.setGlobalConfig(global);
        var resources=new ArrayList<ByteArrayResource>();for(String name:List.of("AcceptanceActivityMapper","AcceptanceReportVersionMapper")){var xml=Files.readString(Path.of("src/main/resources/mapper/acceptancereport/"+name+".xml")).replace("b'0'","0");resources.add(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8),name));}
        factory.setMapperLocations(resources.toArray(ByteArrayResource[]::new));var sessions=new SqlSessionTemplate(factory.getObject());
        for(var type:List.of(AcceptanceBusinessMapper.class,AcceptanceActivityMapper.class,AcceptanceReportVersionMapper.class,AcceptanceReportAttachmentMapper.class)){if(!sessions.getConfiguration().hasMapper(type))sessions.getConfiguration().addMapper(type);context.getBeanFactory().registerSingleton(type.getSimpleName(),sessions.getMapper(type));}
        var scope=registerMock(BusinessScopeAccess.class);when(scope.policyRef()).thenReturn("project");when(scope.readableScopeIds(any())).thenReturn(Set.of(7L));
        var journal=registerMock(OperationExecutionStore.class);when(journal.reserve(any(),any())).thenReturn(true);
        // Normal authenticated HTTP callers do not supply an entry correlation id.
        context.getBeanFactory().registerSingleton("defaults",new BusinessDefaults(()->new AbstractBusinessApplicationService.ResolvedCaller(1L,3L,null),mock(BusinessAccessGuard.class),scope,jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator(),new TransactionTemplate(new DataSourceTransactionManager(ds)),journal,mock(OperationAuditApi.class),mock(BusinessEventPort.class),List.of(),()->mock(DefaultBusinessDeliveryApi.class)));
        var ext=registerMock(cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.class);when(ext.read(any(),any())).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.Values(null,Map.of(),0));registerMock(cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi.class);
        projects=registerMock(ProjectAcceptanceContextApi.class);when(projects.inspect(any())).thenReturn(new ProjectAcceptanceContextApi.Context(7L,7L,2L,4L,"ACTIVE"));when(projects.lock(any(),any(),any())).thenAnswer(call->{if(!Objects.equals(call.getArgument(1),2L)||!Objects.equals(call.getArgument(2),4L))throw new IllegalStateException("PROJECT_VERSION_CONFLICT");return projects.inspect(call.getArgument(0));});
        permissions=registerMock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
        var projectScope=registerMock(ProjectScopeApi.class);when(projectScope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,4L,Set.of(7L),Set.of()));
        files=registerMock(FileArtifactApi.class);registerMock(FileEvidenceApi.class);registerMock(PlatformDeliveryMaterialApi.class);registerMock(PlatformDeliveryRequirementApi.class);
        var commands=registerMock(PlatformCommandExecutionApi.class);when(commands.execute(any(),any(),any(),any(),any())).thenAnswer(call->{Object result=call.<Supplier>getArgument(3).get();call.<Function>getArgument(4).apply(result);return new PlatformCommandExecutionApi.ExecutionResult(PlatformCommandExecutionApi.Decision.NEW,result);});
        context.registerBean(AcceptanceReportCommandService.class);context.registerBean(AcceptanceReportQueryService.class);context.registerBean(AcceptanceReportBusinessResultSource.class);context.registerBean(AcceptanceBusinessService.class);context.refresh();service=context.getBean(AcceptanceBusinessService.class);
    }
    @AfterEach void close(){if(jdbc!=null)jdbc.execute("SHUTDOWN");if(context!=null)context.close();TenantContextHolder.clear();}
    private <T>T registerMock(Class<T> type){var value=mock(type);context.getBeanFactory().registerSingleton(type.getSimpleName(),value);return value;}
    private Map<String,Object> input(String type){return Map.of("projectId",7L,"acceptanceType",type,"$business",Map.of("expectedProjectVersion",2L,"expectedTreeVersion",4L));}
    private long create(){return service.createForm(input("PRELIMINARY"),"activity-create").entityRef().entityId();}
    private AcceptanceReportVersionDO draft(long id){service.createReport(id,service.get(id).getVersion(),UUID.randomUUID().toString());return context.getBean(AcceptanceReportVersionMapper.class).selectByAcceptanceId(id).getFirst();}
    private FileReferenceSetFact fileSet(long report){return new FileReferenceSetFact(new FileReferenceSetKey("ACC","ACCEPTANCE_REPORT_VERSION",String.valueOf(report),"ACCEPTANCE_REPORT_ATTACHMENT"),4L,List.of(new FileArtifactVersionFact(77L,1,"report-file","ACCEPTANCE_REPORT_ATTACHMENT","report.pdf",1L,"application/pdf","a".repeat(64),"AVAILABLE","ACTIVE",new FileFactVersion(1,1,1),4L)));}
    private void available(long report){var set=fileSet(report);when(files.inspectReferenceSets(any())).thenReturn(List.of(set));when(files.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));}
    @Test void nativeReportUploadRemainsExplicitlyAuthorizedWhenSharedAttachmentsAreDisabled(){
        var caller=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext(1L,3L,7L,service.runtimeDefinition().type());
        when(permissions.hasAnyPermissions(3L,"pms:acceptance:report:write")).thenReturn(true);
        when(permissions.hasAnyPermissions(3L,"pms:file:upload")).thenReturn(true);
        assertFalse(service.model().features().attachments());
        assertTrue(service.runtimeActions(caller).contains("FILE_WRITE"));
        when(permissions.hasAnyPermissions(3L,"pms:file:upload")).thenReturn(false);
        assertFalse(service.runtimeActions(caller).contains("FILE_WRITE"));
        when(permissions.hasAnyPermissions(3L,"pms:file:upload")).thenReturn(true);
        when(permissions.hasAnyPermissions(3L,"pms:acceptance:report:write")).thenReturn(false);
        assertFalse(service.runtimeActions(caller).contains("FILE_WRITE"));
        when(permissions.hasAnyPermissions(3L,"pms:acceptance:report:write")).thenReturn(true);
        when(projects.inspect(any())).thenThrow(new IllegalStateException("PROJECT_EDIT_DENIED"));
        assertFalse(service.runtimeActions(caller).contains("FILE_WRITE"));
    }
    @Test void ordinaryCreateLocksQualificationAndPreservesActivityIdentity(){
        long id=create();var row=service.get(id);assertEquals("DIRECT",row.getOriginKind());assertEquals("PENDING",row.getActivityStatus());assertNotNull(row.getOriginSnapshot());
        assertThrows(RuntimeException.class,()->service.createForm(input("PRELIMINARY"),"duplicate"));
        assertThrows(RuntimeException.class,()->service.updateForm(id,Map.of("acceptanceType","FINAL"),row.getVersion(),"change-type"));
        assertThrows(RuntimeException.class,()->service.input(Map.of("activityStatus","COMPLETED")));
        assertThrows(RuntimeException.class,()->service.delete(id,row.getVersion(),"delete-history"));
        assertEquals("PRELIMINARY",service.get(id).getAcceptanceType());
    }
    @Test void noAttachmentAndForeignReportCannotPublishOrAdvanceRoot(){
        long id=create();var draft=draft(id);long version=service.get(id).getVersion();
        when(files.inspectReferenceSets(any())).thenReturn(List.of());
        assertThrows(RuntimeException.class,()->service.publishReport(id,version,"empty",draft.getId(),draft.getReportVersionNo(),null));
        long other=service.createForm(input("FINAL"),"final-create").entityRef().entityId();var foreign=draft(other);
        assertThrows(RuntimeException.class,()->service.draftFiles(id,foreign.getId()));
        assertThrows(RuntimeException.class,()->service.publishReport(id,version,"foreign",foreign.getId(),foreign.getReportVersionNo(),null));
        assertEquals(version,service.get(id).getVersion());assertNull(service.get(id).getCurrentReportVersionId());
        assertEquals("DRAFT",context.getBean(AcceptanceReportVersionMapper.class).selectById(draft.getId()).getReportStatus());
    }
    @Test void publishReplaceAndRevokeRetainFrozenReportAndAttachmentHistory(){
        long id=create();var first=draft(id);available(first.getId());
        var published=service.publishReport(id,service.get(id).getVersion(),"publish-first",first.getId(),first.getReportVersionNo(),null);
        assertEquals("COMPLETED",service.get(id).getActivityStatus());assertEquals(first.getId(),service.get(id).getCurrentReportVersionId());
        var second=draft(id);available(second.getId());
        assertThrows(RuntimeException.class,()->service.publishReport(id,published.newConcurrencyBasis(),"stale",second.getId(),second.getReportVersionNo(),first.getId()));
        var replacement=service.publishReport(id,service.get(id).getVersion(),"publish-second",second.getId(),second.getReportVersionNo(),first.getId());
        var reports=context.getBean(AcceptanceReportVersionMapper.class);assertEquals("SUPERSEDED",reports.selectById(first.getId()).getReportStatus());assertNotNull(reports.selectById(first.getId()).getEffectiveTo());
        assertThrows(RuntimeException.class,()->service.saveReport(id,replacement.newConcurrencyBasis(),"edit-published",first.getId(),first.getReportVersionNo()));
        service.revokeReport(id,replacement.newConcurrencyBasis(),"revoke",second.getId(),second.getReportVersionNo());
        assertEquals("PENDING",service.get(id).getActivityStatus());assertNull(service.get(id).getCurrentReportVersionId());
        assertEquals("REVOKED",reports.selectById(second.getId()).getReportStatus());assertEquals("SUPERSEDED",reports.selectById(first.getId()).getReportStatus());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM acc_acceptance_report_attachment",Integer.class));
    }
    @Test void staleProjectAndLegacySourceFailWithoutChangingExistingHistory(){
        assertThrows(RuntimeException.class,()->service.createForm(Map.of("projectId",7L,"acceptanceType","FINAL","$business",Map.of("expectedProjectVersion",1L,"expectedTreeVersion",4L)),"stale-project"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM acc_acceptance",Integer.class));
        long id=create();jdbc.update("UPDATE acc_acceptance SET origin_kind='LEGACY_TASK',project_task_id=99 WHERE id=?",id);
        assertThrows(RuntimeException.class,()->service.createReport(id,0L,"legacy-create"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM acc_acceptance_report_version",Integer.class));
    }
    @Test void templateFactsKeepAcceptanceTypeConclusionAndCurrentFileEvidence(){
        long id=create();var report=draft(id);available(report.getId());
        service.publishReport(id,service.get(id).getVersion(),"facts-publish",report.getId(),report.getReportVersionNo(),null);
        var evidence=context.getBean(FileEvidenceApi.class);
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true,null,1,1,1));
        var tx=context.getBean(BusinessDefaults.class).transactions();
        var query=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Query(1L,7L,service.runtimeDefinition().type(),id);
        var first=tx.execute(status->service.runtimeObservation(query,true));
        assertEquals(true,first.facts().get("PRELIMINARY_ACCEPTANCE_PASSED"));
        assertEquals(false,first.facts().get("FINAL_ACCEPTANCE_PASSED"));
        jdbc.update("UPDATE acc_acceptance_report_version SET conclusion_code='FAIL' WHERE id=?",report.getId());
        assertEquals(false,tx.execute(status->service.runtimeObservation(query,true)).facts().get("PRELIMINARY_ACCEPTANCE_PASSED"));
        jdbc.update("UPDATE acc_acceptance_report_version SET conclusion_code='PASS' WHERE id=?",report.getId());
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false,"FILE_REVOKED",1,2,1));
        var missing=tx.execute(status->service.runtimeObservation(query,true));
        assertEquals(false,missing.facts().get("PRELIMINARY_ACCEPTANCE_PASSED"));assertFalse(missing.handlingCompleted());
        assertNotEquals(first.factVersion(),missing.factVersion());
    }
    @Test void frozenAcceptanceTypeFiltersBeforeCandidatePageLimitAndNeverWidensInvalidScope(){
        long initial=create();long last=service.createForm(input("FINAL"),"final-filter").entityRef().entityId();
        assertTrue(initial<last);
        var type=service.runtimeDefinition().type();
        String parameters="{\"businessViewRevisionId\":\"123\",\"contextMapping\":{\"projectId\":\"projectId\"},\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\",\"acceptanceType\":\"FINAL\"}";
        var selected=service.runtimeCandidates(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Candidates(1L,7L,type,null,1,parameters));
        assertEquals(List.of(last),selected.stream().map(value->value.entityId()).toList());
        assertTrue(service.runtimeCandidates(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Candidates(1L,7L,type,last,1,parameters)).isEmpty());
        assertTrue(service.runtimeCandidates(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Candidates(1L,8L,type,null,1,parameters)).isEmpty());
        assertThrows(RuntimeException.class,()->service.runtimeCandidates(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Candidates(2L,7L,type,null,1,parameters)));
        assertThrows(RuntimeException.class,()->service.runtimeCandidates(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Candidates(1L,7L,type,null,1,"{\"acceptanceTypo\":\"FINAL\"}")));
        assertThrows(RuntimeException.class,()->service.runtimeCandidates(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Candidates(1L,7L,type,null,1,"[]")));
    }
    @Test void manualInspectionAndLockedLinkValidationRejectTheOtherAcceptanceType(){
        long initial=create();long last=service.createForm(input("FINAL"),"final-inspect").entityRef().entityId();
        var scope=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext(1L,3L,7L,
                service.runtimeDefinition().type(),"{\"acceptanceType\":\"FINAL\",\"configurationName\":\"final-report\"}");
        assertThrows(RuntimeException.class,()->service.runtimeForUser(scope,initial,false,null));
        var selected=service.runtimeForUser(scope,last,false,null);
        assertEquals(last,selected.entityId());
        assertThrows(RuntimeException.class,()->service.runtimeInspectForUser(scope,List.of(last,initial)));
        var batch=service.runtimeInspectForUser(scope,List.of(last));
        assertEquals(List.of(selected),batch.objects());assertEquals(service.runtimeActions(scope),batch.allowedActions());
        var tx=context.getBean(BusinessDefaults.class).transactions();
        assertThrows(RuntimeException.class,()->tx.execute(status->service.runtimeForUser(scope,initial,true,selected.factVersion())));
        assertEquals(selected.factVersion(),tx.execute(status->service.runtimeForUser(scope,last,true,selected.factVersion())).factVersion());
    }
    private void schema(Class<?> type){
        var columns=new LinkedHashMap<String,String>();for(Class<?> current=type;current!=Object.class;current=current.getSuperclass())for(Field field:current.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers())||field.isSynthetic())continue;var a=field.getAnnotation(TableField.class);if(a!=null&&!a.exist())continue;
            String name=a!=null&&!a.value().isBlank()?a.value():field.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);var value=field.getType();String sql=value==Long.class||value==Integer.class?"BIGINT":value==Boolean.class?"BOOLEAN":value==java.time.LocalDateTime.class?"TIMESTAMP":"VARCHAR(100000)";
            if(name.equals("id"))sql="BIGINT AUTO_INCREMENT PRIMARY KEY";else if(name.equals("deleted"))sql="BOOLEAN DEFAULT FALSE";else if(name.endsWith("_time"))sql="TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
            columns.putIfAbsent(name,name+" "+sql);
        }jdbc.execute("CREATE TABLE "+type.getAnnotation(TableName.class).value()+" ("+String.join(",",columns.values())+")");
    }
}
