package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentContractWarrantyCorrectionApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ShipmentContractWarrantyCorrectionMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query.ShipmentContractWarrantyCorrectionQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Expression;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL, including the record result mapping and year DDL; fixtures are temporary only. */
@EnabledIfSystemProperty(named="npdms.contract-years.mysql",matches="true")
class ShipmentContractWarrantyCorrectionMySqlTest {
    Connection db; SqlSession session; ShipmentContractWarrantyCorrectionMapper mapper;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));
        assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        db.setAutoCommit(false); TenantContextHolder.setTenantId(1L);
        try(var sql=db.createStatement()) {
            String ddl;
            try(var schema=sql.executeQuery("SHOW CREATE TABLE com_shipment_contract_reference")) {
                assertTrue(schema.next());ddl=schema.getString(2);
            }
            assertTrue(ddl.startsWith("CREATE TABLE `com_shipment_contract_reference`"));
            // MySQL temporary tables cannot define FKs; all production columns/indices are retained.
            ddl=ddl.lines().filter(line -> !line.contains(" FOREIGN KEY "))
                    .collect(java.util.stream.Collectors.joining("\n")).replaceAll(",\\n\\) ENGINE", "\n) ENGINE");
            sql.execute(ddl.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
            boolean old=false;
            try(var cols=sql.executeQuery("SHOW COLUMNS FROM com_shipment_contract_reference")) {
                while(cols.next())if("warranty_flag".equals(cols.getString(1)))old=true;
            }
            if(old)sql.execute("ALTER TABLE com_shipment_contract_reference RENAME COLUMN warranty_flag TO warranty_years");
            sql.execute("ALTER TABLE com_shipment_contract_reference MODIFY warranty_years VARCHAR(8) NULL");
            sql.execute("INSERT INTO com_shipment_contract_reference(id,tenant_id,source_system,source_record_key,mapping_status,warranty_years,creator,create_time,updater,update_time,deleted) VALUES(2,1,'DPPMS','C','PENDING_MAPPING','  ','original','2017-01-02 03:04:05.123','original','2018-02-03 04:05:06.456',0)");
        }
        var config=new Configuration(new Environment("temp",new JdbcTransactionFactory(),new SingleConnectionDataSource(db,true)));
        var plugins=new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId(){return new LongValue(TenantContextHolder.getRequiredTenantId());}
        }));
        config.addInterceptor(plugins);
        String resource="mapper/migration/ShipmentContractWarrantyCorrectionMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();
        }
        session=new SqlSessionFactoryBuilder().build(config).openSession(db); mapper=session.getMapper(ShipmentContractWarrantyCorrectionMapper.class);
    }
    @AfterEach void cleanup() throws Exception {
        TenantContextHolder.clear(); if(db!=null){try{db.rollback();}finally{if(session!=null)session.close();db.close();}}
    }
    ShipmentContractWarrantyCorrectionQuery query(){return new ShipmentContractWarrantyCorrectionQuery(1L,2L,"C");}
    @Test void realMapperNormalizesWithoutChangingAuditsAndReplaysAfterIntegerDdl() throws Exception {
        var before=mapper.selectCurrent(query()); assertEquals("  ",before.warrantyYears()); assertEquals(64,before.fingerprint().length());
        var request=new Request(1L,"integration:1:100",List.of(new Entry(2L,"C",before.fingerprint(),before.normalizedFingerprint())));
        var owner=new ShipmentContractWarrantyCorrectionService(mapper);
        assertEquals("UPDATED",owner.apply(request).getFirst().action());session.clearCache();
        var after=mapper.selectCurrent(query());assertNull(after.warrantyYears());assertEquals(before.normalizedFingerprint(),after.fingerprint());
        try(var sql=db.createStatement()) {
            sql.execute("ALTER TABLE com_shipment_contract_reference MODIFY warranty_years INT NULL");
            try(var row=sql.executeQuery("SELECT creator,create_time,updater,update_time FROM com_shipment_contract_reference WHERE id=2")) {
                assertTrue(row.next());assertEquals("original",row.getString(1));assertEquals("original",row.getString(3));
                assertEquals("2017-01-02 03:04:05.123",row.getString(2));assertEquals("2018-02-03 04:05:06.456",row.getString(4));
            }
        }
        session.clearCache();assertEquals("UNCHANGED",owner.apply(request).getFirst().action());
    }
    @Test void completeFingerprintBlocksLaterBusinessAndAuditChanges() throws Exception {
        var before=mapper.selectCurrent(query());
        var request=new Request(1L,"integration:1:100",List.of(new Entry(2L,"C",before.fingerprint(),before.normalizedFingerprint())));
        try(var sql=db.createStatement()){sql.execute("UPDATE com_shipment_contract_reference SET remark='manual change',updater='human' WHERE id=2");}
        session.clearCache();assertThrows(IllegalArgumentException.class,()->new ShipmentContractWarrantyCorrectionService(mapper).apply(request));
        assertEquals("  ",mapper.selectCurrent(query()).warrantyYears());
    }
    @Test void tenantSourceKeyAndDeletedProtectionCannotBeBypassed() throws Exception {
        assertNull(mapper.selectCurrent(new ShipmentContractWarrantyCorrectionQuery(2L,2L,"C")));
        assertNull(mapper.selectCurrent(new ShipmentContractWarrantyCorrectionQuery(1L,2L,"OTHER")));
        var before=mapper.selectCurrent(query());
        var request=new Request(1L,"integration:1:100",List.of(new Entry(2L,"C",before.fingerprint(),before.normalizedFingerprint())));
        try(var sql=db.createStatement()){sql.execute("UPDATE com_shipment_contract_reference SET deleted=1 WHERE id=2");}
        session.clearCache();assertThrows(IllegalArgumentException.class,()->new ShipmentContractWarrantyCorrectionService(mapper).apply(request));
    }
}
