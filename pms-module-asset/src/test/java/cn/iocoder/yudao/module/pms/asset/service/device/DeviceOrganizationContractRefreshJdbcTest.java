package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceContractOrganizationBatchQuery;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceOrganizationContractRefreshJdbcTest {
    private JdbcTemplate jdbc;
    private DeviceOrganizationMapper mapper;
    private TransactionTemplate tx;

    @Test void mysqlIndexHintRemainsCompatibleWithTheTenantInterceptor() throws Exception {
        var config = new Configuration();
        try (var in = new ClassPathResource("mapper/device/DeviceOrganizationMapper.xml").getInputStream()) {
            new XMLMapperBuilder(in, config, "mysql-contract-refresh", config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement(DeviceOrganizationMapper.class.getName() + ".selectContractBatchForUpdate");
        var sql = statement.getBoundSql(Map.of("query", new DeviceContractOrganizationBatchQuery(2L, Set.of("A"), "A", 100, 500))).getSql();
        var tenant = new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                    @Override public net.sf.jsqlparser.expression.Expression getTenantId() {
                        return new net.sf.jsqlparser.expression.LongValue(1);
                    }
                });
        String scoped = tenant.parserSingle(sql, null);
        assertTrue(scoped.contains("FORCE INDEX (idx_ast_device_contract_projection)"));
        assertTrue(scoped.contains("tenant_id = 1"));
        assertTrue(scoped.contains("FOR UPDATE"));
    }

    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE ast_device (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,contract_no VARCHAR(64),"
                + "deleted BOOLEAN DEFAULT FALSE,company_id BIGINT,company_name VARCHAR(255),department_id BIGINT,"
                + "department_code VARCHAR(64),department_name VARCHAR(255),organization_source VARCHAR(20),organization_updated_at TIMESTAMP)");
        var config = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), ds));
        config.setMapUnderscoreToCamelCase(true);
        String xml;
        try (var in = new ClassPathResource("mapper/device/DeviceOrganizationMapper.xml").getInputStream()) {
            // H2 executes the same bound predicates and locks; the MySQL index hint is verified against the real target separately.
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace(" FORCE INDEX (idx_ast_device_contract_projection)", "");
        }
        new XMLMapperBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), config,
                "mapper/device/DeviceOrganizationMapper.xml", config.getSqlFragments()).parse();
        mapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(DeviceOrganizationMapper.class);
    }

    @Test void cursorSelectsTheSameTenantAndContractsWithoutProjectDevicesOrDeletedRows() {
        jdbc.update("INSERT INTO ast_device(id,tenant_id,contract_no) VALUES (10,1,'B'),(100,1,'A'),(200,1,'A'),(300,2,'A'),(400,1,'OTHER')");
        jdbc.update("INSERT INTO ast_device(id,tenant_id,contract_no,project_id) VALUES (500,1,'A',99)");
        jdbc.update("INSERT INTO ast_device(id,tenant_id,contract_no,deleted) VALUES (600,1,'A',TRUE)");
        tx.executeWithoutResult(s -> {
            var numbers = Set.of("A", "B");
            assertEquals(100L, mapper.selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, numbers, null, 0, 1)).getFirst().getId());
            assertEquals(200L, mapper.selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, numbers, "A", 100, 1)).getFirst().getId());
            assertEquals(10L, mapper.selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, numbers, "A", 200, 1)).getFirst().getId());
            assertTrue(mapper.selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, numbers, "B", 10, 1)).isEmpty());
            assertTrue(mapper.selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, Set.of(), null, 0, 500)).isEmpty());
            assertTrue(mapper.selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, null, null, 0, 500)).isEmpty());
        });
    }

    @Test void laterPageFailureRollsBackEarlierOrganizationUpdates() {
        for (long id = 1; id <= 501; id++)
            jdbc.update("INSERT INTO ast_device(id,tenant_id,contract_no,company_id,organization_source) VALUES (?,1,'A',9,'CONTRACT')", id);
        var failing = spy(mapper);
        doThrow(new IllegalStateException("Next page unavailable")).when(failing)
                .selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, Set.of("A"), "A", 500, 500));
        var owner = mock(ContractDeviceVisibilityApi.class);
        when(owner.getOrganizations(1L, Set.of("A"))).thenReturn(List.of());
        var service = new DeviceOrganizationContractRefreshService(failing, owner);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s ->
                service.refresh(new DeviceOrganizationProjectionApi.Refresh(1L, Set.of(), Set.of(), Set.of("A")))));
        assertEquals(501, jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE company_id=9 AND organization_source='CONTRACT' AND organization_updated_at IS NULL", Integer.class));
        verify(failing).updateOrganizationBatch(any(), argThat(ids -> ids.size() == 500));
    }
}
