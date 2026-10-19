package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsContractCompletionApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsContractCompletionServiceTest {
    private DppmsContractCompletionMapper mapper;
    private PermissionApi permissions;
    private DeviceOrganizationProjectionApi projections;
    private ManagedOrganizationApi organizations;
    private DppmsContractCompletionService service;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        mapper=mock(DppmsContractCompletionMapper.class);permissions=mock(PermissionApi.class);
        projections=mock(DeviceOrganizationProjectionApi.class);organizations=mock(ManagedOrganizationApi.class);
        service=new DppmsContractCompletionService(mapper,permissions,mock(AdminUserApi.class),projections,organizations);
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        when(mapper.countValidInputs(any())).thenReturn(5L);
    }
    @AfterEach void clear() {TenantContextHolder.clear();}
    private DeliveryScope scope(){return new DeliveryScope(Set.of(10L),Set.of(20L),Set.of(40L));}
    private Command command(boolean preview) {return new Command(1L,2L,preview,scope(),Set.of(20L),Set.of(30L));}
    @Test void deniedTenantAndEmptyScopeNeverCreateTemporaryTables() {
        assertThrows(IllegalArgumentException.class,()->service.complete(new Command(9L,2L,false,scope(),Set.of(20L),Set.of())));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.complete(new Command(1L,3L,false,scope(),Set.of(20L),Set.of())));
        assertEquals(0,service.complete(new Command(1L,2L,false,new DeliveryScope(Set.of(),Set.of(),Set.of()),Set.of(),Set.of())).changedContracts());
        verifyNoInteractions(mapper,organizations,projections);
    }
    @Test void previewCountsOnlyAndApplyCompletesReferencesAndRefreshesProjectionOnce() {
        when(mapper.selectCompanyChangeNumbers(any())).thenReturn(List.of("C1"));
        when(mapper.selectFieldChangeNumbers(any())).thenReturn(List.of("C1","C2"));
        when(mapper.countShipmentChanges(any())).thenReturn(3L);when(mapper.countPackageChanges(any())).thenReturn(4L);
        assertEquals(new Result(2,3,4),service.complete(command(true)));
        verify(mapper,never()).updateContractCompanies(any());verify(mapper,never()).updateShipmentReferences(any());
        verify(mapper,never()).updatePackageReferences(any());verify(mapper,never()).updateContractFields(any());verifyNoInteractions(projections);
        verify(mapper).dropReceivableFields();verify(mapper).dropShipmentFields();verify(mapper).dropUniqueContracts();verify(mapper).dropDepartments();verify(mapper).dropCompanies();verify(mapper).dropScope();
        clearInvocations(mapper,organizations);
        when(mapper.updateContractCompanies(any())).thenReturn(1);when(mapper.updateShipmentReferences(any())).thenReturn(2);
        when(mapper.updatePackageReferences(any())).thenReturn(3);when(mapper.updateContractFields(any())).thenReturn(2);
        when(mapper.selectAffectedContractNumbers(any())).thenReturn(List.of("C1","C2"));
        assertEquals(new Result(2,2,3),service.complete(command(false)));
        verify(organizations).list();verify(mapper).createUniqueContracts(any());verify(mapper).createShipmentFields(any());verify(mapper).createReceivableFields(any());
        verify(projections).refresh(new DeviceOrganizationProjectionApi.Refresh(1L,Set.of(),Set.of(),Set.of("C1","C2")));
        verify(mapper).dropReceivableFields();verify(mapper).dropShipmentFields();verify(mapper).dropUniqueContracts();verify(mapper).dropDepartments();verify(mapper).dropCompanies();verify(mapper).dropScope();
    }
    @Test void staleInputBlocksAllWritesAndCleanupFailureCannotHideOriginalFailure() {
        when(mapper.countValidInputs(any())).thenReturn(4L);
        doThrow(new IllegalStateException("cleanup")).when(mapper).dropReceivableFields();
        var error=assertThrows(IllegalArgumentException.class,()->service.complete(command(false)));
        assertEquals("DPPMS_CONTRACT_COMPLETION_INPUT_CHANGED",error.getMessage());assertEquals(1,error.getSuppressed().length);
        verify(mapper,never()).createShipmentFields(any());verify(mapper,never()).updateContractFields(any());
        verify(mapper).dropShipmentFields();verify(mapper).dropUniqueContracts();verify(mapper).dropDepartments();verify(mapper).dropCompanies();verify(mapper).dropScope();
        verifyNoInteractions(organizations,projections);
    }
    @Test void virtualOnlyPreviewStillValidatesExistingLocalInputs() {
        when(mapper.countValidInputs(any())).thenReturn(0L);
        var command=new Command(1L,2L,true,new DeliveryScope(Set.of(),Set.of(),Set.of()),Set.of(),Set.of(30L));
        assertThrows(IllegalArgumentException.class,()->service.complete(command));
        verify(mapper).countValidInputs(any());verify(mapper,never()).updateContractFields(any());verifyNoInteractions(projections,organizations);
    }
    @Test void projectionFailureRollsBackAllOwnerReferenceAndFieldWrites() {
        var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:contractCompletion"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);jdbc.execute("CREATE TABLE owner_change(kind VARCHAR(20) PRIMARY KEY)");
        when(mapper.updateContractCompanies(any())).thenAnswer(call->{jdbc.update("INSERT INTO owner_change VALUES('company')");return 1;});
        when(mapper.updateShipmentReferences(any())).thenAnswer(call->{jdbc.update("INSERT INTO owner_change VALUES('shipment')");return 1;});
        when(mapper.updatePackageReferences(any())).thenAnswer(call->{jdbc.update("INSERT INTO owner_change VALUES('package')");return 1;});
        when(mapper.updateContractFields(any())).thenAnswer(call->{jdbc.update("INSERT INTO owner_change VALUES('fields')");return 1;});
        when(mapper.selectAffectedContractNumbers(any())).thenReturn(List.of("C1"));
        doThrow(new IllegalStateException("projection failed")).when(projections).refresh(any());
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        assertThrows(IllegalStateException.class,()->tx.execute(status->service.complete(command(false))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM owner_change",Integer.class));
        verify(mapper).dropReceivableFields();verify(mapper).dropShipmentFields();verify(mapper).dropUniqueContracts();verify(mapper).dropDepartments();verify(mapper).dropCompanies();verify(mapper).dropScope();
    }
    @Test void systemDirectoryIsReadOnceAndAmbiguousDepartmentsAreNotChosen() {
        var company=new cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO().setId(1L).setCode("CO").setName("Company");company.setTenantId(1L);
        var first=new cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO().setId(10L).setCode("D").setName("First");first.setTenantId(1L);
        var duplicate=new cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO().setId(11L).setCode("D").setName("Second");duplicate.setTenantId(1L);
        var unique=new cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO().setId(1L).setCode("D2").setName("Disabled included").setStatus(1);unique.setTenantId(1L);
        when(organizations.list()).thenReturn(List.of(new ManagedOrganizationApi.Node(company,null,null),new ManagedOrganizationApi.Node(null,first,null),new ManagedOrganizationApi.Node(null,duplicate,null),new ManagedOrganizationApi.Node(null,unique,null)));
        service.complete(command(true));
        var captured=org.mockito.ArgumentCaptor.forClass(DppmsContractCompletionMapper.OrganizationBatch.class);
        verify(mapper).insertCompanies(captured.capture());verify(mapper).insertDepartments(captured.capture());verify(organizations).list();
        assertEquals(Set.of("COMPANY:CO","DEPARTMENT:D2"),captured.getAllValues().stream().flatMap(batch->batch.rows().stream()).map(row->row.kind()+":"+row.code()).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void projectionScopesRejectMissingForeignAndDuplicateOwnerRows() {
        var query=new ProjectionQuery(1L,2L,Target.CONTRACT,Set.of(10L));
        when(mapper.selectProjection(any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->service.readProjection(query));
        var foreign=new Projection();foreign.setId(20L);when(mapper.selectProjection(any())).thenReturn(List.of(foreign));
        assertThrows(IllegalArgumentException.class,()->service.readProjection(query));
        var projection=new Projection();projection.setId(10L);when(mapper.selectProjection(any())).thenReturn(List.of(projection,projection));
        assertThrows(IllegalArgumentException.class,()->service.readProjection(query));
        when(mapper.selectProjection(any())).thenReturn(List.of(projection));assertEquals(List.of(projection),service.readProjection(query));
    }
    @Test void mysqlTemporaryDirectoriesAreReferencedOnlyOncePerStatement() throws Exception {
        var config=new org.apache.ibatis.session.Configuration();
        String resource="mapper/migration/DppmsContractCompletionMapper.xml";
        try(var in=new org.springframework.core.io.ClassPathResource(resource).getInputStream()) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();
        }
        String prefix=DppmsContractCompletionMapper.class.getName()+".";
        for(var name:config.getMappedStatementNames().stream().filter(value->value.startsWith(prefix)).toList()) {
            String method=name.substring(prefix.length());
            Object query=switch(method) {
                case "insertScope"->new DppmsContractCompletionMapper.ScopeBatch(1L,List.of(new DppmsContractCompletionMapper.ScopeRow("CONTRACT",10L)));
                case "insertCompanies","insertDepartments"->new DppmsContractCompletionMapper.OrganizationBatch(1L,List.of(new DppmsContractCompletionMapper.OrganizationRow("COMPANY",1L,"CO","Company")));
                case "selectProjection"->new ProjectionQuery(1L,2L,Target.CONTRACT,Set.of(10L));
                default->new DppmsContractCompletionMapper.Query(1L);
            };
            String sql=config.getMappedStatement(name).getBoundSql(Map.of("query",query)).getSql();
            var matcher=java.util.regex.Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+(tmp_dppms_[a-z0-9_]+)").matcher(sql);
            var seen=new HashSet<String>();
            while(matcher.find())assertTrue(seen.add(matcher.group(1).toLowerCase(java.util.Locale.ROOT)),method+": MySQL cannot reopen "+matcher.group(1));
        }
    }
    @Test void realMapperProjectionAndUniqueDirectoryKeepTenantSourceDeletionAndCrossCompanyAmbiguity() throws Exception {
        var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:contractCompletionMapper"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        var columns=new ArrayList<String>(List.of("tenant_id BIGINT","deleted BOOLEAN","master_source_system VARCHAR(32)","contract_no VARCHAR(64)"));
        // These relationship columns belong to other COM objects, not com_contract (V160/current schema).
        for(var field:Projection.class.getDeclaredFields()) {
            if(Set.of("contractId","departmentId","shipmentContractReferenceId").contains(field.getName()))continue;
            String column=field.getName().replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(java.util.Locale.ROOT);
            String type=field.getType()==Long.class?"BIGINT":field.getType()==java.math.BigDecimal.class?"DECIMAL(20,2)":field.getType()==java.time.LocalDateTime.class?"TIMESTAMP":"VARCHAR(255)";
            columns.add(column+" "+type);
        }
        jdbc.execute("CREATE TABLE com_contract("+String.join(",",columns)+")");
        jdbc.update("INSERT INTO com_contract(tenant_id,deleted,master_source_system,id,company_code,contract_no,contract_amount) VALUES(1,0,'DPPMS_ERP',10,'A','ONLY',123456789012.12),(1,0,'DPPMS_ERP',11,'A','DUP',1),(1,0,'CRM',12,'B','DUP',2),(1,1,'DPPMS_ERP',13,'A','ONLY',3),(2,0,'DPPMS_ERP',14,'A','ONLY',4)");
        var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
        String resource="mapper/migration/DppmsContractCompletionMapper.xml";
        try(var in=new org.springframework.core.io.ClassPathResource(resource).getInputStream()) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();
        }
        try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession()) {
            var actual=session.getMapper(DppmsContractCompletionMapper.class);
            var rows=actual.selectProjection(new ProjectionQuery(1L,2L,Target.CONTRACT,Set.of(10L,12L,13L,14L)));
            assertEquals(1,rows.size());assertEquals(10L,rows.getFirst().getId());
            assertEquals(new java.math.BigDecimal("123456789012.12"),rows.getFirst().getContractAmount());
            assertTrue(actual.selectProjection(new ProjectionQuery(1L,2L,Target.CONTRACT,Set.of())).isEmpty());
            actual.createUniqueContracts(new DppmsContractCompletionMapper.Query(1L));
            try(var statement=session.getConnection().createStatement();var result=statement.executeQuery("SELECT contract_id FROM tmp_dppms_review_unique_contract")) {
                assertTrue(result.next());assertEquals(10L,result.getLong(1));assertFalse(result.next());
            } finally {
                // H2 supports the query/CTAS, but its DROP grammar has no MySQL TEMPORARY keyword.
                try(var cleanup=session.getConnection().createStatement()){cleanup.execute("DROP TABLE tmp_dppms_review_unique_contract");}
            }
        }
    }
}
