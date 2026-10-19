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

class DirectExecutionOrderTransactionTest {
    JdbcTemplate jdbc;TransactionTemplate tx;DppmsDirectExecutionOrderTask task;SyncRunDO run;
    final SyncTaskService tasks=mock(SyncTaskService.class);final SyncRunMapper runs=mock(SyncRunMapper.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source=new DriverManagerDataSource("jdbc:h2:mem:p13-direct-"+UUID.randomUUID()+";MODE=MySQL;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);jdbc.execute("""
CREATE TABLE com_crm_execution_order(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,version BIGINT,
creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,
source_system VARCHAR(500),
execution_no VARCHAR(500),
project_code VARCHAR(500),
project_name VARCHAR(500) CHECK(project_name<>'bad'),
primary_project_id BIGINT,
sales_rep_code VARCHAR(500),
sales_rep_name VARCHAR(500),
sales_rep_phone VARCHAR(500),
market_code VARCHAR(500),
market_name VARCHAR(500),
system_source_key VARCHAR(500),
system_code VARCHAR(500),
system_name VARCHAR(500),
expend_source_key VARCHAR(500),
expend_code VARCHAR(500),
expend_name VARCHAR(500),
industry_code VARCHAR(500),
industry_name VARCHAR(500),
department_id BIGINT,
department_code VARCHAR(500),
department_name VARCHAR(500),
service_type_name VARCHAR(500),
channel_name VARCHAR(500),
engineering_fee_raw VARCHAR(500),
engineering_fee DECIMAL(20,4),
source_object_id VARCHAR(500),
apply_type VARCHAR(500),
company_id BIGINT,
company_code VARCHAR(500),
company_name VARCHAR(500),
customer_project_name VARCHAR(500),
final_customer_name VARCHAR(500),
agent_name VARCHAR(500),
project_manager_code VARCHAR(500),
project_manager_name VARCHAR(500),
decision_path VARCHAR(500),
required_in_date DATE,
receiver_name VARCHAR(500),
receiver_contact VARCHAR(500),
receiver_address VARCHAR(500),
loan_reason VARCHAR(500),
project_type VARCHAR(500),
major_project_level VARCHAR(500),
project_amount DECIMAL(20,4),
af_project_amount DECIMAL(20,4),
submit_time TIMESTAMP,
predicted_bid_time TIMESTAMP,
contact_name VARCHAR(500),
contact_phone VARCHAR(500),
af_evidence_status VARCHAR(500),
source_sync_time TIMESTAMP,
status VARCHAR(500),UNIQUE(tenant_id,source_system,execution_no))
""");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String path="mapper/sync/DirectExecutionOrderMapper.xml";
        try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(DirectExecutionOrderMapper.class);
        var organizations=mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class);
        when(organizations.list()).thenReturn(List.of());
        task=new DppmsDirectExecutionOrderTask(mapper,organizations,tasks,runs,permissions,source);
        var definition=DppmsDirectExecutionOrderTemplate.create(9L);
        var taskRow=new SyncTaskDO().setId(12L).setAdapter(definition.adapter()).setSourceSystem("DPPMS").setVersion(0).setActiveRunId(13L);taskRow.setTenantId(1L);
        when(tasks.required(12L)).thenReturn(taskRow);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setPreview(true).setStatus("VALIDATING");run.setTenantId(1L);run.setCreator("7");
        when(runs.selectScoped(any())).thenReturn(run);when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Row row(String id,String number,String name) {
        var fields=new LinkedHashMap<String,Object>();fields.put("sourceSystem","CRM");fields.put("executionNo",number);fields.put("projectName",name);
        fields.put("canonicalSourceId",id);
        for(var field:task.descriptor().objects().getFirst().fields())if(field.name().startsWith("canonical_"))
            fields.put(field.name(),fields.get(field.name().substring(10)));
        return new Row("EXECUTION_ORDER",id,fields,null);
    }
    Batch batch(Row... rows){return new Batch("integration:1:12",List.of(rows),List.of(),true,"RETAIN",false,"UPSERT",false);}
    void applying(){run.setPreview(false).setStatus("APPLYING");}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM com_crm_execution_order",Integer.class);}
    @Test void previewIsReadOnlyAndNativeAliasesShareOneFormalHeader() {
        var input=batch(row("101","EX-1","名称"),row("102","EX-1","名称"));
        var preview=tx.execute(status->task.preview(input));assertEquals(0,count());assertEquals("101",preview.getFirst().sourceKey());
        applying();assertThrows(IllegalStateException.class,()->task.apply(input));
        var applied=tx.execute(status->task.apply(input));assertEquals(1,count());assertEquals(applied.getFirst().targetId(),applied.getLast().targetId());
        assertNotEquals(101L,applied.getFirst().targetId());assertEquals("7",jdbc.queryForObject("SELECT creator FROM com_crm_execution_order",String.class));
    }
    @Test void updatesOnlyReviewedHeaderFieldsAndPreservesLifecycleAndOtherConsumers() {
        jdbc.update("INSERT INTO com_crm_execution_order(id,tenant_id,source_system,execution_no,project_name,status,af_evidence_status,primary_project_id,project_manager_name,sales_rep_phone,af_project_amount,version,creator) VALUES(20,1,'CRM','EX-1','原名','CLOSED','ACCEPTED',88,'后续经理','123',123.45,4,'99')");
        applying();var result=tx.execute(status->task.apply(batch(row("101","EX-1","新名"))));assertEquals("UPDATED",result.getFirst().action());
        var saved=jdbc.queryForMap("SELECT * FROM com_crm_execution_order");assertEquals("新名",saved.get("PROJECT_NAME"));assertEquals("CLOSED",saved.get("STATUS"));
        assertEquals("ACCEPTED",saved.get("AF_EVIDENCE_STATUS"));assertEquals(88L,saved.get("PRIMARY_PROJECT_ID"));assertEquals("后续经理",saved.get("PROJECT_MANAGER_NAME"));
        assertEquals("123",saved.get("SALES_REP_PHONE"));assertEquals("99",saved.get("CREATOR"));assertEquals(5L,saved.get("VERSION"));
        var before=jdbc.queryForMap("SELECT * FROM com_crm_execution_order");
        assertEquals("UNCHANGED",tx.execute(status->task.apply(batch(row("101","EX-1","新名")))).getFirst().action());assertEquals(before,jdbc.queryForMap("SELECT * FROM com_crm_execution_order"));
    }
    @Test void secondInsertConstraintFailureRollsBackTheFirst() {
        applying();assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->task.apply(batch(row("101","EX-1","正常"),row("102","EX-2","bad")))));assertEquals(0,count());
    }
    @Test void invalidChunkAndDeletedOrCaseMismatchedIdentityNeverWrite() {
        applying();var bad=new LinkedHashMap<>(row("102","EX-2","错误").fields());bad.put("projectAmount","bad");bad.put("canonical_projectAmount","bad");
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.apply(batch(row("101","EX-1","正常"),new Row("EXECUTION_ORDER","102",bad,null)))));assertEquals(0,count());
        jdbc.update("INSERT INTO com_crm_execution_order(id,tenant_id,source_system,execution_no,version,deleted) VALUES(20,1,'CRM','EX-1',0,TRUE)");
        assertEquals("CONFLICT",tx.execute(status->task.apply(batch(row("101","EX-1","名称")))).getFirst().action());
        jdbc.update("UPDATE com_crm_execution_order SET deleted=FALSE");
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.apply(batch(row("101","ex-1","名称")))));
    }
    @Test void staleRunAndPermissionFailBeforeQueryAndReviewedTemplateUsesNativeKey() {
        run.setConfigVersion(9);assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.preview(batch(row("101","EX-1","名称")))));run.setConfigVersion(0);
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.preview(batch(row("101","EX-1","名称")))));
        var definition=DppmsDirectExecutionOrderTemplate.create(9L);assertEquals("id",definition.sources().getFirst().sourceKey());
        assertEquals("STREAMING_CURSOR",definition.readStrategy());assertTrue(definition.sources().getFirst().sql().contains("canonicalId"));
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(task)).validate(definition));
        assertEquals("DPPMS_CRM_EXECUTION_ORDER",DppmsExecutionOrderSyncTemplate.create(9L).adapter());
    }
}
