package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in; only the newly-owned random schema is accepted. Existing 23316 tests/guards remain unchanged. */
@EnabledIfSystemProperty(named="npdms.ra.mysql.optIn",matches="true")
class RequirementAnalysisMysqlIsolatedTest extends RequirementAnalysisSpringPersistenceTest {
    @Test void originalMysqlDDLRetainsJsonUniqueForeignKeyAndCheckConstraints() {
        assertTrue(mysqlOptIn());
        assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME <> 'ra_test_environment' AND ENGINE='InnoDB'",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME IN ('ck_ra_revision_state','ck_ra_effective','ck_ra_revision_no')",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='fk_extension_definition'",Integer.class));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='plt_idempotency_record' AND INDEX_NAME='uk_plt_idempotency_scope' AND NON_UNIQUE=0",Integer.class));
        assertEquals("json",jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='plt_idempotency_record' AND COLUMN_NAME='response_payload'",String.class));
    }
}
