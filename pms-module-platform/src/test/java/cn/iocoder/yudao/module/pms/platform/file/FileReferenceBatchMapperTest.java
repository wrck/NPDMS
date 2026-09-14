package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceSetsQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceSetQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FileReferenceBatchMapperTest {
    private EmbeddedDatabase database;
    private SqlSession session;
    private FileReferenceMapper mapper;

    @BeforeEach
    void isolatedDatabase() throws Exception {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try (var connection = database.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
        }
        var jdbc = new JdbcTemplate(database);
        jdbc.execute("""
                CREATE TABLE plt_file_reference (
                  id BIGINT PRIMARY KEY, tenant_id BIGINT, owner_context VARCHAR(32), object_type VARCHAR(64),
                  object_id VARCHAR(64), purpose_code VARCHAR(128), reference_key VARCHAR(64),
                  artifact_id BIGINT, file_version_no INT, sensitivity_code VARCHAR(32), status_code VARCHAR(32),
                  scope_version BIGINT, version INT, detached_at TIMESTAMP, detached_by BIGINT,
                  detached_reason VARCHAR(255), archived_at TIMESTAMP, creator VARCHAR(64), create_time TIMESTAMP,
                  updater VARCHAR(64), update_time TIMESTAMP)
                """);
        insert(jdbc, 1L, 0L, "SOL", "CHANGE", "900", "EVIDENCE", "z", "ACTIVE");
        insert(jdbc, 2L, 0L, "SOL", "CHANGE", "900", "EVIDENCE", "a", "ACTIVE");
        insert(jdbc, 3L, 0L, "PLATFORM", "FORM", "31", "PHOTOS", "b", "ACTIVE");
        insert(jdbc, 4L, 1L, "SOL", "CHANGE", "900", "EVIDENCE", "c", "ACTIVE");
        insert(jdbc, 5L, 0L, "SOL", "CHANGE", "900", "EVIDENCE", "d", "DETACHED");
        insert(jdbc, 6L, 0L, "SOL", "FORM", "31", "PHOTOS", "e", "ACTIVE");
        insert(jdbc, 7L, 0L, "PLATFORM", "FORM", "32", "PHOTOS", "f", "ACTIVE");
        var configuration = new Configuration(new Environment("isolated", new JdbcTransactionFactory(), database));
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/file/FileReferenceMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(FileReferenceMapper.class);
    }

    @AfterEach
    void closeDatabase() {
        if (session != null) session.close();
        if (database != null) database.shutdown();
    }

    @Test
    void batchPreservesExactOwnerTuplesTenantStateAndReferenceOrdering() {
        var keys = List.of(new FileReferenceSetKey("SOL", "CHANGE", "900", "EVIDENCE"),
                new FileReferenceSetKey("PLATFORM", "FORM", "31", "PHOTOS"));
        var rows = mapper.selectActiveSets(new FileReferenceSetsQuery(0L, keys));
        assertEquals(List.of(2L, 3L, 1L), rows.stream().map(FileReferenceDO::getId).toList());
        for (var key : keys) {
            var original = mapper.selectActiveSet(new FileReferenceSetQuery(0L, key.ownerContext(),
                    key.objectType(), key.objectId(), key.purposeCode()));
            assertEquals(original, rows.stream().filter(row -> key.ownerContext().equals(row.getOwnerContext())).toList());
        }
        assertEquals(List.of(4L), mapper.selectActiveSets(new FileReferenceSetsQuery(1L, keys))
                .stream().map(FileReferenceDO::getId).toList());
    }

    @Test
    void absentKeysReturnNoRowsRatherThanBroadeningScope() {
        assertTrue(mapper.selectActiveSets(new FileReferenceSetsQuery(0L, List.of())).isEmpty());
        assertTrue(mapper.selectActiveSets(new FileReferenceSetsQuery(0L, null)).isEmpty());
    }

    private void insert(JdbcTemplate jdbc, Long id, Long tenant, String owner, String type, String object,
                        String purpose, String slot, String status) {
        jdbc.update("INSERT INTO plt_file_reference (id,tenant_id,owner_context,object_type,object_id,purpose_code,reference_key,status_code) VALUES (?,?,?,?,?,?,?,?)",
                id, tenant, owner, type, object, purpose, slot, status);
    }
}
