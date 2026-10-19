package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataCommand;
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
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectCustomerTransactionTest {
    JdbcTemplate jdbc;DirectCustomerMapper mapper;DirectCustomerPersistence persistence;TransactionTemplate tx;
    static final LocalDateTime TIME=LocalDateTime.of(2026,10,16,0,0);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source=new DriverManagerDataSource("jdbc:h2:mem:p10-direct-"+UUID.randomUUID()+";MODE=MySQL;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE cus_customer_master(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,version BIGINT DEFAULT 0,deleted BOOLEAN DEFAULT FALSE,
                  code VARCHAR(64),erp_customer_code VARCHAR(50),name VARCHAR(128),short_name VARCHAR(64),
                  customer_level VARCHAR(64),lifecycle_status VARCHAR(16),source_type VARCHAR(32),source_key VARCHAR(128),source_version VARCHAR(64),
                  sync_status VARCHAR(32),data_as_of TIMESTAMP,reconciliation_pending BOOLEAN,temporary_reason VARCHAR(500),
                  contact_phone VARCHAR(64),contact_email VARCHAR(128),department_code VARCHAR(64),department_name VARCHAR(128),
                  market_code VARCHAR(64),market_name VARCHAR(128),system_code VARCHAR(64),system_name VARCHAR(128),
                  expend_code VARCHAR(64),expend_name VARCHAR(128),industry_code VARCHAR(64),industry_name VARCHAR(128),
                  address VARCHAR(500),remark VARCHAR(500),creator VARCHAR(64) DEFAULT '',create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updater VARCHAR(64) DEFAULT '',update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(tenant_id,code))
                """);
        jdbc.execute("""
                CREATE TABLE cus_customer_external_mapping(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT,source_system VARCHAR(32),source_key VARCHAR(128) CHECK(source_key<>'ROLLBACK'),
                  source_version VARCHAR(64),effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,tenant_id BIGINT,
                  current_marker INT GENERATED ALWAYS AS (CASE WHEN effective_to IS NULL AND deleted=FALSE THEN 1 ELSE NULL END),
                  creator VARCHAR(64) DEFAULT '',create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updater VARCHAR(64) DEFAULT '',update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(tenant_id,source_system,source_key,current_marker))
                """);
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String path="mapper/sync/DirectCustomerMapper.xml";
        try(var in=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(in,configuration,path,configuration.getSqlFragments()).parse();}
        mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(DirectCustomerMapper.class);
        persistence=new DirectCustomerPersistence(mapper,source);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    static CustomerMasterDataCommand command(Long target,String source,String erp,String version) {
        return new CustomerMasterDataCommand(1L,target,"CRM-1",erp,"客户","简称",null,null,null,
                null,null,null,null,null,null,null,null,null,null,"ENABLED",source,version,TIME,"operation-"+version,null);
    }
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    @Test void realSqlPreviewDoesNotWriteAndMissingErpSchemaCannotClaimReady() {
        var preview=persistence.preview(command(null,"SOURCE-1","ERP-1","2026-10-15"));
        assertNull(preview.customerId());assertEquals(0,count("cus_customer_master"));assertEquals(0,count("cus_customer_external_mapping"));
        assertThrows(IllegalStateException.class,()->persistence.apply(command(null,"SOURCE-1","ERP-1","2026-10-15")));
        jdbc.execute("ALTER TABLE cus_customer_master DROP COLUMN erp_customer_code");
        assertThrows(org.springframework.jdbc.BadSqlGrammarException.class,()->persistence.preview(command(null,"SOURCE-1","ERP-1","2026-10-15")));
    }
    @Test void sourceVersionsProtectErpCodeAndNewerSourceCanClearItWithoutPlatformFieldChanges() {
        var first=tx.execute(status->persistence.apply(command(null,"SOURCE-1","ERP-1","2026-10-15")));
        Long id=first.customerId();assertNotNull(id);jdbc.update("UPDATE cus_customer_master SET customer_level='A级',remark='平台维护' WHERE id=?",id);
        assertTrue(persistence.preview(command(id,"SOURCE-1","ERP-1","2026-10-15")).replayed());
        assertThrows(IllegalStateException.class,()->persistence.preview(command(id,"SOURCE-1","ERP-2","2026-10-15")));
        assertTrue(tx.execute(status->persistence.apply(command(id,"SOURCE-1","ERP-2","2026-10-14"))).replayed());
        assertEquals("ERP-1",jdbc.queryForObject("SELECT erp_customer_code FROM cus_customer_master WHERE id=?",String.class,id));
        tx.executeWithoutResult(status->persistence.apply(command(id,"SOURCE-1",null,"2026-10-16")));
        assertNull(jdbc.queryForObject("SELECT erp_customer_code FROM cus_customer_master WHERE id=?",String.class,id));
        assertEquals("A级",jdbc.queryForObject("SELECT customer_level FROM cus_customer_master WHERE id=?",String.class,id));
        assertEquals("平台维护",jdbc.queryForObject("SELECT remark FROM cus_customer_master WHERE id=?",String.class,id));
        assertEquals(1L,jdbc.queryForObject("SELECT version FROM cus_customer_master WHERE id=?",Long.class,id));
        assertTrue(persistence.preview(command(id,"SOURCE-1",null,"2026-10-16")).replayed());
    }
    @Test void mappingInsertFailureRollsBackCustomerAndMappingTogether() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->persistence.apply(command(null,"ROLLBACK","ERP-1","2026-10-15"))));
        assertEquals(0,count("cus_customer_master"));assertEquals(0,count("cus_customer_external_mapping"));
    }
    @Test void existingCodesDeletedRowsTenantAndWrongBindingsCannotBeAdopted() {
        jdbc.update("INSERT INTO cus_customer_master(id,tenant_id,code,name,version,deleted) VALUES(1,1,'CRM-1','已有客户',0,TRUE)");
        assertThrows(IllegalStateException.class,()->persistence.preview(command(null,"SOURCE-1","ERP-1","2026-10-15")));
        assertNull(mapper.selectCodeForUpdate(new DirectCustomerMapper.CodeQuery(2L,"CRM-1")));
        assertNull(mapper.selectCustomerForUpdate(new DirectCustomerMapper.CustomerQuery(2L,1L)));
        jdbc.update("DELETE FROM cus_customer_master");
        assertThrows(IllegalStateException.class,()->persistence.preview(command(99L,"SOURCE-1","ERP-1","2026-10-15")));
        var first=tx.execute(status->persistence.apply(command(null,"SOURCE-1","ERP-1","2026-10-15")));
        assertThrows(IllegalStateException.class,()->persistence.preview(command(first.customerId()+1,"SOURCE-1","ERP-1","2026-10-15")));
        assertEquals(0,mapper.updateMapping(new DirectCustomerMapper.MappingWrite(2L,1L,"2026-10-15","2026-10-16")));
    }
    @Test void customerAndSourceOptimisticVersionsBothRemainMandatory() {
        var first=tx.execute(status->persistence.apply(command(null,"SOURCE-1","ERP-1","2026-10-15")));
        var customer=mapper.selectCustomerForUpdate(new DirectCustomerMapper.CustomerQuery(1L,first.customerId()));customer.setName("更改");
        assertEquals(0,mapper.updateCustomer(new DirectCustomerMapper.CustomerWrite(customer,9L)));
        var mapping=mapper.selectCurrentMapping(new DirectCustomerMapper.SourceQuery(1L,"SOURCE-1"));
        assertEquals(0,mapper.updateMapping(new DirectCustomerMapper.MappingWrite(1L,mapping.getId(),"old-version","2026-10-16")));
    }
}
