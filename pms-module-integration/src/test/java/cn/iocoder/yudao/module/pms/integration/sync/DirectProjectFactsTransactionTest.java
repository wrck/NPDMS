package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectFactsMigrationApi.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
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

class DirectProjectFactsTransactionTest {
    JdbcTemplate jdbc;TransactionTemplate tx;DirectProjectFactsPersistence writer;DirectProjectPartyMapper mapper;
    final ProjectMigrationIdentityContextApi projects=mock(ProjectMigrationIdentityContextApi.class);
    final EntityExtensionApi extensions=mock(EntityExtensionApi.class);
    final ProjectMigrationIdentityContextApi.Query identity=new ProjectMigrationIdentityContextApi.Query(1L,7L,19L,3L,"8",9L);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source=new DriverManagerDataSource("jdbc:h2:mem:p19-direct-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);jdbc.execute("""
CREATE TABLE proj_project_party(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,party_role VARCHAR(20),party_code VARCHAR(50),party_name VARCHAR(100) CHECK(party_name<>'bad'),contact_name VARCHAR(100),phone VARCHAR(50),source_system VARCHAR(20),source_table VARCHAR(64),source_record_key VARCHAR(128),effective_from TIMESTAMP,effective_to TIMESTAMP,status VARCHAR(20),version INTEGER,creator VARCHAR(64),create_time TIMESTAMP(6),updater VARCHAR(64),update_time TIMESTAMP(6),deleted BOOLEAN)
""");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String path="mapper/sync/DirectProjectPartyMapper.xml";
        try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(DirectProjectPartyMapper.class);
        writer=new DirectProjectFactsPersistence(source,projects,mapper,extensions);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Entry party(String key,String name,Long target,String fingerprint) {
        var fields=DirectProjectFactsPlanTest.party();fields.put("id",key);fields.put("partyName",name);
        return new Entry("pm_project_related_party",key,fields,null,null,target,fingerprint);
    }
    Entry state(){return new Entry("pm_project_state","8",DirectProjectFactsPlanTest.states(),null,null,null,null);}
    Command command(Entry... entries){return new Command(identity,21L,List.of(entries));}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_party",Integer.class);}
    @Test void nativePartyPreviewIsReadOnlyAndHistoricalNullAuditSurvivesRoundTrip() {
        var input=command(party("11","服务渠道",null,null));
        assertEquals("CREATED",tx.execute(status->writer.preview(input)).getFirst().action());assertEquals(0,count());
        assertThrows(IllegalStateException.class,()->writer.apply(input));
        var saved=tx.execute(status->writer.apply(input)).getFirst();assertNotEquals(11L,saved.targetId());
        assertNull(jdbc.queryForObject("SELECT creator FROM proj_project_party",String.class));
        assertEquals("UNCHANGED",tx.execute(status->writer.apply(command(party("11","服务渠道",saved.targetId(),saved.fingerprint())))).getFirst().action());
        verify(projects,times(2)).lockAndRevalidate(identity);assertEquals(1,count());
    }
    @Test void existingContactAndPhoneStayAndManualChangesBlockReplay() {
        var saved=tx.execute(status->writer.apply(command(party("11","服务渠道",null,null)))).getFirst();
        jdbc.update("UPDATE proj_project_party SET contact_name='联系人',phone='123'");
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(party("11","新名称",saved.targetId(),saved.fingerprint())))));
        var old=tx.execute(status->mapper.selectMigrationSourceForUpdate(new DirectProjectPartyMapper.MigrationSourceQuery(1L,"pm_project_related_party","11"))).getFirst();
        var baseline=DirectProjectFactsPersistence.fingerprint(DirectProjectFactsPersistence.fields(old));
        assertEquals("UPDATED",tx.execute(status->writer.apply(command(party("11","新名称",saved.targetId(),baseline)))).getFirst().action());
        assertEquals("联系人",jdbc.queryForObject("SELECT contact_name FROM proj_project_party",String.class));assertEquals("123",jdbc.queryForObject("SELECT phone FROM proj_project_party",String.class));
    }
    @Test void laterSqlAndPlatformExtensionFailuresRollBackEarlierParty() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->writer.apply(command(party("11","服务渠道",null,null),party("12","bad",null,null)))));assertEquals(0,count());
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(21L,Map.of("existing_attribute","keep"),1));
        when(extensions.save(any())).thenThrow(new IllegalStateException("platform rejects stale extension"));
        assertThrows(IllegalStateException.class,()->tx.execute(status->writer.apply(command(party("11","服务渠道",null,null),state()))));assertEquals(0,count());
    }
    @Test void statusUsesOnlyFourApprovedFieldsAndPreservesPlatformVersionAndUnrelatedValues() {
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(21L,Map.of("existing_attribute","keep"),4));
        tx.execute(status->writer.preview(command(state())));verify(extensions,never()).save(any());
        tx.execute(status->writer.apply(command(state())));
        var capture=org.mockito.ArgumentCaptor.forClass(EntityExtensionApi.Save.class);verify(extensions).save(capture.capture());
        assertEquals(4,capture.getValue().expectedValueVersion());assertEquals(3L,capture.getValue().expectedEntityVersion());assertEquals(21L,capture.getValue().definitionRevisionId());
        assertEquals(Set.of("existing_attribute","shipment_status","shipment_status_updated_at","execution_status","execution_status_updated_at"),capture.getValue().fields().keySet());
        assertEquals("keep",capture.getValue().fields().get("existing_attribute"));
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(22L,Map.of(),1));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(state()))));
    }
    @Test void projectIdentityFailureAndIndependentDefinitionRemainProtected() {
        doThrow(new IllegalArgumentException("forbidden or stale")).when(projects).lockAndRevalidate(identity);
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(party("11","服务渠道",null,null)))));assertEquals(0,count());
        var task=new DppmsDirectProjectFactsTask(mapper,new DriverManagerDataSource(),projects,extensions,
            mock(cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi.class),mock(ProjectMasterMigrationApi.class),mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class),mock(SyncTaskService.class),mock(SyncRunMapper.class));
        var definition=DppmsDirectProjectFactsTemplate.create(9L);assertEquals("DPPMS_PROJECT_FACTS_DIRECT",definition.adapter());
        assertEquals(List.of("id","projectId"),definition.sources().stream().map(SyncDefinition.Source::sourceKey).toList());
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(task)).validate(definition));
    }
}
