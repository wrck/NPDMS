package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DevicePageReadScope;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 使用实际验收库和生产XML，连接只读，不准备或清理业务数据。 */
@EnabledIfSystemProperty(named = "npdms.deviceScope.readOnly", matches = "true")
class DeviceOrganizationScopeMySqlReadOnlyTest {
    @Test
    void z01010DepartmentScopeRejectsHangzhouAndKeepsChangchun() throws Exception {
        Map<String, String> settings = new HashMap<>();
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("compose.yaml"))) root = root.getParent();
        for (String line : Files.readAllLines(root.resolve(".env"))) {
            int equals = line.indexOf('=');
            if (equals > 0 && !line.stripLeading().startsWith("#")) {
                settings.put(line.substring(0, equals).trim(), line.substring(equals + 1).trim().replaceAll("^\"|\"$", ""));
            }
        }
        var config = new Configuration();
        for (String resource : List.of("mapper/authorization/ProjectViewGrantReadSql.xml",
                "mapper/projectscope/ProjectViewReadScopeSql.xml", "mapper/contract/ContractDeviceReadScopeSql.xml",
                "mapper/device/DeviceCurrentModelMapper.xml", "mapper/device/DeviceQueryMapper.xml", "mapper/device/DeviceJoinedPageMapper.xml")) {
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        try (Connection db = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                settings.get("DB_USER"), settings.get("DB_PASSWORD"))) {
            db.setReadOnly(true);
            db.setAutoCommit(false);
            List<ContractDeviceVisibilityApi.OrganizationGrant> grants = new ArrayList<>();
            try (var statement = db.prepareStatement("SELECT company_id,department_id,department_code FROM system_user_company_department_scope WHERE tenant_id=1 AND user_id=? AND deleted=0 AND status=0 AND effective_from<=CURRENT_TIMESTAMP AND (effective_to IS NULL OR effective_to>CURRENT_TIMESTAMP)")) {
                statement.setLong(1, 2108539871029019646L);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) grants.add(new ContractDeviceVisibilityApi.OrganizationGrant(
                            rows.getLong(1), rows.getObject(2, Long.class), rows.getString(3)));
                }
            }
            assertFalse(grants.isEmpty(), "验收账号须有当前有效组织范围");
            assertTrue(grants.stream().noneMatch(g -> g.departmentId() == null || g.departmentId() == 253L));
            var query = VisibleDevicePageQuery.builder().tenantId(1L).pageNo(1).pageSize(10)
                    .organizationGrants(grants.stream().map(g -> new DeviceOrganizationGrant(g.companyId(), g.departmentId(), g.departmentCode())).toList())
                    .readScope(new DevicePageReadScope(null, new ContractDeviceVisibilityApi.ReadScope(grants))).build();
            assertEquals(0L, total(db, config, query.toBuilder().sn("02010502N224800206").build()));
            assertEquals(1L, total(db, config, query.toBuilder().sn("02230029D239025060").build()));
            var companyScope = new DevicePageReadScope(null, new ContractDeviceVisibilityApi.ReadScope(
                    List.of(new ContractDeviceVisibilityApi.OrganizationGrant(1L, null, null))));
            assertEquals(1L, total(db, config, query.toBuilder().sn("02010502N224800206").readScope(companyScope).build()));
            db.rollback();
        }
    }

    private long total(Connection db, Configuration config, VisibleDevicePageQuery query) throws Exception {
        var parameters = Map.of("query", query);
        var bound = config.getMappedStatement(DeviceMapper.class.getName() + ".selectJoinedDevicePageRows").getBoundSql(parameters);
        try (var statement = db.prepareStatement(bound.getSql())) {
            int position = 1;
            for (var mapping : bound.getParameterMappings()) {
                String property = mapping.getProperty();
                statement.setObject(position++, bound.hasAdditionalParameter(property)
                        ? bound.getAdditionalParameter(property) : config.newMetaObject(parameters).getValue(property));
            }
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong("total");
            }
        }
    }
}
