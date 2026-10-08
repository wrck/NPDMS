package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileQueryService;
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
import javax.sql.DataSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Production exact-key XML and registry; isolated Owner SPI, no grant-all registry mock. */
class FileReferenceDiscoveryPersistenceTest {
    private static final long ARTIFACT = 9_007_199_254_740_993L;
    private static final long REFERENCE = ARTIFACT + 2;
    private DataSource database;
    private SqlSession session;
    private JdbcTemplate jdbc;
    private FileQueryService service;
    private boolean ownerAllowed;
    private boolean providerUnavailable;
    private final FileQueryService.Actor actor = new FileQueryService.Actor(11L, 99L);

    @BeforeEach
    void isolatedDatabase() throws Exception {
        database = createDatabase();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("""
                CREATE TABLE plt_file_reference (
                  id BIGINT PRIMARY KEY, tenant_id BIGINT, owner_context VARCHAR(32), object_type VARCHAR(64),
                  object_id VARCHAR(128), purpose_code VARCHAR(128), reference_key VARCHAR(128),
                  artifact_id BIGINT, file_version_no INT, sensitivity_code VARCHAR(32), status_code VARCHAR(32),
                  scope_version BIGINT, version INT, detached_at TIMESTAMP, detached_by BIGINT,
                  detached_reason VARCHAR(255), archived_at TIMESTAMP, creator VARCHAR(64), create_time TIMESTAMP,
                  updater VARCHAR(64), update_time TIMESTAMP,
                  UNIQUE (tenant_id, owner_context, object_type, object_id, purpose_code, reference_key))
                """);
        jdbc.update("""
                INSERT INTO plt_file_reference
                (id, tenant_id, owner_context, object_type, object_id, purpose_code, reference_key,
                 artifact_id, file_version_no, status_code, sensitivity_code, scope_version, version)
                VALUES (?,11,'SOL','NATIVE_OWNER','9007199254740997','EVIDENCE','fixed-slot',?,1,'ACTIVE','INTERNAL',2,0)
                """, REFERENCE, ARTIFACT);
        var configuration = new Configuration(new Environment("isolated", new JdbcTransactionFactory(), database));
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/file/FileReferenceMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        // Reads from independent HTTP requests must not share a MySQL REPEATABLE READ transaction.
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        ownerAllowed = true;
        var provider = new FileBusinessObjectPolicyProvider() {
            public String ownerContext() { return "SOL"; }
            public String objectType() { return "NATIVE_OWNER"; }
            public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
                assertEquals("READ", query.requiredAction());
                if (providerUnavailable) throw new IllegalStateException("Owner unavailable");
                return new FileBusinessObjectPolicyFact(ownerAllowed && query.actorUserId().equals(99L),
                        2L, "MUTABLE", "SINGLE", Set.of("EVIDENCE"), Set.of("text/plain"), 50L, "INTERNAL");
            }
            public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
                throw new AssertionError("discovery must not mutate or lock Owner");
            }
        };
        service = new FileQueryService(new FileBusinessObjectPolicyRegistry(List.of(provider)), null, null,
                session.getMapper(FileReferenceMapper.class), null);
    }

    @AfterEach
    void closeDatabase() {
        if (session != null) session.close();
        if (database instanceof EmbeddedDatabase embedded) embedded.shutdown();
        else if (database != null) jdbc.execute("DROP TABLE IF EXISTS plt_file_reference");
    }

    protected DataSource createDatabase() {
        return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    }

    @Test
    void findsUploadedReferenceAfterCallerLosesArtifactIdWithoutChangingAnyFacts() {
        var result = service.getReference(key(null, "fixed-slot"), actor);
        assertEquals(ARTIFACT, result.getArtifactId());
        assertEquals(REFERENCE, result.getReferenceId());
        assertEquals("9007199254740997", result.getObjectId());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(0, result.getReferenceVersion());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_reference", Integer.class));
    }

    @Test
    void reflectsDetachedAndNewVersionFactsInsteadOfReturningAnActiveCachedReceipt() {
        assertEquals(1, service.getReference(key(null, "fixed-slot"), actor).getVersionNo());
        jdbc.update("UPDATE plt_file_reference SET status_code='DETACHED',version=1 WHERE id=?", REFERENCE);
        session.clearCache(); // independent HTTP requests use fresh SqlSessions
        assertEquals("DETACHED", service.getReference(key(null, "fixed-slot"), actor).getStatus());
        jdbc.update("UPDATE plt_file_reference SET status_code='ACTIVE',file_version_no=2,version=2 WHERE id=?", REFERENCE);
        session.clearCache();
        var current = service.getReference(key(null, "fixed-slot"), actor);
        assertEquals(2, current.getVersionNo());
        assertEquals(2, current.getReferenceVersion());
    }

    @Test
    void absentOrOtherTenantSlotCannotRevealTheUploadedReference() {
        assertNull(service.getReference(key(null, "other-slot"), actor));
        assertNull(service.getReference(key(null, "fixed-slot"), new FileQueryService.Actor(12L, 99L)));
        assertThrows(RuntimeException.class, () -> service.getReference(key(ARTIFACT + 1, "fixed-slot"), actor));
        assertThrows(RuntimeException.class, () -> service.getReference(key(ARTIFACT, "other-slot"), actor));
    }

    @Test
    void currentOwnerDenialAndUnavailableProviderFailClosedBeforeDatabaseLookup() {
        ownerAllowed = false;
        jdbc.execute("DROP TABLE plt_file_reference");
        assertNull(service.getReference(key(null, "fixed-slot"), actor));
        ownerAllowed = true;
        assertNull(service.getReference(key(null, "fixed-slot"), new FileQueryService.Actor(11L, 100L)));
        providerUnavailable = true;
        var failure = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.getReference(key(null, "fixed-slot"), actor));
        assertEquals(cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_PROVIDER_UNAVAILABLE.getCode(), failure.getCode());
    }

    @Test
    void invalidActorAndIncompleteKeysAreRejectedBeforeLookup() {
        assertThrows(RuntimeException.class, () -> service.getReference(key(null, ""), actor));
        assertThrows(RuntimeException.class, () -> service.getReference(key(0L, "fixed-slot"), actor));
        assertThrows(RuntimeException.class, () -> service.getReference(key(null, "fixed-slot"),
                new FileQueryService.Actor(null, 99L)));
        assertThrows(RuntimeException.class, () -> service.getReference(key(null, "fixed-slot"),
                new FileQueryService.Actor(11L, null)));
    }

    @Test
    void productionJsonModulePreservesSnowflakeIdentityAsStrings() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().addModule(
                new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
        var json = mapper.readTree(mapper.writeValueAsString(service.getReference(key(null, "fixed-slot"), actor)));
        assertTrue(json.get("artifactId").isString());
        assertEquals("9007199254740993", json.get("artifactId").asString());
        assertEquals("9007199254740995", json.get("referenceId").asString());
        assertEquals("9007199254740997", json.get("objectId").asString());
    }

    private FileQueryService.ArtifactQuery key(Long artifactId, String slot) {
        return new FileQueryService.ArtifactQuery(artifactId, "SOL", "NATIVE_OWNER", "9007199254740997", "EVIDENCE", slot);
    }
}
