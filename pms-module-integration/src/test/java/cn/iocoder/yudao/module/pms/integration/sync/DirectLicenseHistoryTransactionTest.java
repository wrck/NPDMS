package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;


import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="P37_TEMP_MYSQL_TEST",matches="true")
class DirectLicenseHistoryTransactionTest {
    java.sql.Connection temporaryConnection;JdbcTemplate jdbc;TransactionTemplate tx;DirectLicenseHistoryPersistence service;DirectLicenseHistoryMapper mapper;
    final PermissionApi permissions=mock(PermissionApi.class);
    final AdminUserApi users=mock(AdminUserApi.class);
    final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    final PlatformMigrationEvidenceApi evidence=mock(PlatformMigrationEvidenceApi.class);
    final DirectLicenseHistoryCipher cipher=new DirectLicenseHistoryCipher(DirectLicenseHistoryCipherTest.KEY,"TEST-V1");
    final Map<String,DirectLicenseHistoryPersistence.Saved> receipts=new HashMap<>();
    final Map<String,MigrationSourceRecordFact> facts=new LinkedHashMap<>();
    final License source=new License("201",null,"  MODEL  ","CONTRACT","CUSTOM","CODE","原状态",null,DirectLicenseHistoryCipherTest.ORIGINAL);
    final Command command=new Command(1L,7L,null,200L,LocalDateTime.of(2020,1,2,3,4),List.of(source));
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);var loader=new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(System.getenv("P22_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();var properties=new java.util.Properties();properties.setProperty("user",System.getenv("P22_DB_USER"));properties.setProperty("password",System.getenv("P22_DB_PASSWORD"));temporaryConnection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);var data=new SingleConnectionDataSource(temporaryConnection,true);jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TEMPORARY TABLE ast_device(id BIGINT PRIMARY KEY,tenant_id BIGINT,sn VARCHAR(255),deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE com_contract(id BIGINT PRIMARY KEY,tenant_id BIGINT,contract_no VARCHAR(32),deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE ast_device_license_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,device_id BIGINT,device_sn VARCHAR(255),contract_id BIGINT,contract_no VARCHAR(32),contract_type VARCHAR(16),product_code VARCHAR(255),product_model VARCHAR(256),license_status VARCHAR(255),license_code_ciphertext TEXT,creator VARCHAR(64),create_time TIMESTAMP(3),updater VARCHAR(64),update_time TIMESTAMP(3),deleted BOOLEAN)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),data));String path="mapper/sync/DirectLicenseHistoryMapper.xml";try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(DirectLicenseHistoryMapper.class);
        tx=new TransactionTemplate(new DataSourceTransactionManager(data));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);service=new DirectLicenseHistoryPersistence(data,mapper,cipher,permissions,users,commands,evidence);
        when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        when(commands.execute(any(),anyString(),eq(DirectLicenseHistoryPersistence.Saved.class),any(),any())).thenAnswer(inv->{
            var scope=inv.getArgument(0,IdempotencyScope.class);var previous=receipts.get(scope.key());
            if(previous!=null)return new ExecutionResult<>(previous.checksum().equals(inv.getArgument(1))?Decision.REPLAY_COMPLETED:Decision.CONFLICT,previous);
            var saved=(DirectLicenseHistoryPersistence.Saved)inv.getArgument(3,Supplier.class).get();receipts.put(scope.key(),saved);
            return new ExecutionResult<>(Decision.NEW,saved);
        });
        when(evidence.preview(any())).thenAnswer(inv->{
            var q=inv.getArgument(0,MigrationPreviewCommand.class);var items=new ArrayList<MigrationPreviewItem>();
            for(var request:q.records()) {
                var fact=facts.get(request.sourcePk());var saved=fact==null?null:JsonUtils.parseObject(fact.sourcePayloadJson(),DirectLicenseHistoryPersistence.Saved.class);
                var targets=saved==null?List.<MigrationPreviewTarget>of():List.of(new MigrationPreviewTarget("MAPPED","AST","DeviceLicenseRecord","ast_device_license_record",saved.id(),"LICENSE_HISTORY",0,"test"));
                items.add(new MigrationPreviewItem(request.sourcePk(),saved==null?MigrationPreviewClassification.NO_EXISTING_SOURCE:
                    saved.checksum().equals(request.sourceChecksum())?MigrationPreviewClassification.MAPPED:MigrationPreviewClassification.CHECKSUM_CONFLICT,
                    fact==null?null:fact.sourceRecordId(),fact==null?null:fact.batchId(),fact==null?null:fact.sourceChecksum(),targets));
            }
            return new MigrationPreviewResult("DPPMS",DirectLicenseHistoryPersistence.SOURCE_TABLE,items,0,0,0,0,0,0);
        });
        when(evidence.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(new ArrayList<>(),null));
        var batch=mock(MigrationBatchFact.class);when(batch.batchId()).thenReturn(800L);when(batch.version()).thenReturn(0);
        when(evidence.createImportBatch(any())).thenReturn(batch);
        var claim=mock(MigrationBatchClaimResult.class);when(claim.claimed()).thenReturn(true);when(claim.batch()).thenReturn(batch);
        when(evidence.claimStagedBatch(any())).thenReturn(claim);
        when(evidence.appendSourceRecords(any())).thenAnswer(inv->{
            var q=inv.getArgument(0,AppendMigrationSourceRecordsCommand.class);var result=new ArrayList<MigrationSourceRecordFact>();
            for(var row:q.records()) {var fact=new MigrationSourceRecordFact(900L+facts.size(),1L,800L,"DPPMS",row.sourceTable(),row.sourcePk(),row.sourceBusinessKey(),row.sourcePayloadJson(),row.sourceChecksum(),row.extractedAt(),"MAPPED");facts.put(row.sourcePk(),fact);result.add(fact);}
            when(evidence.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.copyOf(facts.values()),null));return result;
        });
    }
    @AfterEach void clear() throws Exception {TenantContextHolder.clear();if(temporaryConnection!=null)temporaryConnection.close();}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_license_record",Integer.class);}
    List<DirectLicenseHistoryPersistence.Preview> preview(Command q){return tx.execute(status->service.preview(q));}
    List<Target> apply(Command q){return tx.execute(status->service.apply(q));}
    DirectLicenseHistoryRow row(long id){return tx.execute(status->mapper.selectRecordForUpdate(new DirectLicenseHistoryMapper.RecordQuery(1L,id)));}
    @Test void encryptedFullFactsReplayAcrossSnapshotsAndAuditArePreserved() {
        assertEquals("CREATE",preview(command).getFirst().action());assertEquals(0,count());assertThrows(IllegalStateException.class,()->service.apply(command));var saved=apply(command).getFirst();var row=row(saved.id());assertNull(row.getDeviceSn());assertNull(row.getDeviceId());assertNull(row.getContractId());assertEquals("  MODEL  ",row.getProductModel());assertEquals("原状态",row.getLicenseStatus());assertEquals("7",row.getCreator());assertNotEquals(command.extractedAt(),row.getCreateTime());assertEquals(source.envelope().codeMac(),cipher.verifyStored(row.getLicenseCodeCiphertext(),1L));assertFalse(JsonUtils.toJsonString(row).contains("licenseCodeCiphertext"));assertFalse(row.toString().contains(row.getLicenseCodeCiphertext()));
        var other=new Command(1L,7L,null,201L,command.extractedAt(),command.licenses());assertEquals("UNCHANGED",preview(other).getFirst().action());assertEquals(saved.id(),apply(other).getFirst().id());assertEquals(1,count());
        jdbc.update("UPDATE ast_device_license_record SET updater='manual'");assertThrows(IllegalArgumentException.class,()->apply(command));
    }
    @Test void secondRecordSqlFailureRollsBackFirstEncryptedRecord() {
        var capture=new SyncLicenseCaptureCipher(DirectLicenseHistoryCipherTest.KEY,"TEST-V1");var envelope=capture.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"202","synthetic-only".toCharArray()));
        var second=new License("202",null,"REJECT",null,null,null,null,null,envelope);jdbc.execute("ALTER TABLE ast_device_license_record ADD CONSTRAINT second_license_rejected CHECK(product_model<>'REJECT')");var input=new Command(1L,7L,null,200L,command.extractedAt(),List.of(source,second));
        var failure=assertThrows(org.springframework.jdbc.UncategorizedSQLException.class,()->apply(input));assertEquals(3819,failure.getSQLException().getErrorCode());assertTrue(failure.getSQLException().getMessage().contains("second_license_rejected"));assertEquals(0,count());verify(evidence,never()).appendSourceRecords(any());
    }
    @Test void approvedEmptyCodeRemainsEncryptedAndReplayableWithAnExplicitSourceIssue() {
        var capture=new DirectLicenseCaptureCipher(DirectLicenseHistoryCipherTest.KEY,"TEST-V1");
        var envelope=capture.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"472122",new char[0]));
        var empty=new License("472122",null,"MODEL",null,null,null,null,null,envelope);
        var input=new Command(1L,7L,null,200L,command.extractedAt(),List.of(empty));
        var proposed=preview(input).getFirst();assertEquals("CREATE",proposed.action());assertEquals(List.of("LICENSE_CODE_EMPTY"),proposed.sourceIssues());assertEquals(0,count());
        var saved=apply(input).getFirst();assertEquals(1,count());var stored=row(saved.id()).getLicenseCodeCiphertext();
        assertEquals(envelope.codeMac(),cipher.verifyStored(stored,1L));assertNotEquals("",stored);
        var replay=preview(input).getFirst();assertEquals("UNCHANGED",replay.action());assertEquals(List.of("LICENSE_CODE_EMPTY"),replay.sourceIssues());
        assertEquals(saved.id(),apply(input).getFirst().id());assertEquals(1,count());
    }
    @Test void evidenceFailureRollsBackInsertedRecordAndMissingKeysCannotBeBypassed() {
        doThrow(new IllegalStateException("evidence rejected")).when(evidence).completeReconciliation(any());assertThrows(IllegalStateException.class,()->apply(command));assertEquals(0,count());
        var missing=new DirectLicenseHistoryCipher("","TEST-V1");assertThrows(IllegalArgumentException.class,()->missing.verify(source.envelope(),1L));assertEquals(0,count());
    }
    @Test void tenantScopeSourceSubstitutionAndExecutePermissionRejectBeforeWrite() {
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->apply(command));TenantContextHolder.setTenantId(1L);assertThrows(IllegalArgumentException.class,()->apply(new Command(1L,7L,null,200L,command.extractedAt(),List.of(source,source))));var bad=new License("202",null,null,null,null,null,null,null,source.envelope());assertThrows(IllegalArgumentException.class,()->apply(new Command(1L,7L,null,200L,command.extractedAt(),List.of(bad))));doReturn(false).when(permissions).hasAnyPermissions(7L,"pms:integration:execute");assertThrows(IllegalArgumentException.class,()->apply(command));assertEquals(0,count());
        var d=DppmsDirectLicenseHistoryTemplate.create(9L);assertEquals("DPPMS_SECURE_LICENSE_DIRECT",d.adapter());assertDoesNotThrow(()->SyncSecureLicenseReader.validate(d,d.sources().getFirst()));
    }
    @Test void contractsAreResolvedOnlyInsideTenantAndAmbiguityIsRetained() {
        jdbc.update("INSERT INTO com_contract VALUES(1,1,'SINGLE',0),(2,2,'SINGLE',0),(3,1,'SHARED',0),(4,1,'SHARED',0),(5,1,'DELETED',1)");
        assertEquals(List.of(1L),mapper.selectContractIds(new DirectLicenseHistoryMapper.ContractIdentity(1L,"SINGLE")));
        assertEquals(2,mapper.selectContractIds(new DirectLicenseHistoryMapper.ContractIdentity(1L,"SHARED")).size());
        assertTrue(mapper.selectContractIds(new DirectLicenseHistoryMapper.ContractIdentity(1L,"DELETED")).isEmpty());
        assertTrue(mapper.selectContractIds(new DirectLicenseHistoryMapper.ContractIdentity(1L,"MISSING")).isEmpty());
        assertTrue(mapper.selectContractIds(new DirectLicenseHistoryMapper.ContractIdentity(3L,"SINGLE")).isEmpty());
        assertEquals(0,count());
    }
}
