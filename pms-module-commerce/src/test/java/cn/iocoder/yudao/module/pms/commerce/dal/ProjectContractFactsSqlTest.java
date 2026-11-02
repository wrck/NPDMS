package cn.iocoder.yudao.module.pms.commerce.dal;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractFactsQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProjectContractFactsSqlTest {
    @Test
    void returnsCurrentAndHistoricalDirectRelationsWithoutCrossingTenantOrProjectScope() throws Exception {
        var config = new Configuration();
        try (var xml = getClass().getResourceAsStream("/mapper/contract/ProjectCommerceMapper.xml")) {
            new XMLMapperBuilder(xml, config, "contract/ProjectCommerceMapper.xml", config.getSqlFragments()).parse();
        }
        try (var db = DriverManager.getConnection("jdbc:h2:mem:contract_facts;MODE=MySQL")) {
            try (var s = db.createStatement()) {
                s.execute("CREATE TABLE com_contract(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(64),deleted INT)");
                s.execute("CREATE TABLE com_project_contract_relation(id BIGINT,tenant_id BIGINT,project_id BIGINT,contract_id BIGINT,contract_no VARCHAR(64),deleted INT,status VARCHAR(20),effective_from TIMESTAMP,effective_to TIMESTAMP)");
                s.execute("INSERT INTO com_contract VALUES(1,1,'MASTER',0),(2,2,'OTHER',0),(3,1,'DELETED',1)");
                s.execute("INSERT INTO com_project_contract_relation VALUES " +
                        "(1,1,10,1,'OLD',0,'ACTIVE',NULL,NULL)," +
                        "(2,1,10,NULL,'SOURCE',0,'ACTIVE',NULL,NULL)," +
                        "(3,1,10,1,NULL,0,'ACTIVE',NULL,'2020-01-01 00:00:00')," +
                        "(4,1,10,3,NULL,0,'ACTIVE',NULL,NULL)," +
                        "(5,1,10,1,NULL,1,'ACTIVE',NULL,NULL)," +
                        "(6,2,10,2,NULL,0,'ACTIVE',NULL,NULL)," +
                        "(7,1,11,1,NULL,0,'ACTIVE',NULL,NULL)");
            }
            var query = new ProjectContractFactsQuery(1L, Set.of(10L), LocalDateTime.of(2026, 1, 1, 0, 0));
            var parameters = Map.of("query", query);
            var mapped = config.getMappedStatement("cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectCommerceMapper.selectProjectContractFacts");
            var bound = mapped.getBoundSql(parameters);
            try (var stmt = db.prepareStatement(bound.getSql())) {
                int i = 1;
                for (var parameter : bound.getParameterMappings()) {
                    var key = parameter.getProperty();
                    stmt.setObject(i++, bound.hasAdditionalParameter(key) ? bound.getAdditionalParameter(key) : config.newMetaObject(parameters).getValue(key));
                }
                try (var rows = stmt.executeQuery()) {
                    assertTrue(rows.next()); assertEquals("MASTER", rows.getString("contract_no")); assertTrue(rows.getBoolean("current_flag"));
                    assertTrue(rows.next()); assertEquals("SOURCE", rows.getString("contract_no")); assertTrue(rows.getBoolean("current_flag"));
                    assertTrue(rows.next()); assertFalse(rows.getBoolean("current_flag"));
                    assertTrue(rows.next()); assertNull(rows.getString("contract_no")); assertTrue(rows.getBoolean("current_flag"));
                    assertTrue(rows.next()); assertFalse(rows.getBoolean("current_flag"));
                    assertFalse(rows.next());
                }
            }
            var empty = mapped.getBoundSql(Map.of("query", new ProjectContractFactsQuery(1L, Set.of(), query.effectiveAt())));
            assertTrue(empty.getSql().contains("1 = 0"));
        }
    }
}
