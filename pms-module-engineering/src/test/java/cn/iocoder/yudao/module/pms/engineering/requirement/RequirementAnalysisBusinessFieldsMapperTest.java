package cn.iocoder.yudao.module.pms.engineering.requirement;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityProvider;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the real XML and JSON handlers; H2 is not evidence of MySQL migration acceptance. */
class RequirementAnalysisBusinessFieldsMapperTest {
    @Test void currentAndRevisionPersistTypedFieldsAndEnforceTenantVersionAndState() throws Exception {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("SET MODE MySQL");
            Path root = Path.of("").toAbsolutePath();
            while (!Files.isDirectory(root.resolve("sql/migrations"))) root = root.getParent();
            String original = Files.readString(root.resolve("sql/migrations/V248__entity_capabilities_and_requirement_revision.sql"));
            String added = Files.readString(root.resolve("sql/migrations/V321__requirement_analysis_business_fields.sql"));
            for (String table : List.of("sol_requirement_analysis", "sol_requirement_analysis_revision")) {
                var create = Pattern.compile("CREATE TABLE " + table + " \\(.*?\\n\\);", Pattern.DOTALL).matcher(original);
                assertTrue(create.find());
                jdbc.execute(h2(create.group()));
                var alter = Pattern.compile("ALTER TABLE " + table + "\\s.*?;", Pattern.DOTALL).matcher(added);
                assertTrue(alter.find());
                // H2 accepts one added column per statement.
                for (String column : alter.group().replaceFirst("ALTER TABLE " + table, "").replace(";", "").split(","))
                    jdbc.execute(h2("ALTER TABLE " + table + " " + column));
            }
            var config = new Configuration(new Environment("memory-only", new JdbcTransactionFactory(), database));
            config.setMapUnderscoreToCamelCase(true);
            String resource = "mapper/requirement/RequirementAnalysisMapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                String xml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                // Only adapt MySQL's BIT literal for H2; query predicates and mutations stay intact.
                new XMLMapperBuilder(new java.io.StringReader(xml.replace("b'0'", "FALSE")),
                        config, resource, config.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
                var mapper = session.getMapper(RequirementAnalysisMapper.class);
                var revision = new RequirementAnalysisRevisionDO();
                revision.setId(102L); revision.setEntityId(101L); revision.setTenantId(1L); revision.setProjectId(20L);
                revision.setRevisionNo(1); revision.setRevisionState("DRAFT"); revision.setDraftMarker(1);
                revision.setStatusCode("DRAFT"); revision.setVersion(1L); revision.setCreator("test"); revision.setUpdater("test");
                var values = Map.<String, Object>of("transmissionCurrentOptions", List.of("IPv6", "MTU"),
                        "trafficNewConnections", "100/s", "trafficConcurrency", "2000", "trafficThroughput", "1G",
                        "businessDeviceDetails", List.of(Map.of("deviceName", "设备甲", "serialNumber", "SN-1")),
                        "ipManagementResources", "10.0.0.1", "ipPublicResources", "待分配",
                        "operationsManagementOptions", List.of("SNMP", "堡垒机"));
                RequirementAnalysisEntityProvider.FIELDS.write(revision, values);
                assertEquals(1, mapper.insertRevision(revision));
                var restored = mapper.selectRevision(new RequirementRevisionQuery(1L, 102L));
                assertEquals(RequirementAnalysisEntityProvider.FIELDS.read(revision), RequirementAnalysisEntityProvider.FIELDS.read(restored));
                assertNull(mapper.selectRevision(new RequirementRevisionQuery(2L, 102L)));
                var current = BeanUtils.toBean(restored, RequirementAnalysisDO.class); current.setId(101L);
                assertEquals(1, mapper.insertCurrent(current));
                assertEquals(RequirementAnalysisEntityProvider.FIELDS.read(restored),
                        RequirementAnalysisEntityProvider.FIELDS.read(mapper.selectCurrent(new RequirementEntityQuery(1L, 101L))));
                restored.setTrafficConcurrency(null); restored.setBusinessDeviceDetails(List.of());
                assertEquals(1, mapper.saveDraft(restored));
                assertEquals(0, mapper.saveDraft(restored), "stale revision must not overwrite content");
                session.clearCache();
                var cleared = mapper.selectRevision(new RequirementRevisionQuery(1L, 102L));
                assertNull(cleared.getTrafficConcurrency()); assertTrue(cleared.getBusinessDeviceDetails().isEmpty());
                assertEquals("100/s", cleared.getTrafficNewConnections());
                assertEquals(1, mapper.freeze(new RequirementFreezeUpdate(1L, 102L, 2, 7L, java.time.LocalDateTime.now())));
                cleared.setVersion(3L);
                assertEquals(0, mapper.saveDraft(cleared), "frozen content is immutable");
            }
        } finally { database.shutdown(); }
    }
    private String h2(String sql) {
        return sql.replace("LONGTEXT", "CLOB").replace(" JSON ", " VARCHAR(100000) ")
                .replace("b'0'", "FALSE").replaceAll("COMMENT '[^']*'", "");
    }
}
