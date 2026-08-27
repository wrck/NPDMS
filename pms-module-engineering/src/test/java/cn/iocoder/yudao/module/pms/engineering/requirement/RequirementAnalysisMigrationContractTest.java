package cn.iocoder.yudao.module.pms.engineering.requirement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementAnalysisMigrationContractTest {

    private static final String[] CORE_SECTION_CODES = {
            "PROJECT_BACKGROUND", "PROJECT_OBJECTIVE", "NETWORK_TOPOLOGY",
            "TRANSMISSION_REQUIREMENT", "TRAFFIC_REQUIREMENT", "BUSINESS_REQUIREMENT",
            "IP_PLANNING", "REDUNDANCY_REQUIREMENT", "SECURITY_PROTECTION",
            "OPERATIONS_REQUIREMENT", "LOGGING_REQUIREMENT"
    };

    private static String schemaSql;
    private static String seedSql;

    @BeforeAll
    static void loadMigrations() throws IOException {
        Path root = locateRepositoryRoot();
        schemaSql = normalizeLines(Files.readString(root.resolve(
                "sql/migrations/V99__fsol003_requirement_analysis.sql"), StandardCharsets.UTF_8));
        seedSql = normalizeLines(Files.readString(root.resolve(
                "sql/migrations/V100__fsol003_requirement_analysis_seed.sql"), StandardCharsets.UTF_8));
    }

    @Test
    void extendsPreparationAndCreatesOnlyTheRequirementAnalysisSectionTable() {
        assertTrue(schemaSql.contains("ALTER TABLE `sol_preparation`"));
        assertEquals(1, occurrences(schemaSql, "CREATE TABLE `sol_"));
        assertTrue(schemaSql.contains("CREATE TABLE `sol_requirement_analysis_section`"));
        for (String column : new String[]{"source_preparation_id", "draft_marker", "effective_marker",
                "content_version", "completed_by", "completed_at"}) {
            assertTrue(schemaSql.contains("ADD COLUMN `" + column + "`"), column);
        }
        for (String column : new String[]{"preparation_id", "source_section_id", "section_code",
                "section_name", "section_kind_code", "field_type_code", "required_flag",
                "dictionary_type", "sort_order", "schema_snapshot", "value_snapshot",
                "attachment_reference_snapshot", "version", "tenant_id"}) {
            assertTrue(schemaSql.contains("`" + column + "`"), column);
        }
    }

    @Test
    void definesIndependentDraftAndEffectiveMarkersWithTenantScopedUniqueness() {
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_sol_preparation_draft`\n"
                + "        (`tenant_id`, `project_id`, `preparation_type_code`, `draft_marker`)"));
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_sol_preparation_effective`\n"
                + "        (`tenant_id`, `project_id`, `preparation_type_code`, `effective_marker`)"));
        assertTrue(schemaSql.contains("'PRE_04_REQUIREMENT_ANALYSIS'"));
        assertTrue(schemaSql.contains("'DRAFT', 'COMPLETED'"));
        assertTrue(schemaSql.contains("`status_code` = 'DRAFT' AND `draft_marker` = 1"));
        assertTrue(schemaSql.contains("`status_code` = 'COMPLETED' AND `draft_marker` IS NULL"));
        assertTrue(schemaSql.contains("`effective_marker` IS NULL OR `effective_marker` = 1"));
        assertTrue(schemaSql.contains("`current_marker` IS NULL AND `content_version` IS NOT NULL"));
    }

    @Test
    void keepsEveryNewForeignKeyInsideSolAndTenantScoped() {
        assertTrue(schemaSql.contains("FOREIGN KEY (`tenant_id`, `source_preparation_id`)\n"
                + "        REFERENCES `sol_preparation` (`tenant_id`, `id`)"));
        assertTrue(schemaSql.contains("FOREIGN KEY (`tenant_id`, `preparation_id`)\n"
                + "        REFERENCES `sol_preparation` (`tenant_id`, `id`)"));
        assertTrue(schemaSql.contains("FOREIGN KEY (`tenant_id`, `source_section_id`)\n"
                + "        REFERENCES `sol_requirement_analysis_section` (`tenant_id`, `id`)"));
        assertFalse(schemaSql.contains("REFERENCES `proj_"));
        assertFalse(schemaSql.contains("REFERENCES `plt_"));
        assertFalse(schemaSql.contains("REFERENCES `system_"));
    }

    @Test
    void remainsForwardOnlyWithoutDroppingTablesColumnsOrBusinessRows() {
        String combined = schemaSql + "\n" + seedSql;
        assertFalse(Pattern.compile("(?im)^\\s*DROP\\s+TABLE(?!\\s+`_v100_)").matcher(combined).find());
        assertFalse(Pattern.compile("(?im)^\\s*ALTER\\s+TABLE[\\s\\S]*?DROP\\s+COLUMN")
                .matcher(combined).find());
        assertFalse(Pattern.compile("(?im)^\\s*(DELETE|TRUNCATE)\\s+").matcher(combined).find());
    }

    @Test
    void seedsTheExactElevenCoreSectionsAndThreeMandatoryRules() {
        String catalogSeed = seedSql.substring(
                seedSql.indexOf("'pms.sol.requirement-analysis.catalog.v1'"),
                seedSql.indexOf("-- 独立高段示例"));
        int payloadStart = catalogSeed.indexOf("{\"schemaVersion\":1");
        String payload = catalogSeed.substring(payloadStart, catalogSeed.indexOf("]]}", payloadStart) + 3);
        assertTrue(payload.contains("\"catalogCode\":\"PRE_04_REQUIREMENT_ANALYSIS\""));
        assertTrue(payload.contains("\"catalogVersion\":1"));
        assertEquals(11, occurrences(payload, "[\""));
        for (String code : CORE_SECTION_CODES) {
            assertEquals(1, occurrences(payload, "[\"" + code + "\""), code);
        }
        assertEquals(3, occurrences(payload, ",true]"));
        assertEquals(8, occurrences(payload, ",false]"));
    }

    @Test
    void seedsOnlyTheTwoStablePermissionsWithoutAutomaticRoleGrant() {
        assertEquals(1, occurrences(seedSql, "'pms:requirement-analysis:query'"));
        assertEquals(1, occurrences(seedSql, "'pms:requirement-analysis:manage'"));
        assertFalse(seedSql.contains("INSERT INTO `system_role_menu`"));
    }

    @Test
    void seedsNoExtensionAllApprovedFieldTypesAndDisabledDictionaryRejectionCases() {
        assertTrue(seedSql.contains("'{\"schemaVersion\":1,"
                + "\"catalogCode\":\"PRE_04_REQUIREMENT_ANALYSIS\",\"catalogVersion\":1,"
                + "\"extensionItems\":[]}'"));
        for (String fieldType : new String[]{"RICH_TEXT", "TEXT", "NUMBER", "BOOLEAN",
                "SINGLE_SELECT", "MULTI_SELECT"}) {
            assertTrue(seedSql.contains("\"fieldTypeCode\":\"" + fieldType + "\""), fieldType);
        }
        assertTrue(seedSql.contains("\"optionSnapshot\":[{\"code\":\"ENHANCED\","
                + "\"label\":\"增强等级\"},{\"code\":\"STANDARD\",\"label\":\"标准等级\"}]"));
        assertTrue(seedSql.contains("'DISABLED_OPTION', 'pms_requirement_analysis_extension_demo', 1"));
        assertTrue(seedSql.contains("'T-REQ-ANALYSIS-DISABLED-DEMO'"));
        assertTrue(seedSql.contains("\"code\":\"DISABLED_OPTION\""));
        assertTrue(seedSql.contains("ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`"));
    }

    @Test
    void seedsEachPublishedRequirementAnalysisTemplateAsACompleteInstantiableLifecycle() {
        String stageSeed = between("INSERT INTO `proj_project_template_stage_definition`",
                "INSERT INTO `proj_project_template_task_definition`");
        String taskSeed = between("INSERT INTO `proj_project_template_task_definition`",
                "INSERT INTO `proj_project_template_milestone_definition`");
        String milestoneSeed = between("INSERT INTO `proj_project_template_milestone_definition`",
                "INSERT INTO `proj_project_template_deliverable_definition`");
        String deliverableSeed = between("INSERT INTO `proj_project_template_deliverable_definition`",
                "INSERT INTO `proj_project_template_gate_definition`");
        String gateSeed = between("INSERT INTO `proj_project_template_gate_definition`",
                "INSERT INTO `proj_project_template_gate_reference`");
        String gateReferenceSeed = between("INSERT INTO `proj_project_template_gate_reference`",
                "INSERT INTO `system_menu`");

        for (String revisionId : new String[]{"992103050001", "992103050002"}) {
            assertEquals(1, occurrences(stageSeed, "(" + revisionId + ", 'S0',"), revisionId);
            for (String stageCode : new String[]{"S0", "S1", "S2", "S3", "S4", "S5", "S6"}) {
                assertTrue(stageSeed.contains("(" + revisionId + ", '" + stageCode + "',"),
                        revisionId + "/" + stageCode);
                assertTrue(taskSeed.contains("(" + revisionId + ", '" + stageCode + "',"),
                        revisionId + "/task/" + stageCode);
                assertTrue(gateSeed.contains("(" + revisionId + ", 'G-" + stageCode + "-EXIT',"),
                        revisionId + "/gate/" + stageCode);
                assertTrue(gateReferenceSeed.contains("(" + revisionId + ", 'G-" + stageCode + "-EXIT',"),
                        revisionId + "/gate-reference/" + stageCode);
            }
            assertTrue(taskSeed.contains("(" + revisionId + ", 'S1', 'T-REQ-ANALYSIS'"));
            assertTrue(milestoneSeed.contains("(" + revisionId + ", 'M-REQ-ANALYSIS'"));
            assertTrue(deliverableSeed.contains("(" + revisionId + ", 'D-REQ-ANALYSIS'"));
        }
    }

    @Test
    void doesNotMutateImmutablePublishedTemplateDefinitions() {
        String workBindingSeed = seedSql.substring(
                seedSql.indexOf("-- 独立高段示例"),
                seedSql.indexOf("INSERT INTO `system_menu`"));
        assertFalse(workBindingSeed.contains("UPDATE `proj_project_template_task_definition`"));
        assertFalse(workBindingSeed.contains("WHERE r.`status` = 'PUBLISHED'"));
    }

    @Test
    void seedsCatalogByStableKeyAndRestoresAnExistingRow() {
        String catalogSeed = seedSql.substring(
                seedSql.indexOf("-- config_key是目录身份"),
                seedSql.indexOf("-- 独立高段示例"));
        assertTrue(catalogSeed.contains("'pms.sol.requirement-analysis.catalog.v1'"));
        assertTrue(catalogSeed.contains("CREATE TEMPORARY TABLE `_v100_requirement_catalog_key_guard`"));
        assertTrue(catalogSeed.contains("WHERE NOT EXISTS"));
        assertTrue(catalogSeed.contains("UPDATE `infra_config`"));
        assertTrue(catalogSeed.contains("WHERE `config_key` = 'pms.sol.requirement-analysis.catalog.v1'"));
        assertFalse(catalogSeed.contains("(`id`,"));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        for (int offset = 0; (offset = source.indexOf(token, offset)) >= 0; offset += token.length()) {
            count++;
        }
        return count;
    }

    private static String between(String start, String end) {
        int startOffset = seedSql.indexOf(start);
        int endOffset = seedSql.indexOf(end, startOffset + start.length());
        assertTrue(startOffset >= 0, start);
        assertTrue(endOffset > startOffset, end);
        return seedSql.substring(startOffset, endOffset);
    }

    private static String normalizeLines(String source) {
        return source.replace("\r\n", "\n");
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/migrations"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("无法定位仓库根目录");
        }
        return current;
    }
}
