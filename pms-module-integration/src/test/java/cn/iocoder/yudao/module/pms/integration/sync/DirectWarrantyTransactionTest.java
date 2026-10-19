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

import cn.iocoder.yudao.module.pms.asset.api.migration.DeviceWarrantyRenewalMigrationApi.*;
class DirectWarrantyTransactionTest {
    JdbcTemplate jdbc;TransactionTemplate tx;DirectWarrantyPersistence writer;
    final PermissionApi permissions=mock(PermissionApi.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source=new DriverManagerDataSource("jdbc:h2:mem:p25-direct-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);jdbc.execute("""
CREATE TABLE ast_device_warranty_renewal_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,device_sn VARCHAR(100),warranty_contract_no VARCHAR(64),warranty_grade VARCHAR(32) CHECK(warranty_grade<>'bad'),warranty_start_date DATE,warranty_end_date DATE,warranty_months INTEGER,extended BOOLEAN,is_latest BOOLEAN,remark VARCHAR(1000),version INTEGER,creator VARCHAR(64),create_time TIMESTAMP(6),updater VARCHAR(64),update_time TIMESTAMP(6),deleted BOOLEAN)
""");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String path="mapper/sync/DirectWarrantyMapper.xml";
        try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(DirectWarrantyMapper.class);
        writer=new DirectWarrantyPersistence(mapper,mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class),permissions,source);
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Source source(String sn,String years,String grade){return new Source("same-legacy-id","RENEW-01",sn,grade,"2020-01-01 00:00:00","2022-04-17 00:00:00",years,"原备注","1",null);}
    Entry entry(Source source){return new Entry(source.sourceKey(),source,null,null);}
    Request request(Entry... entries){return new Request(1L,7L,"integration:1:12",List.of(entries));}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_warranty_renewal_record",Integer.class);}
    @Test void previewIsReadOnlyAndDuplicateRawIdsWithDifferentSerialsStayDistinct() {
        var input=request(entry(source("SN1","1","G")),entry(source("SN2","2","G")));
        var preview=tx.execute(status->writer.preview(input));assertEquals(0,count());assertNull(preview.getFirst().targetId());
        assertEquals(12,preview.getFirst().fields().get("warrantyMonths"));assertEquals("2022-04-17",preview.getFirst().fields().get("warrantyEndDate"));
        assertThrows(IllegalStateException.class,()->writer.apply(input));
        var saved=tx.execute(status->writer.apply(input));assertEquals(2,count());assertNotEquals(saved.getFirst().targetId(),saved.getLast().targetId());
        assertEquals(tools.jackson.databind.node.NullNode.getInstance(),saved.getFirst().fields().get("isLatest"));
    }
    @Test void unchangedReplayAndVersionedUpdateKeepCreationAuditAndRejectManualEdit() {
        var initial=source("SN1","1","G");var first=tx.execute(status->writer.apply(request(entry(initial)))).getFirst();
        var before=jdbc.queryForMap("SELECT * FROM ast_device_warranty_renewal_record");
        var repeated=new Entry(initial.sourceKey(),initial,first.targetId(),first.fingerprint());assertEquals("UNCHANGED",tx.execute(status->writer.apply(request(repeated))).getFirst().action());assertEquals(before,jdbc.queryForMap("SELECT * FROM ast_device_warranty_renewal_record"));
        var next=source("SN1","2","G");var updated=tx.execute(status->writer.apply(request(new Entry(next.sourceKey(),next,first.targetId(),first.fingerprint())))).getFirst();
        assertEquals("UPDATED",updated.action());assertEquals(24,updated.fields().get("warrantyMonths"));assertEquals(1,updated.fields().get("version"));
        assertEquals(before.get("CREATE_TIME"),jdbc.queryForMap("SELECT * FROM ast_device_warranty_renewal_record").get("CREATE_TIME"));
        jdbc.update("UPDATE ast_device_warranty_renewal_record SET remark='人工变更'");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(request(new Entry(next.sourceKey(),next,updated.targetId(),updated.fingerprint())))));
    }
    @Test void laterConstraintFailureRollsBackEarlierRecord() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->writer.apply(request(entry(source("SN1","1","G")),entry(source("SN2","1","bad"))))));assertEquals(0,count());
    }
    @Test void permissionAndTenantRemainRequiredAndApprovedCompositeSourceIsPreserved() {
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(false);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(request(entry(source("SN1","1","G"))))));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(new Request(2L,7L,"integration:2:12",List.of()))));assertEquals(0,count());
        var definition=DppmsDirectWarrantyTemplate.create(9L);assertEquals("renewalSourceKey",definition.sources().getFirst().sourceKey());
        assertEquals("DPPMS_WARRANTY_RENEWAL_DIRECT",definition.adapter());assertEquals("DPPMS_WARRANTY_RENEWAL",DppmsWarrantyRenewalSyncTemplate.create(9L).adapter());
        var adapter=new DppmsDirectWarrantyTask(mock(DirectWarrantyMapper.class),mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class),permissions,new DriverManagerDataSource(),mock(SyncTaskService.class),mock(SyncRunMapper.class));
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
    }
}
