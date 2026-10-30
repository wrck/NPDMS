package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceSelectionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectDeviceSelectionQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceCurrentModelSqlTest {
    private JdbcDataSource source;
    private SqlSession session;
    private ProjectDeviceSelectionMapper mapper;

    @BeforeEach void setup() throws Exception {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:model_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = source.getConnection(); var sql = c.createStatement()) {
            sql.execute("CREATE TABLE ast_device(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,customer_id BIGINT,sn VARCHAR(64),name VARCHAR(64),product_code VARCHAR(64),product_model VARCHAR(64),contract_no VARCHAR(64),status VARCHAR(20),deleted INT DEFAULT 0,shipment_time TIMESTAMP DEFAULT '2026-10-01 00:00:00',conp_version VARCHAR(64))");
            sql.execute("CREATE TABLE ast_product_official_info(id BIGINT PRIMARY KEY,tenant_id BIGINT,product_code VARCHAR_IGNORECASE(64) NOT NULL,product_model VARCHAR(64),source_system VARCHAR(20),status VARCHAR(20),deleted INT, UNIQUE(tenant_id,product_code))");
            for (int id = 1; id <= 10; id++)
                sql.execute("INSERT INTO ast_device(id,tenant_id,project_id,sn,product_code) VALUES(" + id + ",1,10,'SN-" + id + "','C" + id + "')");
            sql.execute("UPDATE ast_device SET product_model='DEVICE-ORIGINAL' WHERE id=2");
            sql.execute("UPDATE ast_device SET product_model='' WHERE id=9");
            sql.execute("INSERT INTO ast_device(id,tenant_id,project_id,sn,product_code) VALUES(11,2,10,'FOREIGN','C1'),(12,1,20,'OUTSIDE','C1')");
            sql.execute("INSERT INTO ast_product_official_info VALUES (1,1,'C1','CRM-ONE','CRM','ACTIVE',0),(2,1,'C2','CRM-OTHER','CRM','ACTIVE',0),"
                    + "(3,1,'C3','A','CRM','ACTIVE',0),(5,1,'C4','SAME','CRM','ACTIVE',0),"
                    + "(7,2,'C5','FOREIGN','CRM','ACTIVE',0),(8,1,'C6','INACTIVE','CRM','INACTIVE',0),(9,1,'C7','DELETED','CRM','ACTIVE',1),"
                    + "(10,1,'C8','OTHER-SOURCE','DPPMS','ACTIVE',0),(11,1,'C9','CRM-NINE','CRM','ACTIVE',0),(12,1,'C10','   ','CRM','ACTIVE',0)");
        }
        var config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setEnvironment(new Environment("model", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        for (var entity : List.of(
                cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO.class,
                cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(config, ""), entity);
        }
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        var interceptor = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));
        config.addInterceptor(interceptor);
        for (String name : List.of("DeviceCurrentModelMapper", "ProjectDeviceSelectionMapper", "ProjectDeviceShipmentMapper")) {
            try (var xml = getClass().getResourceAsStream("/mapper/device/" + name + ".xml")) {
                new XMLMapperBuilder(xml, config, name, config.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        mapper = session.getMapper(ProjectDeviceSelectionMapper.class);
    }
    @AfterEach void close() {
        if (session != null) session.close();
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
    }
    private ProjectDeviceSelectionQuery query() {
        var query = new ProjectDeviceSelectionQuery();
        query.setTenantId(1L); query.setProjectId(10L); query.setPageNo(1); query.setPageSize(50);
        return query;
    }
    @Test void uniqueOfficialModelFillsOnlyBlankValuesRegardlessOfSourceOrStatusAndDoesNotWriteBack() throws Exception {
        var rows = mapper.selectPageCandidates(query());
        var byId = new HashMap<Long, String>(); rows.forEach(row -> byId.put(row.getId(), row.getProductModel()));
        assertEquals("CRM-ONE", byId.get(1L));
        assertEquals("DEVICE-ORIGINAL", byId.get(2L));
        assertEquals("CRM-NINE", byId.get(9L));
        assertEquals("A", byId.get(3L)); assertEquals("SAME", byId.get(4L));
        assertEquals("INACTIVE", byId.get(6L)); assertEquals("OTHER-SOURCE", byId.get(8L));
        for (long id : List.of(5L,7L)) assertNull(byId.get(id), "unexpected model for " + id);
        assertEquals("   ", byId.get(10L), "主档展示值不在查询中归一化");
        assertEquals(10, rows.size());
        try (var c = source.getConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT product_model FROM ast_device WHERE id=1")) {
            assertTrue(r.next()); assertNull(r.getString(1));
        }
    }
    @Test void searchCountAndPagesUseTheSameModelWithoutDuplicateDevices() {
        var query = query(); query.setKeyword("CRM"); query.setPageSize(1);
        assertEquals(2, mapper.selectCountCandidates(query));
        assertEquals(List.of(9L), mapper.selectPageCandidates(query).stream().map(row -> row.getId()).toList());
        query.setPageNo(2);
        assertEquals(List.of(1L), mapper.selectPageCandidates(query).stream().map(row -> row.getId()).toList());
        query.setPageNo(1); query.setKeyword("CRM-OTHER");
        assertEquals(0, mapper.selectCountCandidates(query));
    }
    @Test void independentModelFilterWithoutKeywordUsesTheDisplayedCurrentModel() {
        var query = query(); query.setProductModel("CRM");
        assertEquals(2, mapper.selectCountCandidates(query));
        assertEquals(List.of(9L, 1L), mapper.selectPageCandidates(query).stream().map(row -> row.getId()).toList());
        query.setKeyword("SN-1");
        assertEquals(1, mapper.selectCountCandidates(query), "关键词仍按AND收窄当前型号");
        query.setKeyword(null);
        query.setProductModel("DEVICE-ORIGINAL");
        assertEquals(List.of(2L), mapper.selectPageCandidates(query).stream().map(row -> row.getId()).toList());
        query.setSn("SN-1");
        assertEquals(0, mapper.selectCountCandidates(query), "原有显式条件仍以AND收窄");
    }
    @Test void missingProjectAndOtherTenantRemainOutsideTheCandidateScope() {
        var query = query(); query.setProjectId(null);
        assertEquals(0, mapper.selectCountCandidates(query));
        assertTrue(mapper.selectPageCandidates(query).isEmpty());
        query.setProjectId(10L); query.setTenantId(3L);
        assertEquals(0, mapper.selectCountCandidates(query));
    }
    @Test void cutoverShipmentSearchUsesTheSameFallbackAndKeepsShipmentScope() throws Exception {
        var shipments = session.getMapper(cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceShipmentMapper.class);
        var query = cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShippedDevicePageQuery.builder()
                .tenantId(1L).projectId(10L).keyword("CRM").pageNo(1).pageSize(20).build();
        assertEquals(2, shipments.countProjectDevices(query));
        assertEquals(Set.of("CRM-ONE", "CRM-NINE"), shipments.selectProjectDevices(query).stream()
                .map(row -> row.getProductModel()).collect(java.util.stream.Collectors.toSet()));
        try (var c = source.getConnection(); var sql = c.createStatement()) {
            sql.execute("UPDATE ast_device SET shipment_time=NULL WHERE id=1");
        }
        session.clearCache();
        assertEquals(1, shipments.countProjectDevices(query));
        assertEquals(9L, shipments.selectProjectDevices(query).getFirst().getId());
    }
    @Test void authorizedCurrentIdProjectionDoesNotExpandEmptyOrForeignSets() {
        var models = session.getMapper(cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceCurrentModelMapper.class);
        assertTrue(models.selectCurrentModels(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceCurrentModelQuery(1L, Set.of())).isEmpty());
        assertTrue(models.selectCurrentModels(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceCurrentModelQuery(1L, Set.of(11L))).isEmpty());
        var found = models.selectCurrentModels(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceCurrentModelQuery(1L, Set.of(1L)));
        assertEquals("CRM-ONE", found.getFirst().productModel());
    }

    @Test void productCodeUsesThePhysicalUniqueKeyCollationAndRejectsDuplicateKeys() throws Exception {
        try(var c=source.getConnection();var sql=c.createStatement()) {
            sql.execute("INSERT INTO ast_device(id,tenant_id,project_id,sn,product_code) VALUES(13,1,10,'CASE','ABC'),(15,1,10,'EXACT','PAIR')");
            sql.execute("INSERT INTO ast_product_official_info VALUES(13,1,'abc','CANONICAL-MODEL','CRM','ACTIVE',0),(15,1,'PAIR','EXACT-MODEL','CRM','ACTIVE',0)");
            assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO ast_product_official_info VALUES(16,1,'pair','CASE-NEIGHBOR','CRM','ACTIVE',0)"));
        }
        var models=session.getMapper(cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceCurrentModelMapper.class)
                .selectCurrentModels(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceCurrentModelQuery(1L,Set.of(13L,15L)));
        var values=new HashMap<Long,String>();models.forEach(row->values.put(row.deviceId(),row.productModel()));
        assertEquals("CANONICAL-MODEL",values.get(13L));assertEquals("EXACT-MODEL",values.get(15L));
    }

    @Test void businessCodesUseNativeEqualityWithoutTrimmingStoredEvidence() throws Exception {
        try(var c=source.getConnection();var sql=c.createStatement()) {
            sql.execute("INSERT INTO ast_device(id,tenant_id,project_id,sn,product_code) VALUES(13,1,10,'EXACT','02010005'),(14,1,10,'PADDED',' PAD ')");
            sql.execute("INSERT INTO ast_product_official_info VALUES(13,1,'02010005','SFP-G-SX-MM850','CRM','ACTIVE',0),(14,1,'PAD','PAD-MODEL','CRM','ACTIVE',0)");
        }
        var models=session.getMapper(cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceCurrentModelMapper.class)
                .selectCurrentModels(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceCurrentModelQuery(1L,Set.of(13L,14L)));
        var values=new HashMap<Long,String>();models.forEach(row->values.put(row.deviceId(),row.productModel()));
        assertEquals("SFP-G-SX-MM850",values.get(13L));assertNull(values.get(14L));
        var shipments=session.getMapper(cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceShipmentMapper.class);
        var selection=cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShippedDevicePageQuery.builder()
                .tenantId(1L).projectId(10L).keyword("SFP-G-SX-MM850").pageNo(1).pageSize(20).build();
        assertEquals(1,shipments.countProjectDevices(selection));
        assertEquals(13L,shipments.selectProjectDevices(selection).getFirst().getId());
        try(var c=source.getConnection();var sql=c.createStatement();var raw=sql.executeQuery("SELECT product_code,product_model FROM ast_device WHERE id=13")) {
            assertTrue(raw.next());assertEquals("02010005",raw.getString(1));assertNull(raw.getString(2));
        }
    }

    @Test void duplicateOfficialKeysAreRejectedAndInternalSpacesRemainSignificant() throws Exception {
        try(var c=source.getConnection();var sql=c.createStatement()) {
            sql.execute("INSERT INTO ast_device(id,tenant_id,project_id,sn,product_code) VALUES(13,1,10,'DUPLICATE','DUP'),(14,1,10,'INTERNAL','INNER CODE')");
            sql.execute("INSERT INTO ast_product_official_info VALUES(13,1,'DUP','MODEL','CRM','ACTIVE',0),(15,1,'INNERCODE','WRONG','CRM','ACTIVE',0)");
            assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO ast_product_official_info VALUES(14,1,'DUP','OTHER','CRM','ACTIVE',0)"));
        }
        var models=session.getMapper(cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceCurrentModelMapper.class)
                .selectCurrentModels(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceCurrentModelQuery(1L,Set.of(13L,14L)));
        assertEquals(2,models.size());
        assertEquals("MODEL",models.stream().filter(row->row.deviceId()==13L).findFirst().orElseThrow().productModel());
        assertNull(models.stream().filter(row->row.deviceId()==14L).findFirst().orElseThrow().productModel());
    }
}
