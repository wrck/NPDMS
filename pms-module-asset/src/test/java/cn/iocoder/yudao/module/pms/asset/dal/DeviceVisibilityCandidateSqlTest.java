package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceContractCandidateQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceProjectCandidatesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeviceVisibilityCandidateSqlTest {
    @Test void ownerContractsWithoutCandidateEnumerationStillRequireExactDeviceMatch() throws Exception {
        var config = configuration();
        try (var db = database("owner_contracts_without_candidates")) {
            try (var sql = db.createStatement()) {
                sql.execute("INSERT INTO ast_device VALUES (1,1,'exact',NULL,'C-1',false,NULL,NULL,NULL,NULL),"
                        + "(2,1,'lower',NULL,'c-1',false,NULL,NULL,NULL,NULL),"
                        + "(3,1,'space',NULL,'C-1 ',false,NULL,NULL,NULL,NULL),"
                        + "(4,1,'missing',NULL,NULL,false,NULL,NULL,NULL,NULL),"
                        + "(5,2,'foreign',NULL,'C-1',false,NULL,NULL,NULL,NULL),"
                        + "(6,1,'deleted',NULL,'C-1',true,NULL,NULL,NULL,NULL),"
                        + "(7,1,'unauthorized',NULL,'HIDDEN',false,NULL,NULL,NULL,NULL)");
            }
            var query = VisibleDevicePageQuery.builder().tenantId(1L).pageNo(1).pageSize(10)
                    .visibleProjectIds(Set.of()).organizationGrants(List.of())
                    .visibleContractNumbers(Set.of("C-1", "NO_DEVICE")).build();
            assertEquals(Set.of(1L), values(db, config, "selectVisibleDeviceCount", query, Long.class));
            assertEquals(values(db, config, "selectVisibleDeviceCount", query, Long.class),
                    values(db, config, "selectVisibleDeviceCount", query.toBuilder()
                            .visibleContractNumbers(Set.of("C-1")).build(), Long.class));
            assertEquals(Set.of(0L), values(db, config, "selectVisibleDeviceCount",
                    query.toBuilder().visibleContractNumbers(Set.of()).build(), Long.class));
        }
    }

    @Test void projectCandidateSupersetPreservesVisibilityAndFinalRelationshipDates() throws Exception {
        var config = configuration();
        try (var db = database("project_candidates")) {
            try (var sql = db.createStatement()) {
                sql.execute("INSERT INTO ast_device VALUES (1,1,'direct',10,NULL,false,NULL,NULL,NULL,NULL),"
                        + "(2,1,'related',NULL,NULL,false,NULL,NULL,NULL,NULL),"
                        + "(3,1,'future',NULL,NULL,false,NULL,NULL,NULL,NULL),"
                        + "(4,1,'expired',NULL,NULL,false,NULL,NULL,NULL,NULL),"
                        + "(5,1,'deleted',20,NULL,true,NULL,NULL,NULL,NULL),"
                        + "(6,2,'foreign',80,NULL,false,NULL,NULL,NULL,NULL)");
                sql.execute("INSERT INTO ast_device_project_relationship VALUES "
                        + "(1,'related',40,DATEADD('DAY',-1,CURRENT_TIMESTAMP),NULL,false),"
                        + "(1,'future',50,DATEADD('DAY',1,CURRENT_TIMESTAMP),NULL,false),"
                        + "(1,'expired',60,DATEADD('DAY',-2,CURRENT_TIMESTAMP),DATEADD('DAY',-1,CURRENT_TIMESTAMP),false),"
                        + "(1,'related',70,DATEADD('DAY',-1,CURRENT_TIMESTAMP),NULL,true),"
                        + "(2,'related',80,DATEADD('DAY',-1,CURRENT_TIMESTAMP),NULL,false)");
            }
            Set<Long> candidates = values(db, config, "selectProjectCandidates", new DeviceProjectCandidatesQuery(1L), Long.class);
            assertEquals(Set.of(10L, 40L, 50L, 60L), candidates);
            var original = VisibleDevicePageQuery.builder().tenantId(1L).pageNo(1).pageSize(10)
                    .visibleProjectIds(Set.of(10L, 40L, 50L, 60L, 70L, 80L, 99L))
                    .organizationGrants(List.of()).visibleContractNumbers(Set.of()).build();
            Set<Long> reduced = new HashSet<>(original.visibleProjectIds());
            reduced.retainAll(candidates);
            assertEquals(Set.of(2L), values(db, config, "selectVisibleDeviceCount", original, Long.class));
            assertEquals(values(db, config, "selectVisibleDeviceCount", original, Long.class),
                    values(db, config, "selectVisibleDeviceCount", original.toBuilder().visibleProjectIds(reduced).build(), Long.class));
            assertEquals(Set.of(), values(db, config, "selectProjectCandidates", new DeviceProjectCandidatesQuery(3L), Long.class));
        }
    }

    @Test void ownerLimitedContractCandidatesKeepCaseTenantDeletionOrganizationAndEmptyFilters() throws Exception {
        var config = configuration();
        try (var db = database("contract_candidates")) {
            try (var sql = db.createStatement()) {
                sql.execute("INSERT INTO ast_device VALUES (1,1,'direct',NULL,'C-1',false,NULL,NULL,NULL,NULL),"
                        + "(2,1,'lower',NULL,'c-1',false,NULL,NULL,NULL,NULL),"
                        + "(3,1,'hidden',NULL,'HIDDEN',false,NULL,NULL,NULL,NULL),"
                        + "(4,1,'deleted',NULL,'C-1',true,NULL,NULL,NULL,NULL),"
                        + "(5,2,'foreign',NULL,'C-1',false,NULL,NULL,NULL,NULL),"
                        + "(6,1,'organization',NULL,'C-1',false,20,NULL,NULL,'PROJECT')");
            }
            var query = DeviceContractCandidateQuery.builder().tenantId(1L)
                    .organizationGrants(List.of(new DeviceOrganizationGrant(20L, null, null)))
                    .authorizedContractNumbers(Set.of("C-1")).build();
            Set<String> candidates = values(db, config, "selectContractCandidates", query, String.class);
            assertEquals(Set.of("C-1", "c-1"), candidates);
            var exact = new HashSet<>(query.authorizedContractNumbers()); exact.retainAll(candidates);
            assertEquals(Set.of("C-1"), exact);
            assertEquals(Set.of(), values(db, config, "selectContractCandidates",
                    query.toBuilder().authorizedContractNumbers(Set.of()).build(), String.class));
            assertEquals(Set.of(), values(db, config, "selectContractCandidates",
                    query.toBuilder().matchingProjectIds(Set.of()).build(), String.class));
            assertEquals(Set.of("C-1"), values(db, config, "selectContractCandidates",
                    query.toBuilder().sn("direct").build(), String.class));
        }
    }

    private Configuration configuration() throws Exception {
        var config = new Configuration();
        ProjectDeviceListSqlFixture.load(config);
        try (var xml = getClass().getResourceAsStream("/mapper/device/DeviceQueryMapper.xml")) {
            new XMLMapperBuilder(xml, config, "device", config.getSqlFragments()).parse();
        }
        return config;
    }

    private Connection database(String name) throws Exception {
        var db = DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=MySQL");
        try (var sql = db.createStatement()) {
            sql.execute("CREATE TABLE ast_device(id BIGINT,tenant_id BIGINT,sn VARCHAR(40),project_id BIGINT,"
                    + "contract_no VARCHAR_IGNORECASE(40),deleted BOOLEAN,company_id BIGINT,department_id BIGINT,"
                    + "department_code VARCHAR(40),organization_source VARCHAR(40))");
            ProjectDeviceListSqlFixture.schema(sql);
            sql.execute("CREATE TABLE ast_device_project_relationship(tenant_id BIGINT,device_sn VARCHAR(40),"
                    + "project_id BIGINT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
        }
        return db;
    }

    private <T> Set<T> values(Connection db, Configuration config, String statement, Object query, Class<T> type) throws Exception {
        var parameters = Map.of("query", query);
        var bound = config.getMappedStatement("cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper." + statement)
                .getBoundSql(parameters);
        var sql = bound.getSql().replace("FORCE INDEX (idx_ast_device_visibility_cover)", "")
                .replace(" AS BINARY)", " AS VARBINARY(255))").replace("b'0'", "FALSE");
        try (var prepared = db.prepareStatement(sql)) {
            int index = 1;
            for (var mapping : bound.getParameterMappings()) {
                String key = mapping.getProperty();
                prepared.setObject(index++, bound.hasAdditionalParameter(key) ? bound.getAdditionalParameter(key)
                        : config.newMetaObject(parameters).getValue(key));
            }
            Set<T> values = new HashSet<>();
            try (var rows = prepared.executeQuery()) {
                while (rows.next()) values.add(type == Long.class ? type.cast(rows.getLong(1)) : type.cast(rows.getString(1)));
            }
            return values;
        }
    }
}
