package cn.iocoder.yudao.module.pms.integration.basesync.task;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Executes production mapper SELECTs against connection-local CTEs; never creates or changes tables. */
class P22OrderSourceKeySqlTest {
    private Connection connection;
    private URLClassLoader driverLoader;
    private Configuration configuration;

    @BeforeEach void connect() throws Exception {
        String url = System.getenv("TEST_MYSQL_URL");
        String user = System.getenv("TEST_MYSQL_USERNAME");
        String password = System.getenv("TEST_MYSQL_PASSWORD");
        assumeTrue(url != null && user != null && password != null,
                "Requires TEST_MYSQL_URL/USERNAME/PASSWORD");
        var properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        String driverJar = System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR");
        if (driverJar != null && !driverJar.isBlank()) {
            driverLoader = new URLClassLoader(new java.net.URL[]{Path.of(driverJar).toUri().toURL()}, getClass().getClassLoader());
            var driver = (Driver) Class.forName("com.mysql.cj.jdbc.Driver", true, driverLoader).getConstructor().newInstance();
            connection = driver.connect(url, properties);
        } else {
            connection = DriverManager.getConnection(url, properties);
        }
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        configuration = new Configuration();
        for (String resource : List.of("base-sync-source/P14SourceMapper.xml", "mapper/basesync/P22TargetMapper.xml")) {
            try (var input = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @AfterEach void close() throws Exception {
        try {
            if (connection != null) {
                try { connection.rollback(); } finally { connection.close(); }
            }
        } finally {
            if (driverLoader != null) driverLoader.close();
        }
    }

    @Test void numericP14TypesAndStringLineNumbersMatchFormalVarcharTypes() throws Exception {
        for (int type : List.of(0, 1)) {
            for (String line : List.of("1", "10")) {
                var fixture = new Fixture();
                fixture.type = type;
                fixture.line = line;
                assertEquals(List.of("101"), owned(fixture, false), "type=" + type + ", line=" + line);
            }
        }
    }

    @Test void sameOrderNumberWithDifferentParentTypeCannotMatch() throws Exception {
        var fixture = new Fixture();
        fixture.parentType = "1";
        assertTrue(owned(fixture, false).isEmpty());
    }

    @Test void ownershipAndCompletedNonPreviewRunAreRequired() throws Exception {
        List<Consumer<Fixture>> invalid = List.of(
                f -> f.tenant = 2,
                f -> f.connection = 34,
                f -> f.runStatus = "RUNNING",
                f -> f.preview = true,
                f -> f.taskCode = "BASE_P15",
                f -> f.runTaskCode = "BASE_P15",
                f -> f.objectKey = "SALES_ORDER",
                f -> f.bindingDeleted = true,
                f -> f.taskDeleted = true,
                f -> f.runDeleted = true,
                f -> f.runConnection = 34,
                f -> f.runId = 99,
                f -> f.source = "OTHER",
                f -> f.company = "002",
                f -> f.bindingKeyWrong = true);
        for (int index = 0; index < invalid.size(); index++) {
            var fixture = new Fixture();
            invalid.get(index).accept(fixture);
            assertTrue(owned(fixture, false).isEmpty(), "invalid ownership fixture " + index);
        }
    }

    @Test void ambiguousOwnedLinesAreExcluded() throws Exception {
        var fixture = new Fixture();
        fixture.duplicate = true;
        assertTrue(owned(fixture, false).isEmpty());
    }

    @Test void emptyCatalogueReturnsNoCandidates() throws Exception {
        assertTrue(owned(new Fixture(), true).isEmpty());
    }

    @Test void catalogueLookupKeepsExactProductText() throws Exception {
        for (String product : List.of("product", "PRODUCT ")) {
            var fixture = new Fixture();
            fixture.product = product;
            assertTrue(owned(fixture, false).isEmpty(), product);
        }
    }

    @Test void postfillKeepsCurrentRunBoundaryAndExactOrderKeys() throws Exception {
        assertEquals(List.of("1001"), postfill(new Fixture(), new PostfillFixture()));
        List<Consumer<PostfillFixture>> invalid = List.of(
                f -> f.bindingTenant = 2,
                f -> f.taskTenant = 2,
                f -> f.scopeTenant = 2,
                f -> f.connection = 34,
                f -> f.activeRun = 602,
                f -> f.bindingRun = 602,
                f -> f.taskCode = "BASE_P14",
                f -> f.objectKey = "SALES_LINE",
                f -> f.taskDeleted = true,
                f -> f.bindingDeleted = true,
                f -> f.scopeDeleted = true,
                f -> f.confirmed = true,
                f -> f.orderLineId = 101,
                f -> f.allocationSource = "MANUAL");
        for (int index = 0; index < invalid.size(); index++) {
            var fixture = new PostfillFixture();
            invalid.get(index).accept(fixture);
            assertTrue(postfill(new Fixture(), fixture).isEmpty(), "postfill boundary " + index);
        }
        for (String product : List.of("product", "PRODUCT ")) {
            var fixture = new Fixture();
            fixture.product = product;
            assertTrue(postfill(fixture, new PostfillFixture()).isEmpty(), product);
        }
        var wrongType = new Fixture();
        wrongType.parentType = "1";
        assertTrue(postfill(wrongType, new PostfillFixture()).isEmpty());
        var duplicate = new Fixture();
        duplicate.duplicate = true;
        assertTrue(postfill(duplicate, new PostfillFixture()).isEmpty());
    }

    @Test void scopeIdentityCatalogueRetainsDuplicatesAndDeletedHistoryInTenant() throws Exception {
        var query = new P22ProductScopeSyncTask.ScopeIdentityQuery(1, true);
        var ids = select(P22ProductScopeSyncTask.TargetMapper.class.getName() + ".selectScopeIdentities",
                Map.of("query", query), scopeCte(), "id");
        assertEquals(List.of("101", "102", "104"), ids.stream().sorted().toList());
    }

    @Test void scopePrimaryKeyReadsKeepTenantAndEmptySetBoundaries() throws Exception {
        var id = P22ProductScopeSyncTask.TargetMapper.class.getName() + ".selectScopes";
        assertEquals(List.of("101", "102"), select(id,
                Map.of("query", new P22ProductScopeSyncTask.ScopesQuery(1, List.of(101L, 102L, 201L), true)),
                scopeCte(), "id").stream().sorted().toList());
        assertTrue(select(id,
                Map.of("query", new P22ProductScopeSyncTask.ScopesQuery(1, List.of(), true)),
                scopeCte(), "id").isEmpty());
        assertEquals(List.of("201"), select(id,
                Map.of("query", new P22ProductScopeSyncTask.ScopesQuery(2, List.of(101L, 201L), true)),
                scopeCte(), "id"));
    }

    private String scopeCte() {
        return """
                WITH com_delivery_scope AS (
                  SELECT 101 id,1 tenant_id,'DPPMS:pm_project_product_line:1:1' source_evidence,FALSE deleted
                  UNION ALL SELECT 102,1,'DPPMS:pm_project_product_line:1:1',TRUE
                  UNION ALL SELECT 103,1,'MANUAL:other',FALSE
                  UNION ALL SELECT 104,1,'dppms:pm_project_product_line:1:2',FALSE
                  UNION ALL SELECT 201,2,'DPPMS:pm_project_product_line:1:1',FALSE
                )
                """;
    }

    private String sourceKey(String source, Fixture f) throws Exception {
        String sourceCte = """
                WITH pm_order_line_from_erp AS (
                  SELECT 1 id,'%s' source,'01' compCode,%d lineType,'ORDER' orderNumber,'%s' lineNum,
                    'PRODUCT' itemCode,'Product' itemDesc,2 orderQuantity,1 openQuantity,
                    NULL bundleCode,12 warrantyMonth,NULL profitCenter,NULL realOrderExecNumber,
                    CAST('2026-10-20 12:00:00' AS DATETIME) syncTime
                )
                """.formatted(source, f.type, f.line);
        var sourceKeys = select(P14SalesOrderSyncTask.SourceMapper.class.getName() + ".selectLine",
                Map.of("limit", 100), sourceCte, "source_key");
        assertEquals(1, sourceKeys.size());
        String sourceKey = sourceKeys.getFirst();
        assertTrue(sourceKey.matches("[0-9a-f]{64}"));
        return sourceKey;
    }

    private String targetCte(Fixture f, boolean emptyCatalogue) throws Exception {
        String sourceKey = sourceKey("SAP", f);
        if (f.bindingKeyWrong) sourceKey = "0".repeat(64);
        String duplicateLine = f.duplicate ? " UNION ALL SELECT 102,tenant_id,202,deleted,status,'D365',company_code,company_name,order_type,line_type,order_no,line_no,product_code FROM base_line" : "";
        String targetCte = """
                WITH base_line AS (
                  SELECT 101 id,%d tenant_id,201 order_id,FALSE deleted,'ENABLED' status,
                    '%s' source_system,'%s' company_code,'Company' company_name,
                    CAST('%d' AS CHAR) order_type,CAST('%d' AS CHAR) line_type,
                    'ORDER' order_no,'%s' line_no,'%s' product_code
                ), com_sales_order_line AS (
                  SELECT * FROM base_line%s
                %s
                ), base_order AS (
                  SELECT 201 id,%d tenant_id,FALSE deleted,'ENABLED' status,'%s' source_system,
                    '%s' company_code,'%s' order_type,'ORDER' order_no
                ), com_sales_order AS (
                  SELECT * FROM base_order
                %s
                ), int_base_sync_binding AS (
                  SELECT %d tenant_id,101 target_id,'%s' object_key,%s deleted,
                    '%s' source_key,301 task_id,401 last_run_id
                ), int_base_sync_task AS (
                  SELECT %d tenant_id,301 id,'%s' task_code,%d connection_id,%s deleted
                ), int_base_sync_run AS (
                  SELECT %d tenant_id,301 task_id,%d connection_id,%d id,'%s' task_code,
                    '%s' status,%s preview,%s deleted
                )
                """.formatted(f.tenant, f.source, f.company, f.type, f.type, f.line, f.product,
                emptyCatalogue ? " WHERE 1=0" : "", duplicateLine,
                f.tenant, f.source, f.company, f.parentType == null ? Integer.toString(f.type) : f.parentType,
                f.duplicate ? " UNION ALL SELECT 202,tenant_id,deleted,status,'D365',company_code,order_type,order_no FROM base_order" : "",
                f.tenant, f.objectKey, f.bindingDeleted, sourceKey, f.tenant, f.taskCode, f.connection,
                f.taskDeleted, f.tenant, f.runConnection, f.runId, f.runTaskCode, f.runStatus, f.preview, f.runDeleted);
        // SAP and D365 are distinct formal keys but ambiguous to P22's source-free lookup.
        if (f.duplicate) targetCte = targetCte.replace("401 last_run_id", "401 last_run_id UNION ALL SELECT "
                + f.tenant + ",102,'SALES_LINE',FALSE,'" + sourceKey("D365", f) + "',301,401");
        return targetCte;
    }

    private List<String> owned(Fixture f, boolean emptyCatalogue) throws Exception {
        var requested = new P22ProductScopeSyncTask.OrderKey("001", "ORDER", f.line, "PRODUCT");
        // Production reuses this exact OrderKey lookup over the once-loaded SQL catalogue.
        return catalogue(targetCte(f, emptyCatalogue)).stream()
                .filter(row -> requested.equals(new P22ProductScopeSyncTask.OrderKey(
                        row.getCompanyCode(), row.getOrderNo(), row.getLineNo(), row.getProductCode())))
                .map(row -> row.getId().toString()).toList();
    }

    private List<String> postfill(Fixture order, PostfillFixture f) throws Exception {
        var cte = targetCte(order, false)
                .replace("int_base_sync_binding AS (", "binding_seed AS (")
                .replace("int_base_sync_task AS (", "task_seed AS (") + """
                , int_base_sync_binding AS (
                  SELECT * FROM binding_seed
                  UNION ALL SELECT %d,1001,'%s',%s,NULL,501,%d
                ), int_base_sync_task AS (
                  SELECT *,NULL active_run_id FROM task_seed
                  UNION ALL SELECT %d,501,'%s',%d,%s,%d
                ), com_delivery_scope AS (
                  SELECT 1001 id,%d tenant_id,'001' project_company_code,'ORDER' order_no,
                    '1' line_no,'PRODUCT' product_code,%d order_line_id,%s deleted,
                    '%s' allocation_source,%s allocation_confirmed
                )
                """.formatted(f.bindingTenant, f.objectKey, f.bindingDeleted, f.bindingRun,
                f.taskTenant, f.taskCode, f.connection, f.taskDeleted, f.activeRun,
                f.scopeTenant, f.orderLineId, f.scopeDeleted, f.allocationSource, f.confirmed);
        var statement = configuration.getMappedStatement(
                P22ProductScopeSyncTask.TargetMapper.class.getName() + ".backfillOrderLineIds");
        assertEquals(SqlCommandType.UPDATE, statement.getSqlCommandType());
        var parameters = Map.of("query", new P22ProductScopeSyncTask.CurrentRunQuery(1, 33));
        var bound = statement.getBoundSql(parameters);
        // Execute only the production UPDATE's read relation, never its SET clause or DML.
        String sql = bound.getSql().replaceFirst("UPDATE com_delivery_scope s", "SELECT s.id FROM com_delivery_scope s")
                .replaceFirst("(?s) SET s\\.order_line_id=.*? WHERE ", " WHERE ");
        assertTrue(sql.startsWith("SELECT"));
        try (var prepared = connection.prepareStatement(cte + sql)) {
            prepared.setQueryTimeout(20);
            new DefaultParameterHandler(statement, parameters, bound).setParameters(prepared);
            try (var rows = prepared.executeQuery()) {
                var result = new ArrayList<String>();
                while (rows.next()) result.add(rows.getString("id"));
                return result;
            }
        }
    }

    private List<P22ProductScopeSyncTask.OrderLine> catalogue(String cte) throws Exception {
        var statement = configuration.getMappedStatement(
                P22ProductScopeSyncTask.TargetMapper.class.getName() + ".selectOwnedOrderCatalogue");
        assertEquals(SqlCommandType.SELECT, statement.getSqlCommandType());
        var parameters = Map.of("query", new P22ProductScopeSyncTask.OwnedOrderCatalogueQuery(1, 33));
        var bound = statement.getBoundSql(parameters);
        try (var prepared = connection.prepareStatement(cte + bound.getSql())) {
            prepared.setQueryTimeout(20);
            new DefaultParameterHandler(statement, parameters, bound).setParameters(prepared);
            try (var rows = prepared.executeQuery()) {
                var result = new ArrayList<P22ProductScopeSyncTask.OrderLine>();
                while (rows.next()) {
                    var row = new P22ProductScopeSyncTask.OrderLine();
                    row.setId(rows.getLong("id"));
                    row.setCompanyCode(rows.getString("company_code"));
                    row.setOrderNo(rows.getString("order_no"));
                    row.setLineNo(rows.getString("line_no"));
                    row.setProductCode(rows.getString("product_code"));
                    result.add(row);
                }
                return result;
            }
        }
    }

    private List<String> select(String id, Map<String, ?> parameters, String cte, String column) throws Exception {
        var statement = configuration.getMappedStatement(id);
        assertEquals(SqlCommandType.SELECT, statement.getSqlCommandType());
        var bound = statement.getBoundSql(parameters);
        try (var prepared = connection.prepareStatement(cte + bound.getSql())) {
            prepared.setQueryTimeout(20);
            new DefaultParameterHandler(statement, parameters, bound).setParameters(prepared);
            try (var rows = prepared.executeQuery()) {
                var result = new ArrayList<String>();
                while (rows.next()) result.add(rows.getString(column));
                return result;
            }
        }
    }

    private static class Fixture {
        int type;
        String line = "1", parentType, product = "PRODUCT";
        long tenant = 1, connection = 33, runConnection = 33, runId = 401;
        String source = "SAP", company = "001", objectKey = "SALES_LINE";
        String taskCode = "BASE_P14", runTaskCode = "BASE_P14", runStatus = "COMPLETED";
        boolean preview, bindingDeleted, taskDeleted, runDeleted, bindingKeyWrong, duplicate;
    }

    private static class PostfillFixture {
        long bindingTenant = 1, taskTenant = 1, scopeTenant = 1, connection = 33,
                bindingRun = 601, activeRun = 601, orderLineId = -1;
        String taskCode = "BASE_P22", objectKey = "PRODUCT_SCOPE", allocationSource = "DPPMS";
        boolean bindingDeleted, taskDeleted, scopeDeleted, confirmed;
    }
}
