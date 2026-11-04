package cn.iocoder.yudao.module.pms.asset.service.license;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.license.DeviceLicenseRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.license.DeviceLicenseRecordMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Approved DDL and real tenant/audit handlers, on one connection-local table only. */
@EnabledIfSystemProperty(named="npdms.license.mysql",matches="true")
class DeviceLicenseRecordMySqlTest {
    Connection db;SingleConnectionDataSource data;TransactionTemplate tx;
    DeviceLicenseRecordMapper mapper;DppmsLicenseRecordService service;DppmsLicenseRecordServiceTest fixture;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        data=new SingleConnectionDataSource(db,true);
        String ddl=Files.readString(Path.of(System.getProperty("npdms.license.sourceRoot"),"SQL/migrations/V433__approved_device_license_record.sql"));
        try(var sql=db.createStatement()){sql.execute(ddl.replace("CREATE TABLE ast_device_license_record","CREATE TEMPORARY TABLE ast_device_license_record"));}
        try(var sql=db.createStatement()){sql.execute("ALTER TABLE ast_device_license_record ADD COLUMN license_code VARCHAR(256) NULL");}
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();factory.setDataSource(data);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        var plugins=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));factory.setPlugins(plugins);
        var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());factory.setGlobalConfig(global);
        factory.setMapperLocations(new ClassPathResource("mapper/license/DeviceLicenseRecordMapper.xml"));
        mapper=spy(new SqlSessionTemplate(factory.getObject()).getMapper(DeviceLicenseRecordMapper.class));
        tx=new TransactionTemplate(new DataSourceTransactionManager(data));
        fixture=new DppmsLicenseRecordServiceTest();fixture.setup();
        service=new DppmsLicenseRecordService(mapper,fixture.devices,fixture.cipher,fixture.projects,fixture.scopes,fixture.commands,fixture.evidence);
        var login=new cn.iocoder.yudao.framework.security.core.LoginUser();login.setId(7L);login.setTenantId(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(login,null));
    }
    @AfterEach void close() throws Exception {
        TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();
        if(data!=null)data.destroy();else if(db!=null)db.close();
    }
    @Test void exactSixteenColumnDdlCarriesNullsUnicodeAuditAndAuthenticatedCipher() throws Exception {
        var result=tx.execute(status->service.apply(fixture.command));Long id=result.getFirst().id();
        var row=tx.execute(status->mapper.selectRecordForUpdate(new DeviceLicenseRecordMapper.RecordQuery(1L,id)));
        assertNull(row.getDeviceId());assertNull(row.getDeviceSn());assertNull(row.getContractId());
        assertEquals("  MODEL  ",row.getProductModel());assertEquals("原状态",row.getLicenseStatus());
        assertEquals("7",row.getCreator());assertEquals("7",row.getUpdater());assertEquals(row.getCreateTime(),row.getUpdateTime());
        assertEquals(DeviceLicenseRecordCipherTest.ORIGINAL.codeMac(),fixture.cipher.verifyStored(row.getLicenseCodeCiphertext(),1L));
        try(var sql=db.createStatement();var rs=sql.executeQuery("SHOW COLUMNS FROM ast_device_license_record")){int count=0;while(rs.next())count++;assertEquals(16,count);}
        var page=service.page(new Query(1L,7L,100L,List.of(id),1,20,null));
        assertEquals(1,page.getTotal());assertFalse(page.getList().getFirst().values().containsKey("licenseCodeCiphertext"));
    }
    @Test void serialFilterAppliesBeforeCountAndPaginationAndKeepsLiteralCharacters() throws Exception {
        var result=tx.execute(status->service.apply(fixture.command));Long id=result.getFirst().id();
        try(var statement=db.prepareStatement("UPDATE ast_device_license_record SET device_sn=? WHERE id=?")) {
            statement.setString(1,"SN%_ABC");statement.setLong(2,id);statement.executeUpdate();
        }
        assertEquals(1,mapper.page(new DeviceLicenseRecordMapper.PageQuery(1L,List.of(id),0,20,"%_A")).getTotal());
        assertEquals(1,service.page(new Query(1L,7L,100L,List.of(id),1,20,"  ABC  ")).getTotal());
        assertEquals(0,mapper.page(new DeviceLicenseRecordMapper.PageQuery(1L,List.of(id),0,20,"missing")).getTotal());
        var beyond=mapper.page(new DeviceLicenseRecordMapper.PageQuery(1L,List.of(id),20,20,"ABC"));
        assertEquals(1,beyond.getTotal());assertTrue(beyond.getList().isEmpty());
        assertEquals(0,mapper.page(new DeviceLicenseRecordMapper.PageQuery(2L,List.of(id),0,20,"ABC")).getTotal());
        assertEquals(0,mapper.page(new DeviceLicenseRecordMapper.PageQuery(1L,List.of(),0,20,"ABC")).getTotal());
    }
    @Test void aFailureAfterSqlInsertRollsBackTheBusinessRecord() throws Exception {
        doThrow(new IllegalStateException("synthetic receipt failure")).when(fixture.evidence).appendSourceRecords(any());
        assertThrows(IllegalStateException.class,()->tx.execute(status->service.apply(fixture.command)));
        try(var sql=db.createStatement();var rs=sql.executeQuery("SELECT COUNT(*) FROM ast_device_license_record")){rs.next();assertEquals(0,rs.getInt(1));}
    }
    @Test void tenantFiltersAndEmptyIdsCannotBroadenThePage() {
        var result=tx.execute(status->service.apply(fixture.command));Long id=result.getFirst().id();
        TenantContextHolder.setTenantId(2L);
        assertEquals(0,mapper.page(new DeviceLicenseRecordMapper.PageQuery(2L,List.of(id),0,20,null)).getTotal());
        assertEquals(0,mapper.page(new DeviceLicenseRecordMapper.PageQuery(2L,List.of(),0,20,null)).getTotal());
    }
    @Test void laterManualSqlChangeIsDetectedBeforeAnIdempotentReplay() {
        var result=tx.execute(status->service.apply(fixture.command));Long id=result.getFirst().id();
        tx.executeWithoutResult(status->{try(var sql=db.prepareStatement("UPDATE ast_device_license_record SET updater='99' WHERE id=?")){sql.setLong(1,id);sql.executeUpdate();}catch(SQLException error){throw new IllegalStateException(error);}});
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->service.preview(fixture.command)));
    }
}
