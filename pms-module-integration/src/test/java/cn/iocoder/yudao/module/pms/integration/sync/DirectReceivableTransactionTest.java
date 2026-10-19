package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractReceivableMigrationApi;
class DirectReceivableTransactionTest {
    JdbcTemplate jdbc;TransactionTemplate tx;DppmsDirectReceivableTask task;SyncRunDO run;
    final SyncTaskService tasks=mock(SyncTaskService.class);final SyncRunMapper runs=mock(SyncRunMapper.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source=new DriverManagerDataSource("jdbc:h2:mem:p24-direct-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);jdbc.execute("""
CREATE TABLE com_contract_receivable(id BIGINT PRIMARY KEY,tenant_id BIGINT,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP(6),update_time TIMESTAMP(6),deleted BOOLEAN DEFAULT FALSE,
contract_id BIGINT,
contract_no VARCHAR(2000),
company_id BIGINT,
company_code VARCHAR(2000),
company_name VARCHAR(2000),
company_resolution_source VARCHAR(2000),
mapping_status VARCHAR(2000),
project_name VARCHAR(2000) CHECK(project_name<>'bad'),
source_order_no VARCHAR(2000),
customer_code VARCHAR(2000),
customer_name VARCHAR(2000),
contract_amount DECIMAL(24,6),
delivered_amount DECIMAL(24,6),
collected_amount DECIMAL(24,6),
collected_ratio DECIMAL(24,6),
receivable_amount DECIMAL(24,6),
overdue_amount DECIMAL(24,6),
currency_name VARCHAR(2000),
market_code VARCHAR(2000),
market_name VARCHAR(2000),
department_id BIGINT,
department_code VARCHAR(2000),
department_name VARCHAR(2000),
industry_name VARCHAR(2000),
marketing_representative_name VARCHAR(2000),
source_batch_code VARCHAR(2000),
import_batch_no VARCHAR(2000),
project_code VARCHAR(2000),
system_source_key VARCHAR(2000),
system_code VARCHAR(2000),
system_name VARCHAR(2000),
industry_code VARCHAR(2000),
expend_source_key VARCHAR(2000),
expend_code VARCHAR(2000),
expend_name VARCHAR(2000),
marketing_representative_code VARCHAR(2000),
secondary_representative_code VARCHAR(2000),
original_division_values VARCHAR(2000),
source_effective_from TIMESTAMP(6),
source_effective_to TIMESTAMP(6),
contract_create_time TIMESTAMP(6),
latest_ship_time TIMESTAMP(6),
source_system VARCHAR(2000),
source_record_key VARCHAR(2000),
source_sync_time TIMESTAMP(6),
source_row_id BIGINT,
source_revision_hash VARCHAR(2000),
source_data_source VARCHAR(2000),
source_created_by VARCHAR(2000),
source_created_at TIMESTAMP(6),
source_updated_by VARCHAR(2000),
source_updated_at TIMESTAMP(6),
source_collected_money_ratio DOUBLE,UNIQUE(tenant_id,source_system,source_record_key))
""");
        jdbc.execute("CREATE TABLE com_contract(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(100),deleted BOOLEAN)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String path="mapper/sync/DirectReceivableMapper.xml";
        try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(DirectReceivableMapper.class);
        var users=mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class);
        when(users.getHistoricalIdentitiesByUsername(eq(1L),anySet())).thenReturn(List.of());
        task=new DppmsDirectReceivableTask(mapper,mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class),permissions,source,users,tasks,runs);
        var definition=DppmsDirectReceivableTemplate.create(9L);
        var taskRow=new SyncTaskDO().setId(12L).setAdapter(definition.adapter()).setSourceSystem("DPPMS").setVersion(0).setActiveRunId(13L).setDefinition(JsonUtils.toJsonString(definition));taskRow.setTenantId(1L);
        when(tasks.required(12L)).thenReturn(taskRow);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setPreview(true).setStatus("VALIDATING");run.setTenantId(1L);run.setCreator("7");
        when(runs.selectScoped(any())).thenReturn(run);when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    static Map<String,String> source() {
        var values=new LinkedHashMap<String,String>();ContractReceivableMigrationApi.ORIGINAL_COLUMNS.forEach(name->values.put(name,null));
        values.put("id","11");values.put("contract_num","C-01");values.put("dataSource","CRM");values.put("create_by","legacy");
        values.put("update_by","crmapp");values.put("create_time","2023-06-01T12:01:02.123");values.put("update_time","2023-07-02T09:08:07.654");
        values.put("contract_money_amount","123.45");values.put("delivered_money_amount","112.34");values.put("collected_money_amount","101.23");
        values.put("collected_money_ratio","81.999999");values.put("receivables_money_amount","22.22");values.put("over_due_money_amount","3.45");
        values.put("systemid_o","4");values.put("industry_name_o","旧行业");
        return values;
    }

    Row row(Map<String,String> fields,Long target){return new Row("sms_ofst_contract_head_sap",fields.get("id"),new LinkedHashMap<>(fields),target);}
    Batch batch(List<Row> rows,List<Binding> bindings){return new Batch("integration:1:12",rows,bindings,true,"RETAIN",false,"UPSERT",false);}
    Batch batch(Row... rows){return batch(List.of(rows),List.of());}
    void applying(){run.setPreview(false).setStatus("APPLYING");}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class);}
    Binding binding(Change change){return new Binding(change.object(),change.sourceKey(),change.targetId(),change.after());}
    @Test void previewPreservesFinancialFactsAndAuditsWithoutBusinessWrites() {
        var changes=tx.execute(status->task.preview(batch(row(source(),null))));assertEquals(0,count());
        var fields=(Map<?,?>)changes.getFirst().after().get("receivable");assertEquals(52,fields.size());assertEquals("81.999999",fields.get("collectedRatio"));
        assertEquals("legacy",fields.get("creator"));assertEquals("2023-06-01T12:01:02.123",fields.get("createTime"));
        applying();assertThrows(IllegalStateException.class,()->task.apply(batch(row(source(),null))));
        var saved=tx.execute(status->task.apply(batch(row(source(),null)))).getFirst();assertNotEquals(11L,saved.targetId());assertEquals(1,count());
        assertEquals("PENDING_COMPANY",jdbc.queryForObject("SELECT mapping_status FROM com_contract_receivable",String.class));
    }
    @Test void sourceChangesRequireTheExactBaselineAndKeepCurrentAssociations() {
        applying();var first=tx.execute(status->task.apply(batch(row(source(),null)))).getFirst();
        jdbc.update("UPDATE com_contract_receivable SET company_code='001',company_id=88,contract_id=77,mapping_status='MATCHED'");
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.apply(batch(List.of(row(source(),first.targetId())),List.of(binding(first))))));
        // A baseline from the actual formal row represents an explicitly verified prior receipt.
        var formal=DirectReceivablePersistence.entity(new ContractReceivableMigrationApi.Entry("11",source(),"legacy","crmapp",null,null),1L);
        formal.setId(first.targetId());formal.setCompanyCode("001");formal.setCompanyId(88L);formal.setContractId(77L);formal.setMappingStatus("MATCHED");
        var before=new LinkedHashMap<String,Object>();before.put("fingerprint",DirectReceivablePersistence.fingerprint(formal));
        var changed=source();changed.put("collected_money_amount","102.23");
        var updated=tx.execute(status->task.apply(batch(List.of(row(changed,first.targetId())),List.of(new Binding(first.object(),"11",first.targetId(),before))))).getFirst();
        assertEquals("UPDATED",updated.action());assertEquals("001",jdbc.queryForObject("SELECT company_code FROM com_contract_receivable",String.class));
        assertEquals(77L,jdbc.queryForObject("SELECT contract_id FROM com_contract_receivable",Long.class));
        var exact=jdbc.queryForMap("SELECT * FROM com_contract_receivable");
        assertEquals("UNCHANGED",tx.execute(status->task.apply(batch(List.of(row(changed,updated.targetId())),List.of(binding(updated))))).getFirst().action());
        assertEquals(exact,jdbc.queryForMap("SELECT * FROM com_contract_receivable"));
    }
    @Test void laterConstraintFailureRollsBackEarlierReceipt() {
        applying();var second=source();second.put("id","12");second.put("project_name","bad");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->task.apply(batch(row(source(),null),row(second,null)))));assertEquals(0,count());
    }
    @Test void nullContractAndInvalidAuthorizationNeverCreateFormalRows() {
        applying();var empty=source();empty.put("contract_num",null);assertEquals("SKIPPED",tx.execute(status->task.apply(batch(row(empty,null)))).getFirst().action());
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.apply(batch(row(source(),null)))));assertEquals(0,count());
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);run.setConfigVersion(9);
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.apply(batch(row(source(),null)))));
        var definition=DppmsDirectReceivableTemplate.create(9L);assertEquals("id",definition.sources().getFirst().sourceKey());assertNull(definition.sources().getFirst().updatedAt());
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(task)).validate(definition));
        assertEquals("DPPMS_CONTRACT_RECEIVABLE",DppmsContractReceivableSyncTemplate.create(9L).adapter());
    }
}
