package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsShipmentReferenceCorrectionApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Existing physical schema is reused on connection-local tables; permanent business tables are never written. */
@EnabledIfSystemProperty(named="npdms.shipment-references.mysql",matches="true")
class DeviceShipmentReferenceMySqlTest {
    Connection db;SingleConnectionDataSource source;TransactionTemplate tx;
    ShipmentReferenceCorrectionMapper mapper;DppmsShipmentReferenceCorrectionService service;
    final Command command=new Command(1L,7L,100L,List.of(new Reference("20",80L,70L,90L,"SN","ORDER","10625024")));
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        source=new SingleConnectionDataSource(db,true);
        createTemporary("ast_device_shipment");createTemporary("ast_device");
        try(var sql=db.createStatement()) {
            sql.execute("INSERT INTO ast_device_shipment(id,tenant_id,device_sn,event_type,source_system,source_key,sync_status,version,order_no,line_no,shipment_time,package_no,contract_no,source_payload,creator,create_time,updater,update_time) VALUES(80,1,'SN','SHIPMENT','DPPMS','20','SUCCESS',3,'ORDER','10625024','2020-01-02 03:04:05.123','PACKAGE','CONTRACT',JSON_OBJECT('id',20),'','2020-01-02 03:04:05.123','','2020-01-02 03:04:05.456')");
            sql.execute("INSERT INTO ast_device(id,tenant_id,sn,status,source_system,project_id,shipment_record_id,version,shipment_time,package_no,contract_no,creator,create_time,updater,update_time) VALUES(70,1,'SN','INSTALLED','DPPMS',100,60,4,'2020-01-02 03:04:05.123','PACKAGE','CONTRACT','','2020-01-02 03:04:05.123','','2020-01-02 03:04:05.456')");
        }
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();factory.setDataSource(source);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.addMapper(cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper.class);
        factory.setConfiguration(config);
        var plugins=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(
                        new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));
        factory.setPlugins(plugins);
        var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
        factory.setGlobalConfig(global);
        factory.setMapperLocations(new ClassPathResource("mapper/shipment/ShipmentReferenceCorrectionMapper.xml"),new ClassPathResource("mapper/shipment/DeviceShipmentMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());mapper=spy(session.getMapper(ShipmentReferenceCorrectionMapper.class));
        var projects=mock(ProjectDeviceAssignmentGuardApi.class);
        when(projects.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        var target=new DppmsShipmentReferenceCorrectionService(mapper,session.getMapper(DeviceShipmentMapper.class),projects);
        var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        service=(DppmsShipmentReferenceCorrectionService)proxy.getProxy();TenantContextHolder.setTenantId(1L);
        var login=new cn.iocoder.yudao.framework.security.core.LoginUser();login.setId(7L);login.setTenantId(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(login,null));
    }
    @AfterEach void close() throws Exception {
        TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();
        if(source!=null)source.destroy();else if(db!=null)db.close();
    }
    @Test void fullExistingSchemasPreserveAuditAndPayloadUnderAuthenticatedCorrectionAndReplay() throws Exception {
        var baseline=tx.execute(status->service.preview(command));
        assertEquals(39,JsonUtils.parseTree(baseline.getFirst().shipmentFieldsJson()).size());
        assertEquals(53,JsonUtils.parseTree(baseline.getFirst().deviceFieldsJson()).size());
        assertPhysicalFields("ast_device_shipment",baseline.getFirst().shipmentFieldsJson());
        assertPhysicalFields("ast_device",baseline.getFirst().deviceFieldsJson());
        tx.executeWithoutResult(status->service.correct(command,baseline));tx.executeWithoutResult(status->service.verify(command,baseline));
        assertEquals(90L,((Number)scalar("SELECT order_line_id FROM ast_device_shipment WHERE id=80")).longValue());
        assertEquals(80L,((Number)scalar("SELECT shipment_record_id FROM ast_device WHERE id=70")).longValue());
        assertEquals("",scalar("SELECT updater FROM ast_device WHERE id=70"));assertEquals("",scalar("SELECT updater FROM ast_device_shipment WHERE id=80"));
        assertEquals(3,((Number)scalar("SELECT version FROM ast_device_shipment WHERE id=80")).intValue());
        assertEquals(4L,((Number)scalar("SELECT version FROM ast_device WHERE id=70")).longValue());
    }
    @Test void secondWriteFailureRollsBackTheFirstReferenceAndKeepsOriginalBlankAudits() throws Exception {
        var baseline=tx.execute(status->service.preview(command));doReturn(0).when(mapper).repairDeviceShipment(any());
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(status->service.correct(command,baseline)));
        assertNull(scalar("SELECT order_line_id FROM ast_device_shipment WHERE id=80"));
        assertEquals(60L,((Number)scalar("SELECT shipment_record_id FROM ast_device WHERE id=70")).longValue());
        assertEquals("",scalar("SELECT updater FROM ast_device_shipment WHERE id=80"));
    }
    @Test void foreignTenantRecordIsInvisibleAndCannotBeCorrected() throws Exception {
        try(var sql=db.createStatement()) {sql.execute("UPDATE ast_device SET tenant_id=2,update_time=update_time WHERE id=70");}
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->service.preview(command)));
        assertNull(scalar("SELECT order_line_id FROM ast_device_shipment WHERE id=80"));
        assertEquals(60L,((Number)scalar("SELECT shipment_record_id FROM ast_device WHERE id=70")).longValue());
        verify(mapper,never()).fillOrderLine(any());verify(mapper,never()).repairDeviceShipment(any());
    }
    @Test void laterBusinessChangeAndExistingCurrentShipmentBothBlockBeforeAnyWrite() throws Exception {
        var baseline=tx.execute(status->service.preview(command));
        try(var sql=db.createStatement()) {sql.execute("UPDATE ast_device SET remark='human modification' WHERE id=70");}
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(status->service.correct(command,baseline)));
        assertNull(scalar("SELECT order_line_id FROM ast_device_shipment WHERE id=80"));
        try(var sql=db.createStatement()) {sql.execute("INSERT INTO ast_device_shipment(id,tenant_id,device_sn,event_type,source_system,source_key,sync_status,shipment_time) VALUES(60,1,'OTHER','SHIPMENT','ERP','OTHER','SUCCESS','2019-01-01')");}
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->service.preview(command)));
        assertNull(scalar("SELECT order_line_id FROM ast_device_shipment WHERE id=80"));verify(mapper,never()).fillOrderLine(any());
    }
    @Test void databaseAutomaticAuditChangeIsDetectedInsteadOfBeingAcceptedAsASuccessfulRepair() throws Exception {
        var baseline=tx.execute(status->service.preview(command));
        String original=scalar("SELECT update_time FROM ast_device_shipment WHERE id=80").toString();
        try(var sql=db.createStatement()) {
            sql.execute("UPDATE ast_device_shipment SET order_line_id=90 WHERE id=80");
            sql.execute("UPDATE ast_device SET shipment_record_id=80 WHERE id=70");
        }
        assertNotEquals(original,scalar("SELECT update_time FROM ast_device_shipment WHERE id=80").toString());
        assertEquals("DPPMS_SHIPMENT_REFERENCE_APPLIED_TARGET_CHANGED",assertThrows(IllegalArgumentException.class,
                ()->tx.executeWithoutResult(status->service.verify(command,baseline))).getMessage());
    }
    private void assertPhysicalFields(String table,String snapshot) throws Exception {
        var expected=new HashSet<String>();
        try(var statement=db.createStatement();var rows=statement.executeQuery("SHOW COLUMNS FROM "+table)) {
            while(rows.next())expected.add(rows.getString(1));
        }
        var actual=new HashSet<String>();
        for(var field:JsonUtils.parseTree(snapshot).propertyNames())
            actual.add(field.replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT));
        assertEquals(expected,actual);
    }
    private Object scalar(String sql) throws Exception {
        try(var statement=db.createStatement();var result=statement.executeQuery(sql)) {assertTrue(result.next());return result.getObject(1);}
    }
    private void createTemporary(String table) throws Exception {
        assertTrue(Set.of("ast_device_shipment","ast_device").contains(table));
        String definition;
        try(var statement=db.createStatement();var row=statement.executeQuery("SHOW CREATE TABLE "+table)) {
            assertTrue(row.next());definition=row.getString(2);
        }
        // Preserve every existing column/index/check. A changed schema with foreign keys requires a reviewed fixture.
        assertFalse(definition.contains("FOREIGN KEY"));
        try(var statement=db.createStatement()) {statement.execute(definition.replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"));}
    }
}
