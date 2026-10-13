package cn.iocoder.yudao.module.pms.asset.service.warranty;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DeviceWarrantyRenewalMigrationApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
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
import static cn.iocoder.yudao.module.pms.asset.service.warranty.DeviceWarrantyRenewalMigrationServiceTest.*;

/** Real approved schema and mapper, with connection-local shadow tables only. */
@EnabledIfSystemProperty(named = "npdms.renewal.mysql", matches = "true")
class DeviceWarrantyRenewalMySqlTest {
    Connection db;
    SingleConnectionDataSource data;
    TransactionTemplate transaction;
    DeviceWarrantyRenewalRecordMapper mapper;
    DeviceWarrantyRenewalRecordMapper actualMapper;
    DeviceWarrantyRenewalMigrationService service;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306", System.getenv("NPDMS_MYSQL_PORT"));
        assertEquals("npdms_domain_test", System.getenv("NPDMS_DB_NAME"));
        db = DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"), System.getenv("NPDMS_DB_PASSWORD"));
        data = new SingleConnectionDataSource(db, true);
        String ddl = Files.readString(Path.of(System.getProperty("npdms.renewal.sourceRoot"),
                "SQL/migrations/V473__approved_device_warranty_renewal_record.sql"));
        try (var sql = db.createStatement()) {
            sql.execute(ddl.replace("CREATE TABLE ast_device_warranty_renewal_record", "CREATE TEMPORARY TABLE ast_device_warranty_renewal_record"));
            sql.execute("CREATE TEMPORARY TABLE ast_device_shipment (id BIGINT,tenant_id BIGINT,device_sn VARCHAR(100),contract_no VARCHAR(64),rma_no VARCHAR(100),deleted BIT DEFAULT 0) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        var factory = new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(data);
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        var plugins = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));
        factory.setPlugins(plugins);
        var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());factory.setGlobalConfig(global);
        factory.setMapperLocations(new ClassPathResource("mapper/warranty/DeviceWarrantyRenewalRecordMapper.xml"));
        actualMapper = new SqlSessionTemplate(factory.getObject()).getMapper(DeviceWarrantyRenewalRecordMapper.class);
        mapper = spy(actualMapper);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(data));
        var permissions = mock(PermissionApi.class);when(permissions.hasAnyPermissions(eq(7L), any())).thenReturn(true);
        service = new DeviceWarrantyRenewalMigrationService(mapper, mock(AdminUserApi.class), permissions);
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void close() throws Exception { TenantContextHolder.clear();if (data != null) data.destroy();else if (db != null) db.close(); }
    @Test void exactSeventeenColumnsPersistActualDatesNullsEmptySnAndIdempotentAudit() throws Exception {
        var original = source("", "");
        var result = transaction.execute(status -> service.apply(request(entry(original)))).getFirst();
        var row = transaction.execute(status -> mapper.selectForUpdate(new DeviceWarrantyRenewalRecordMapper.Identity(1L, result.targetId())));
        assertEquals("", row.getDeviceSn());assertNull(row.getWarrantyMonths());assertNull(row.getIsLatest());
        assertEquals("2022-04-17", row.getWarrantyEndDate().toString());assertEquals("7", row.getCreator());
        assertEquals(result.fingerprint(), DeviceWarrantyRenewalMigrationService.fingerprint(row));
        var replay = transaction.execute(status -> service.apply(request(new Entry(original.sourceKey(), original, row.getId(), result.fingerprint())))).getFirst();
        assertEquals("UNCHANGED", replay.action());assertEquals(result.fingerprint(), replay.fingerprint());
        try (var sql = db.createStatement(); var rs = sql.executeQuery("SHOW COLUMNS FROM ast_device_warranty_renewal_record")) {
            int count = 0;while (rs.next()) count++;assertEquals(17, count);
        }
    }
    @Test void projectShipmentQueryIncludesAllRenewalsWithoutAnyDeviceMasterAndDoesNotDuplicateRows() throws Exception {
        transaction.execute(status -> service.apply(request(entry(source("NO-MASTER-SN", "1")), entry(source("OTHER-SN", "2")), entry(source("", "")))));
        try (var sql = db.createStatement()) {
            sql.executeUpdate("INSERT INTO ast_device_shipment VALUES (1,1,'NO-MASTER-SN','C-1',NULL,0),(2,1,'NO-MASTER-SN','C-1','',0),(3,2,'OTHER-SN','C-1',NULL,0),(4,1,'','C-1',NULL,0)");
        }
        var query = new DeviceWarrantyRenewalRecordMapper.PageQuery(1L, null, Set.of("C-1"), null, 0, 20);
        assertEquals(1, mapper.count(query));assertEquals("NO-MASTER-SN", mapper.selectPage(query).getFirst().getDeviceSn());
        assertEquals(0, mapper.count(new DeviceWarrantyRenewalRecordMapper.PageQuery(1L, null, Set.of(), null, 0, 20)));
        assertEquals(0, mapper.count(new DeviceWarrantyRenewalRecordMapper.PageQuery(1L, "", Set.of(), null, 0, 20)));
        TenantContextHolder.setTenantId(2L);
        assertEquals(0, mapper.count(new DeviceWarrantyRenewalRecordMapper.PageQuery(2L, null, Set.of("C-1"), null, 0, 20)));
    }
    @Test void sourceChangeUpdatesBusinessOnlyAndSubsequentManualChangeBlocksReplay() throws Exception {
        var original = source("SN", "1");
        var created = transaction.execute(status -> service.apply(request(entry(original)))).getFirst();
        var changed = source("SN", "2");
        var updated = transaction.execute(status -> service.apply(request(new Entry(changed.sourceKey(), changed, created.targetId(), created.fingerprint())))).getFirst();
        assertEquals("UPDATED", updated.action());assertEquals(24, updated.fields().get("warrantyMonths"));
        assertEquals(created.fields().get("createTime"), updated.fields().get("createTime"));
        var stored = transaction.execute(status -> mapper.selectForUpdate(new DeviceWarrantyRenewalRecordMapper.Identity(1L, updated.targetId())));
        assertEquals(updated.fingerprint(), DeviceWarrantyRenewalMigrationService.fingerprint(stored));
        try (var sql = db.createStatement()) { sql.executeUpdate("UPDATE ast_device_warranty_renewal_record SET remark='manual'"); }
        assertThrows(IllegalArgumentException.class, () -> transaction.execute(status -> service.apply(request(
                new Entry(changed.sourceKey(), changed, updated.targetId(), updated.fingerprint())))));
    }
    @Test void chunkFailureRollsBackEarlierInsert() throws Exception {
        doAnswer(call -> {
            var row = (cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRenewalRecordDO) call.getArgument(0);
            if ("FAIL".equals(row.getDeviceSn())) throw new IllegalStateException("forced second-row failure");
            return actualMapper.insertRecord(row);
        }).when(mapper).insertRecord(any());
        assertThrows(RuntimeException.class, () -> transaction.execute(status -> service.apply(request(entry(source("OK", "1")), entry(source("FAIL", "1"))))));
        try (var sql = db.createStatement(); var rs = sql.executeQuery("SELECT COUNT(*) FROM ast_device_warranty_renewal_record")) { rs.next();assertEquals(0, rs.getLong(1)); }
    }
}
