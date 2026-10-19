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

class DirectProductTransactionTest {
    JdbcTemplate jdbc;TransactionTemplate tx;DppmsDirectProductTask task;
    final SyncTaskService tasks=mock(SyncTaskService.class);final SyncRunMapper runs=mock(SyncRunMapper.class);
    final PermissionApi permissions=mock(PermissionApi.class);SyncRunDO run;
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source=new DriverManagerDataSource("jdbc:h2:mem:p12-direct-"+UUID.randomUUID()+";MODE=MySQL;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);jdbc.execute("""
                CREATE TABLE ast_product_official_info(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,product_code VARCHAR(100),product_name VARCHAR(500),product_model VARCHAR(128) CHECK(product_model<>'bad'),
                  product_desc VARCHAR(500),technical_spec VARCHAR(1000),list_price DECIMAL(23,10),business_unit VARCHAR(100),
                  status VARCHAR(32),source_system VARCHAR(32),source_key VARCHAR(128),source_version VARCHAR(64),source_updated_at TIMESTAMP,
                  sync_status VARCHAR(32),synced_at TIMESTAMP,version BIGINT,deleted BOOLEAN DEFAULT FALSE,
                  creator VARCHAR(64),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(tenant_id,product_code))
                """);
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String path="mapper/sync/DirectProductMapper.xml";
        try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(DirectProductMapper.class);
        task=new DppmsDirectProductTask(mapper,permissions,source,tasks,runs);
        var definition=DppmsDirectProductTemplate.create(9L);
        var taskRow=new SyncTaskDO().setId(12L).setAdapter(definition.adapter()).setSourceSystem("DPPMS").setVersion(0).setActiveRunId(13L).setDefinition(JsonUtils.toJsonString(definition));taskRow.setTenantId(1L);
        when(tasks.required(12L)).thenReturn(taskRow);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setPreview(true).setStatus("VALIDATING");run.setTenantId(1L);run.setCreator("7");
        when(runs.selectScoped(any())).thenReturn(run);when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Row row(String nativeId,String code,String name,String model) {
        var fields=new LinkedHashMap<String,Object>();fields.put("id",nativeId);fields.put("item",code);fields.put("describe_",name);fields.put("itemname",model);
        return new Row("fb_items",code,fields,null);
    }
    Batch batch(Row... rows){return new Batch("integration:1:12",List.of(rows),List.of(),true,"RETAIN",false,"UPSERT",false);}
    void applying(){run.setPreview(false).setStatus("APPLYING");}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class);}
    @Test void previewUsesApprovedCodeWithNullNativeIdAndApplyRequiresTheRunnerTransaction() {
        var input=batch(row("101","CODE","原名称","原型号"));
        var changes=tx.execute(status->task.preview(input));assertNull(changes.getFirst().targetId());assertEquals("CODE",changes.getFirst().sourceKey());assertEquals(0,count());
        applying();assertThrows(IllegalStateException.class,()->task.apply(input));assertEquals(0,count());
        var applied=tx.execute(status->task.apply(input));assertNotEquals(101L,applied.getFirst().targetId());assertEquals(1,count());
        var saved=jdbc.queryForMap("SELECT * FROM ast_product_official_info");assertEquals("ACTIVE",saved.get("STATUS"));assertEquals("DPPMS",saved.get("SOURCE_SYSTEM"));
        assertNull(saved.get("SOURCE_VERSION"));assertNull(saved.get("SOURCE_UPDATED_AT"));assertEquals("7",saved.get("CREATOR"));
    }
    @Test void existingCrmFieldsAndAuditsAreNotTouchedEvenWhenLegacyLabelsDiffer() {
        jdbc.update("INSERT INTO ast_product_official_info(id,tenant_id,product_code,product_name,product_model,product_desc,technical_spec,list_price,business_unit,status,source_system,source_key,version,creator,updater) VALUES(10,1,'CODE','CRM名称','CRM型号','CRM描述','CRM规格',123.456,'BU','INACTIVE','CRM','crm-key',7,'99','88')");
        var before=jdbc.queryForMap("SELECT * FROM ast_product_official_info WHERE id=10");applying();
        var result=tx.execute(status->task.apply(batch(row("101","CODE","不同旧名称",null))));assertEquals("UNCHANGED",result.getFirst().action());assertEquals(10L,result.getFirst().targetId());
        assertEquals(before,jdbc.queryForMap("SELECT * FROM ast_product_official_info WHERE id=10"));
    }
    @Test void multipleNativeAliasesCreateOneCodeAndConflictingNewLabelsFailBeforeWriting() {
        applying();var aliases=tx.execute(status->task.apply(batch(row("101","CODE","名称","型号"),row("102"," CODE ","名称","型号"))));
        assertEquals(1,count());assertEquals(aliases.getFirst().targetId(),aliases.getLast().targetId());
        jdbc.update("DELETE FROM ast_product_official_info");
        assertThrows(IllegalStateException.class,()->tx.execute(status->task.apply(batch(row("101","CODE","名称一",null),row("102"," CODE ","名称二",null)))));
        assertEquals(0,count());
    }
    @Test void lateInsertFailureRollsBackEarlierProductAndTenantStateStaysSeparate() {
        applying();assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->task.apply(batch(row("101","AAA","名称","good"),row("102","ZZZ","名称","bad")))));
        assertEquals(0,count());
        jdbc.update("INSERT INTO ast_product_official_info(id,tenant_id,product_code,product_name,version,deleted) VALUES(10,2,'CODE','其他租户',0,FALSE)");
        tx.executeWithoutResult(status->task.apply(batch(row("101","CODE","名称",null))));assertEquals(2,count());
    }
    @Test void staleRunDeletedCaseConflictsAndBusinessCodeAsSourceKeyRemainBlocked() {
        run.setConfigVersion(1);assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.preview(batch(row("101","CODE","名称",null)))));run.setConfigVersion(0);
        var original=row("101","CODE","名称",null);var wrong=new Row(original.object(),"WRONG",original.fields(),null);
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.preview(batch(wrong))));
        jdbc.update("INSERT INTO ast_product_official_info(id,tenant_id,product_code,product_name,version,deleted) VALUES(10,1,'CODE','CRM名称',0,TRUE)");
        assertThrows(IllegalStateException.class,()->tx.execute(status->task.preview(batch(original))));
        jdbc.update("UPDATE ast_product_official_info SET deleted=FALSE");
        assertThrows(IllegalStateException.class,()->tx.execute(status->task.preview(batch(row("101","code","名称",null)))));
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(task)).validate(DppmsDirectProductTemplate.create(9L)));
        assertEquals("item",DppmsProductCatalogSyncTemplate.create(new DppmsProductCatalogSyncTemplate.Request(9L,List.of("CODE"))).sources().getFirst().sourceKey());
    }
}
