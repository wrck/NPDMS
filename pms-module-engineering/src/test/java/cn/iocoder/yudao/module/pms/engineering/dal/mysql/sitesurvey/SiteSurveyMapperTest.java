package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyWriteCondition;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** FR-ENG-001：实际执行本聚合XML，验证CAS/隔离/软删除/字段保存。H2不替代MySQL并发验收。 */
class SiteSurveyMapperTest {
    private SqlSession session;
    private SiteSurveyMapper mapper;

    @BeforeEach void setUp() throws Exception {
        var dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:survey_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new Configuration(new Environment("site-survey-test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/sitesurvey/SiteSurveyMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(false);
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE pms_eng_site_survey (
                      id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
                      code VARCHAR(64), name VARCHAR(128), survey_date DATE, surveyor_user_id BIGINT, location VARCHAR(255),
                      address_id BIGINT, address_version INT, site_id BIGINT, site_version INT,
                      site_location_id BIGINT, site_location_version INT, location_resolution_status VARCHAR(16),
                      address_snapshot TEXT, location_snapshot TEXT,
                      power_supply VARCHAR(500), cabinet VARCHAR(500), network_port VARCHAR(500), fiber VARCHAR(500),
                      module VARCHAR(500), cable VARCHAR(500), ground VARCHAR(500), construction_resource VARCHAR(500),
                      conclusion VARCHAR(500), remark VARCHAR(500), status INT, version INT, deleted BOOLEAN DEFAULT FALSE,
                      creator VARCHAR(64), updater VARCHAR(64), create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO pms_eng_site_survey (id, tenant_id, project_id, code, name, status, version,
                      location, address_id, address_version, site_id, site_version, site_location_id, site_location_version,
                      location_resolution_status, address_snapshot, location_snapshot, creator)
                    VALUES (101, 1, 10, 'SUR-1', 'original', 0, 4, 'original-location', 11, 1, 21, 2, 31, 3,
                            'RESOLVED', 'original-address-snapshot', 'original-location-snapshot', 'original-creator')
                    """);
        }
        mapper = session.getMapper(SiteSurveyMapper.class);
    }

    @AfterEach void close() { if (session != null) session.close(); }

    @Test void rowQueriesAndLocksRequireCorrectTenantAndExcludeDeleted() throws Exception {
        assertNotNull(mapper.selectForUpdate(new SiteSurveyRowQuery(1L, 101L)));
        assertNull(mapper.selectForUpdate(new SiteSurveyRowQuery(2L, 101L)));
        assertNull(mapper.selectByRow(new SiteSurveyRowQuery(2L, 101L)));
        assertEquals(1, mapper.deleteDraftIfMatch(condition(4, 0)));
        assertNull(mapper.selectByRow(new SiteSurveyRowQuery(1L, 101L)));
        assertNull(mapper.selectForUpdate(new SiteSurveyRowQuery(1L, 101L)));
        try (var s = session.getConnection().createStatement(); var r = s.executeQuery(
                "SELECT deleted, version, address_snapshot, creator FROM pms_eng_site_survey WHERE id = 101")) {
            assertTrue(r.next()); assertTrue(r.getBoolean(1)); assertEquals(5, r.getInt(2));
            assertEquals("original-address-snapshot", r.getString(3)); assertEquals("original-creator", r.getString(4));
        }
    }

    @Test void draftSavePersistsAllOriginalFieldsAndClearsExplicitNullLocationStructure() {
        var row = draft();
        assertEquals(1, mapper.updateDraftIfMatch(condition(4, 0), row));
        var stored = mapper.selectByRow(new SiteSurveyRowQuery(1L, 101L));
        assertEquals(5, stored.getVersion()); assertEquals(0, stored.getStatus()); assertEquals(10L, stored.getProjectId());
        assertEquals("SUR-1", stored.getCode()); assertEquals("original-creator", stored.getCreator());
        assertEquals(row.getName(), stored.getName()); assertEquals(row.getSurveyDate(), stored.getSurveyDate());
        assertEquals(7L, stored.getSurveyorUserId()); assertEquals("new-location", stored.getLocation());
        assertEquals("供电", stored.getPowerSupply()); assertEquals("机柜", stored.getCabinet());
        assertEquals("网口", stored.getNetworkPort()); assertEquals("光纤", stored.getFiber());
        assertEquals("模块", stored.getModule()); assertEquals("线缆", stored.getCable()); assertEquals("接地", stored.getGround());
        assertEquals("施工资源", stored.getConstructionResource()); assertEquals("结论", stored.getConclusion());
        assertEquals("备注", stored.getRemark()); assertEquals("7", stored.getUpdater());
        assertNull(stored.getAddressId()); assertNull(stored.getAddressVersion()); assertNull(stored.getSiteId());
        assertNull(stored.getSiteVersion()); assertNull(stored.getSiteLocationId()); assertNull(stored.getSiteLocationVersion());
        assertNull(stored.getAddressSnapshot()); assertNull(stored.getLocationSnapshot());
        assertEquals("UNRESOLVED", stored.getLocationResolutionStatus());
        assertEquals(0, mapper.updateDraftIfMatch(condition(4, 0), row), "stale second write must fail CAS");
    }

    @Test void allWritesRejectTamperedIdentityAndTenant() {
        var conditions = new SiteSurveyWriteCondition[]{
                new SiteSurveyWriteCondition(2L, 101L, 10L, "SUR-1", 4, 0, "7"),
                new SiteSurveyWriteCondition(1L, 102L, 10L, "SUR-1", 4, 0, "7"),
                new SiteSurveyWriteCondition(1L, 101L, 20L, "SUR-1", 4, 0, "7"),
                new SiteSurveyWriteCondition(1L, 101L, 10L, "HACK", 4, 0, "7"),
                condition(3, 0), condition(4, 2)};
        for (var condition : conditions) {
            assertEquals(0, mapper.updateDraftIfMatch(condition, draft()));
            assertEquals(0, mapper.deleteDraftIfMatch(condition));
            assertEquals(0, mapper.updateStatusIfMatch(condition, 1));
        }
        assertEquals(4, mapper.selectByRow(new SiteSurveyRowQuery(1L, 101L)).getVersion());
    }

    @Test void transitionSqlPreservesFrozenSnapshotsAndRejectsWrongStateOrStaleVersion() {
        assertEquals(0, mapper.updateStatusIfMatch(condition(4, 0), 3));
        assertEquals(1, mapper.updateStatusIfMatch(condition(4, 0), 1));
        assertEquals(0, mapper.updateStatusIfMatch(condition(4, 0), 2));
        assertEquals(0, mapper.updateDraftIfMatch(condition(5, 1), draft()));
        assertEquals(0, mapper.deleteDraftIfMatch(condition(5, 1)));
        assertEquals(1, mapper.updateStatusIfMatch(condition(5, 1), 3));
        assertEquals(0, mapper.updateDraftIfMatch(condition(6, 3), draft()));
        assertEquals(0, mapper.deleteDraftIfMatch(condition(6, 3)));
        assertEquals(0, mapper.updateStatusIfMatch(condition(6, 3), 1));
        var stored = mapper.selectByRow(new SiteSurveyRowQuery(1L, 101L));
        assertEquals(3, stored.getStatus()); assertEquals(6, stored.getVersion());
        assertEquals("original-address-snapshot", stored.getAddressSnapshot());
        assertEquals("original-location-snapshot", stored.getLocationSnapshot()); assertEquals(31L, stored.getSiteLocationId());
    }

    @Test void rejectedAllowsContentOnlyAndNoDeleteOrImplicitResubmission() {
        assertEquals(1, mapper.updateStatusIfMatch(condition(4, 0), 2));
        assertEquals(1, mapper.updateDraftIfMatch(condition(5, 2), draft()));
        assertEquals(0, mapper.deleteDraftIfMatch(condition(6, 2)));
        assertEquals(0, mapper.updateStatusIfMatch(condition(6, 2), 0));
        assertEquals(0, mapper.updateStatusIfMatch(condition(6, 2), 1));
        assertEquals(2, mapper.selectByRow(new SiteSurveyRowQuery(1L, 101L)).getStatus());
    }

    @Test void initializationOnlyChangesVersionZeroDraftAndDoesNotBumpVersion() throws Exception {
        assertEquals(0, mapper.initializeLocationIfMatch(condition(4, 0), draft()));
        try (var statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE pms_eng_site_survey SET version = 0 WHERE id = 101");
        }
        assertEquals(1, mapper.initializeLocationIfMatch(condition(0, 0), draft()));
        var stored = mapper.selectByRow(new SiteSurveyRowQuery(1L, 101L));
        assertEquals(0, stored.getVersion()); assertEquals("original", stored.getName());
        assertEquals("new-location", stored.getLocation());
    }

    private SiteSurveyWriteCondition condition(int version, int status) {
        return new SiteSurveyWriteCondition(1L, 101L, 10L, "SUR-1", version, status, "7");
    }
    private SiteSurveyDO draft() {
        var row = new SiteSurveyDO(); row.setName("工勘"); row.setSurveyDate(LocalDate.of(2026, 9, 8));
        row.setSurveyorUserId(7L); row.setLocation("new-location"); row.setLocationResolutionStatus("UNRESOLVED");
        row.setPowerSupply("供电"); row.setCabinet("机柜"); row.setNetworkPort("网口"); row.setFiber("光纤");
        row.setModule("模块"); row.setCable("线缆"); row.setGround("接地"); row.setConstructionResource("施工资源");
        row.setConclusion("结论"); row.setRemark("备注"); return row;
    }
}
