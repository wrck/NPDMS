package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationAuthorizationService;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeProjectionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Current physical schema and real Owner writes in a connection-local clone only. */
@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql",matches="true")
class ProjectMasterMigrationMySqlTest {
    Connection db;
    org.springframework.context.support.GenericApplicationContext context;
    org.springframework.jdbc.datasource.DataSourceTransactionManager transactions;
    org.springframework.transaction.TransactionStatus transaction;
    org.apache.ibatis.session.SqlSession session;
    ProjectMasterMigrationMapper mapper;
    ProjectMasterMigrationService owner;
    Map<String,String> original;
    Long version;
    final Long id=992203060189L;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));
        assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        try(var sql=db.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE approved_project_master_test LIKE proj_project");
            assertEquals(1,sql.executeUpdate("INSERT INTO approved_project_master_test SELECT * FROM proj_project WHERE id=992203060189 AND tenant_id=1 AND deleted=b'0'"));
            sql.execute("ALTER TABLE approved_project_master_test RENAME TO proj_project");
            for(String table:List.of("proj_project_tree_version","proj_project_tree_path")) {
                sql.execute("CREATE TEMPORARY TABLE approved_"+table+" LIKE "+table);
                sql.execute("ALTER TABLE approved_"+table+" RENAME TO "+table);
            }
            // Reset only this connection-local clone to represent initial migration, never the real project.
            assertEquals(1,sql.executeUpdate("UPDATE proj_project SET lifecycle_status='ACTIVE', current_stage='S4', closed_at=NULL WHERE id=992203060189"));
        }
        db.setAutoCommit(false);
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        // Native batch mappers must also use this connection's temporary tables.
        var dataSource=new org.springframework.jdbc.datasource.SingleConnectionDataSource(db,true);
        factory.setDataSource(dataSource);
        factory.setTransactionFactory(new org.mybatis.spring.transaction.SpringManagedTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        var tenant=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        tenant.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                    @Override public net.sf.jsqlparser.expression.Expression getTenantId(){
                        return new net.sf.jsqlparser.expression.LongValue(TenantContextHolder.getRequiredTenantId());
                    }
                }));
        config.addInterceptor(tenant);
        tenant.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());
        var global=com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.defaults();
        global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config,global);
        config.addMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.class);
        config.addMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper.class);
        factory.setGlobalConfig(global);
        factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/legacydetail/ProjectMasterMigrationMapper.xml"),
                new org.springframework.core.io.ClassPathResource("mapper/projecttree/ProjectTreeVersionMapper.xml"));
        var sessions=factory.getObject();
        context=new org.springframework.context.support.GenericApplicationContext();
        context.getBeanFactory().registerSingleton("dataSource",dataSource);
        context.getBeanFactory().registerSingleton("sqlSessionFactory",sessions);
        context.refresh();
        var spring=new cn.hutool.extra.spring.SpringUtil();spring.setApplicationContext(context);spring.postProcessBeanFactory(context.getBeanFactory());
        transactions=new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        transaction=transactions.getTransaction(new org.springframework.transaction.support.DefaultTransactionDefinition());
        session=sessions.openSession();mapper=session.getMapper(ProjectMasterMigrationMapper.class);
        TenantContextHolder.setTenantId(1L);
        var login=new cn.iocoder.yudao.framework.security.core.LoginUser();login.setId(1L);login.setTenantId(1L);login.setUserType(2);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(login,null,List.of()));
        var row=mapper.selectIdentity(new ProjectMasterMigrationMapper.IdentityQuery(1L,id));
        assertEquals("ACTIVE",row.getLifecycleStatus());version=row.getVersion();
        var sources=mock(ProjectLegacyDetailMapper.class);
        var source=new ProjectLegacySourceDO();source.setTenantId(1L);source.setProjectId(id);
        source.setSourceProjectKey("52564");source.setCurrentSnapshotId(20L);
        when(sources.selectSource(any())).thenReturn(source);when(sources.selectSourceForUpdate(any())).thenReturn(source);
        var companies=mock(CompanyApi.class);var company=new CompanyRespDTO();
        company.setId(row.getCompanyId());company.setCode("001");company.setName(row.getCompanyName());
        when(companies.getCompanyByCode("001")).thenReturn(company);
        var departments=mock(DeptApi.class);var dept=new DeptRespDTO();
        dept.setId(row.getDepartmentId());dept.setCode(row.getDepartmentCode());
        when(departments.getDept(row.getDepartmentId())).thenReturn(dept);
        owner=new ProjectMasterMigrationService(mapper,sources,mock(ProjectManualCreationService.class),companies,departments,mock(AdminUserApi.class),mock(SystemUserMigrationApi.class),mock(SystemDepartmentMigrationApi.class),mock(ProjectCreationAuthorizationService.class),mock(OrganizationScopeApi.class),mock(ProjectTreeProjectionService.class));
        original=new LinkedHashMap<>();ProjectMasterMigrationApi.ORIGINAL_COLUMNS.forEach(k->original.put(k,null));
        original.put("projectId","52564");original.put("projectCode","approved-history-test");original.put("projectName","历史项目");
        original.put("projectState","100");original.put("projectType","10");original.put("column001",row.getDepartmentCode());original.put("compId","1");
        original.put("column010","10");original.put("column011","20");original.put("column012","1");
        original.put("createTime","2023-01-02 03:04:05");original.put("projectCloseTime","2024-12-23 09:46:31");
        original.put("column009","2022-08-01 12:13:14");original.put("disabled","0");
        original.put("customInfo","{\"phone\":\"123\",\"money\":\"12.30\",\"nested\":{\"value\":true}}");
        var payload=new LinkedHashMap<String,Object>();original.forEach((k,v)->payload.put(k,v==null?tools.jackson.databind.node.NullNode.getInstance():v));
        var record=new ProjectLegacyRecordDO().setSourceTable("pm_project_header").setSourceKey("52564").setPayloadJson(JsonUtils.toJsonString(payload));
        when(sources.selectRecords(any())).thenReturn(List.of(record));
    }
    @AfterEach void close() throws Exception {
        TenantContextHolder.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        if(transaction!=null&&!transaction.isCompleted())transactions.rollback(transaction);
        if(session!=null)session.close();if(context!=null)context.close();if(db!=null&&!db.isClosed())db.close();
    }
    private Request request(Facts last) {
        return new Request(1L,1L,"integration:1:12",List.of(new Entry("52564",id,version,
                new LinkedHashMap<>(original),"001",original.get("column001"),null,null,last)),true);
    }
    private Map<String,Object> row() throws Exception {
        try(var sql=db.createStatement();var rs=sql.executeQuery("SELECT * FROM proj_project WHERE id=992203060189 AND tenant_id=1")) {
            assertTrue(rs.next());var values=new LinkedHashMap<String,Object>();var meta=rs.getMetaData();
            for(int i=1;i<=meta.getColumnCount();i++)values.put(meta.getColumnLabel(i),rs.getObject(i));
            assertFalse(rs.next());return values;
        }
    }
    @Test void restorationProtectsEveryColumnAndTenantWithoutRewritingAudit() throws Exception {
        try(var sql=db.createStatement()) {
            sql.executeUpdate("UPDATE proj_project SET source_type='MIGRATION',deleted=b'1',version=1,parent_id=NULL,"
                    +"root_id=id,code_root_id=id,manager_id=NULL,active_plan_version_id=NULL,lifecycle_template_id=NULL,"
                    +"assignment_status='UNASSIGNED' WHERE id=992203060189");
        }
        session.clearCache();var identity=new ProjectMasterMigrationMapper.IdentityQuery(1L,id);
        assertNull(mapper.selectIdentity(identity));assertTrue(mapper.selectRestorationIdentityForUpdate(identity).getDeleted());
        String proof=mapper.selectRestorationFingerprint(identity);assertNotNull(proof);
        assertNull(mapper.selectRestorationFingerprint(new ProjectMasterMigrationMapper.IdentityQuery(2L,id)));
        try(var sql=db.createStatement()) {sql.executeUpdate("UPDATE proj_project SET creation_reason='later edit' WHERE id=992203060189");}
        session.clearCache();assertNotEquals(proof,mapper.selectRestorationFingerprint(identity));
        assertEquals(0,mapper.restoreHistoricalIfMatch(new ProjectMasterMigrationMapper.RestorationUpdate(1L,id,1L,proof)));
        var before=row();proof=mapper.selectRestorationFingerprint(identity);
        assertEquals(0,mapper.restoreHistoricalIfMatch(new ProjectMasterMigrationMapper.RestorationUpdate(2L,id,1L,proof)));
        assertEquals(1,mapper.restoreHistoricalIfMatch(new ProjectMasterMigrationMapper.RestorationUpdate(1L,id,1L,proof)));
        var after=row();assertNotEquals(before.remove("deleted"),after.remove("deleted"));assertEquals(before,after);
    }
    @Test void newHistoricalRootUsesTheExistingSchemaAndNeverCreatesCurrentRuntimeFacts() throws Exception {
        var existing=mapper.selectIdentity(new ProjectMasterMigrationMapper.IdentityQuery(1L,id));
        var companies=mock(CompanyApi.class);var company=new CompanyRespDTO();company.setId(existing.getCompanyId());
        company.setCode("001");company.setName(existing.getCompanyName());when(companies.getCompanyByCode("001")).thenReturn(company);
        var departments=mock(DeptApi.class);var dept=new DeptRespDTO();dept.setId(existing.getDepartmentId());
        dept.setCode(existing.getDepartmentCode());dept.setName(existing.getDepartmentName());
        when(departments.getDept(dept.getId())).thenReturn(dept);
        var catalogue=mock(SystemDepartmentMigrationApi.class);
        when(catalogue.getCatalogue()).thenReturn(List.of(new SystemDepartmentMigrationApi.Department(
                dept.getId(),dept.getCode(),dept.getName(),0L,0,null,null,null,0,0,null,null,null,null)));
        var organization=mock(OrganizationScopeApi.class);when(organization.hasScope(1L,company.getId(),dept.getId())).thenReturn(true);
        var newOwner=new ProjectMasterMigrationService(mapper,mock(ProjectLegacyDetailMapper.class),mock(ProjectManualCreationService.class),
                companies,departments,mock(AdminUserApi.class),mock(SystemUserMigrationApi.class),catalogue,mock(ProjectCreationAuthorizationService.class),organization,projection());
        var raw=new LinkedHashMap<>(original);raw.put("projectId","900000001");raw.put("projectCode","approved-new-history-root");
        raw.put("projectCloseTime",null);
        var request=new Request(1L,1L,"integration:1:12",List.of(new Entry("900000001",null,null,raw,"001",dept.getCode(),null,null,null)),false);
        var before=row();var preview=newOwner.preview(request).getFirst();
        assertEquals("CREATED",preview.action());assertNull(preview.before());assertNull(preview.after().id());assertEquals(before,row());
        var result=newOwner.apply(request).getFirst();assertEquals("CREATED",result.action());assertNotNull(result.after().id());
        var created=mapper.selectIdentity(new ProjectMasterMigrationMapper.IdentityQuery(1L,result.after().id()));
        assertEquals(result.after(),ProjectMasterMigrationService.snapshot(created));assertEquals("MIGRATION",created.getSourceType());
        assertEquals(created.getId(),created.getCodeRootId());assertEquals(created.getId(),created.getRootId());
        assertEquals(0,created.getProjectSequence());assertEquals("",created.getTreePath());assertNull(created.getParentId());
        assertEquals("NORMAL_CLOSED",created.getLifecycleStatus());assertNull(created.getClosedAt());
        assertNull(created.getCreator());assertNull(created.getUpdater());assertEquals(created.getCreateTime(),created.getUpdateTime());
        assertNull(created.getLifecycleTemplateId());assertNull(created.getLifecycleTemplateRevisionId());assertNull(created.getActivePlanVersionId());
        assertNull(created.getCreationReason());assertNull(created.getImplementationLocation());assertEquals(before,row());
        var tree=session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.class)
                .selectLatestActive(created.getId());
        assertNotNull(tree);assertEquals(1L,tree.getTreeVersion());assertEquals(1,tree.getNodeCount());assertEquals(1,tree.getPathCount());
        assertEquals(1L,tree.getTenantId());assertEquals("1",tree.getCreator());
        var paths=session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper.class)
                .selectByAncestor(created.getId(),1L,created.getId(),null);
        assertEquals(1,paths.size());assertEquals(created.getId(),paths.getFirst().getDescendantProjectId());assertEquals(0,paths.getFirst().getDistance());
        var permission=mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
        when(permission.hasAnyRoles(1L,cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum.SUPER_ADMIN.getCode())).thenReturn(true);
        var scope=new cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService(
                session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.class),
                mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.class),
                session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper.class),
                session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.class),
                mock(cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi.class),permission,
                mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectOrganizationScopeService.class));
        var normal=mock(cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationServiceImpl.class,CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(normal,"projectMasterMapper",session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(normal,"projectTreeVersionMapper",session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(normal,"projectTreeScopeService",scope);
        assertEquals(created.getId(),normal.getProjectForManage(created.getId(),new ProjectManualCreationService.ProjectAccessActor(1L,1L)).getId());
        var verification=new BindingVerification(1L,1L,List.of(result.after()));
        assertEquals(verification.projects(),newOwner.inspectBindings(new IdentityInspection(1L,1L,
                List.of(new IdentityTarget(created.getId(),created.getProjectType(),created.getProjectCode())))));
        newOwner.verifyBindings(verification);newOwner.lockBindings(verification);
        assertEquals(result.after(),ProjectMasterMigrationService.snapshot(mapper.selectIdentity(new ProjectMasterMigrationMapper.IdentityQuery(1L,created.getId()))));
        try(var sql=db.prepareStatement("SELECT COUNT(*) FROM proj_project_stage WHERE project_id=?")) {
            sql.setLong(1,created.getId());try(var rs=sql.executeQuery()){assertTrue(rs.next());assertEquals(0,rs.getLong(1));}
        }
    }
    @Test void missingOriginalOrganizationUsesNullableFormalFieldsAndCanReplayWithoutInventingGrants() throws Exception {
        var existing=mapper.selectIdentity(new ProjectMasterMigrationMapper.IdentityQuery(1L,id));
        var companies=mock(CompanyApi.class);var company=new CompanyRespDTO();company.setId(existing.getCompanyId());
        company.setCode("001");company.setName(existing.getCompanyName());when(companies.getCompanyByCode("001")).thenReturn(company);
        var departments=mock(DeptApi.class);var dept=new DeptRespDTO();dept.setId(existing.getDepartmentId());
        dept.setCode(existing.getDepartmentCode());dept.setName(existing.getDepartmentName());when(departments.getDept(dept.getId())).thenReturn(dept);
        var catalogue=mock(SystemDepartmentMigrationApi.class);
        when(catalogue.getCatalogue()).thenReturn(List.of(new SystemDepartmentMigrationApi.Department(
                dept.getId(),dept.getCode(),dept.getName(),0L,0,null,null,null,0,0,null,null,null,null)));
        var authorization=mock(ProjectCreationAuthorizationService.class);var organization=mock(OrganizationScopeApi.class);
        var newOwner=new ProjectMasterMigrationService(mapper,mock(ProjectLegacyDetailMapper.class),mock(ProjectManualCreationService.class),
                companies,departments,mock(AdminUserApi.class),mock(SystemUserMigrationApi.class),catalogue,authorization,organization,projection());
        var preserved=row();
        for(int mask=1;mask<=3;mask++) {
            boolean missingCompany=(mask&1)!=0,missingDepartment=(mask&2)!=0;
            String sourceKey="90000001"+mask;var raw=new LinkedHashMap<>(original);
            raw.put("projectId",sourceKey);raw.put("projectCode","approved-missing-organization-"+mask);
            if(missingCompany)raw.put("compId",null);if(missingDepartment)raw.put("column001",null);
            var entry=new Entry(sourceKey,null,null,raw,missingCompany?null:"001",missingDepartment?null:dept.getCode(),null,null,null);
            var request=new Request(1L,1L,"integration:1:12",List.of(entry),false);
            assertEquals("CREATED",newOwner.preview(request).getFirst().action());
            var result=newOwner.apply(request).getFirst();
            var created=mapper.selectIdentity(new ProjectMasterMigrationMapper.IdentityQuery(1L,result.after().id()));
            assertEquals(missingCompany?null:company.getId(),created.getCompanyId());
            assertEquals(missingCompany?null:"001",created.getCompanyCode());
            assertEquals(missingDepartment?null:dept.getId(),created.getDepartmentId());
            assertEquals(missingDepartment?null:dept.getCode(),created.getDepartmentCode());
            assertNull(created.getActivePlanVersionId());assertNull(created.getManagerId());
            var repeat=new Entry(sourceKey,created.getId(),null,raw,entry.companyCode(),entry.departmentCode(),null,null,result.after());
            assertEquals("UNCHANGED",newOwner.apply(new Request(1L,1L,"integration:1:12",List.of(repeat),false)).getFirst().action());
            assertEquals(preserved,row());
        }
        verify(authorization,times(9)).assertCanImportWithoutOrganization(1L);verifyNoInteractions(organization);
    }
    private ProjectTreeProjectionService projection() {
        var result=new ProjectTreeProjectionService(
                session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.class),
                session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.class),
                session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper.class),
                mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeChangeMapper.class),
                mock(cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.class),
                mock(cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeMetrics.class),
                mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(result,"ruleEvents",
                mock(cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi.class));
        return result;
    }
    @Test void failedNativeProjectionRollsBackRootVersionAndPathsInTheMigrationTransaction() throws Exception {
        var raw=new LinkedHashMap<>(original);raw.put("projectId","900000020");
        raw.put("projectCode","approved-rollback-history-root");raw.put("compId",null);raw.put("column001",null);
        var projection=projection();
        var events=mock(cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi.class);
        doThrow(new IllegalStateException("APPROVED_TEST_OUTBOX_FAILURE")).when(events).append(anyString(),anyString(),any());
        org.springframework.test.util.ReflectionTestUtils.setField(projection,"ruleEvents",events);
        var target=new ProjectMasterMigrationService(mapper,mock(ProjectLegacyDetailMapper.class),mock(ProjectManualCreationService.class),
                mock(CompanyApi.class),mock(DeptApi.class),mock(AdminUserApi.class),mock(SystemUserMigrationApi.class),
                mock(SystemDepartmentMigrationApi.class),mock(ProjectCreationAuthorizationService.class),mock(OrganizationScopeApi.class),projection);
        var proxy=new org.springframework.aop.framework.ProxyFactory(target);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var request=new Request(1L,1L,"integration:1:12",List.of(new Entry("900000020",null,null,raw,null,null,null,null,null)),false);
        var guarded=(ProjectMasterMigrationApi)proxy.getProxy();
        assertEquals("APPROVED_TEST_OUTBOX_FAILURE",assertThrows(IllegalStateException.class,()->guarded.apply(request)).getMessage());
        assertTrue(transaction.isRollbackOnly());
        transactions.rollback(transaction);session.clearCache();
        try(var sql=db.createStatement()) {
            for(var table:List.of("proj_project_tree_version","proj_project_tree_path"))
                try(var rs=sql.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(rs.next());assertEquals(0,rs.getLong(1),table);}
            try(var rs=sql.executeQuery("SELECT COUNT(*) FROM proj_project")){assertTrue(rs.next());assertEquals(1,rs.getLong(1));}
        }
    }
    @Test void ownerWritesFormalHistoryWithNullAuditAndPreservesEveryOtherColumn() throws Exception {
        var before=row();var preview=owner.preview(request(null)).getFirst();assertEquals("UPDATED",preview.action());
        assertEquals(before,row());
        var result=owner.apply(request(null)).getFirst();assertEquals(preview,result);
        var persisted=mapper.selectIdentity(new ProjectMasterMigrationMapper.IdentityQuery(1L,id));
        assertEquals(result.after(),ProjectMasterMigrationService.snapshot(persisted));
        assertNull(persisted.getCreator());assertNull(persisted.getUpdater());
        assertEquals(LocalDateTime.of(2023,1,2,3,4,5),persisted.getCreateTime());assertEquals(persisted.getCreateTime(),persisted.getUpdateTime());
        assertEquals("NORMAL_CLOSED",persisted.getLifecycleStatus());assertEquals("S6",persisted.getCurrentStage());
        assertEquals("approved-history-test",persisted.getProjectCode());
        assertEquals(LocalDateTime.of(2022,8,1,12,13,14),persisted.getOrderCreateTime());
        var mutable=Set.of("project_code","project_name","company_id","company_code","company_name","project_type","project_category",
                "signing_method","implementation_mode","customer_project_name","sales_type","major_project_level","market_name","system_name",
                "expend_name","industry_name","not_track_reason","project_start_time","project_refresh_time","project_close_time","order_create_time",
                "custom_info","disabled","effective_from","effective_to","creator","create_time","updater","update_time","lifecycle_status","current_stage","closed_at","version");
        var after=row();for(var column:before.keySet())if(!mutable.contains(column))assertEquals(before.get(column),after.get(column),column);
        assertEquals("UNCHANGED",owner.apply(request(result.after())).getFirst().action());assertEquals(after,row());
        try(var sql=db.createStatement()) {assertEquals(1,sql.executeUpdate("UPDATE proj_project SET version=version+1 WHERE id=992203060189"));}
        session.clearCache();var changed=row();
        assertEquals("ISSUE",owner.apply(request(result.after())).getFirst().action());assertEquals(changed,row());
    }
    @Test void sqlCompareAndSetRejectsTenantVersionAndLifecycleChanges() throws Exception {
        var before=row();var row=mapper.selectIdentity(new ProjectMasterMigrationMapper.IdentityQuery(1L,id));
        var facts=owner.preview(request(null)).getFirst().after();
        assertEquals(0,mapper.initializeFormalIfMatch(new ProjectMasterMigrationMapper.InitializationUpdate(2L,id,version,row.getLifecycleStatus(),row.getCurrentStage(),facts)));
        assertEquals(0,mapper.initializeFormalIfMatch(new ProjectMasterMigrationMapper.InitializationUpdate(1L,id,version-1,row.getLifecycleStatus(),row.getCurrentStage(),facts)));
        assertEquals(0,mapper.initializeFormalIfMatch(new ProjectMasterMigrationMapper.InitializationUpdate(1L,id,version,"EXCEPTION_CLOSED",row.getCurrentStage(),facts)));
        assertEquals(before,row());
    }
}
