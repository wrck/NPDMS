package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** V478_2 实际关系化草稿与 0807 第 30-84 行的独立业务期望对照；只读独立验证库。 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class Cutover0807MatchingMySqlTest {
    private static final long ORIGINAL = 970000000000442001L;
    private static final long DRAFT = 970000000000478001L;
    private static final List<String> TYPES = List.of("DEVICE_REPLACE_WHOLE", "DEVICE_REPLACE_BOARD", "DEVICE_REPLACE_VENDOR",
            "DEVICE_ONBOARD", "VERSION_UPGRADE", "DISASTER_RECOVERY_DRILL", "CONFIGURATION_CHANGE",
            "NETWORK_TOPOLOGY_CHANGE", "VERSION_PATCH", "SIGNATURE_UPGRADE");
    private static final List<String> MODES = List.of("", "VSM", "SILENT_DUAL", "DRP_DUAL", "NORMAL_DUAL", "CLUSTER");
    private static final List<String> DEVICES = List.of("FW", "SW", "ADX", "DPX");

    @Test
    void matchesAll13500CombinationsAndKeepsDOutsideP3() throws Exception {
        try (var db = connect()) {
            var configuration = configuration(db);
            assertEquals(133, configuration.items().size());
            assertEquals(133, configuration.rules().size());
            var dualKeys = configuration.items().stream().map(CutoverFrozenConfiguration.ItemDefinition::stableItemKey)
                    .filter(key -> key.startsWith("DUAL_")).collect(Collectors.toSet());
            assertEquals(97, dualKeys.size());
            var matcher = new CutoverChecklistMatcher();
            int checked = 0;
            for (var type : TYPES) for (var mode : MODES) for (int mask = 1; mask < 16; mask++) {
                var devices = new ArrayList<String>();
                for (int bit = 0; bit < 4; bit++) if ((mask & (1 << bit)) != 0) devices.add(DEVICES.get(bit));
                for (var grade : List.of("A", "B", "C", "D")) for (var major : List.of("", "无", "办事处级", "市场级", "公司级")) {
                    var input = new LinkedHashMap<String, List<String>>();
                    input.put("CUTOVER_TYPE", List.of(type)); input.put("DEVICE_TYPE", devices); input.put("CUTOVER_LEVEL", List.of(grade));
                    if (!mode.isEmpty()) input.put("NETWORK_MODE", List.of(mode));
                    if (!major.isEmpty()) input.put("PROJECT_MAJOR_LEVEL", List.of(major));
                    var matched = matcher.match(configuration, new CutoverChecklistMatcher.MatchInput(input));
                    assertTrue(matched.conflicts().isEmpty(), input::toString);
                    var actual = matched.readyItems().stream().map(value -> value.item().stableItemKey()).collect(Collectors.toSet());
                    if ("D".equals(grade)) {
                        assertTrue(actual.isEmpty(), input::toString);
                    } else {
                        var expected = expected(type, mode, devices, dualKeys);
                        if (Set.of("办事处级", "市场级", "公司级").contains(major)) expected.add("RISK_MAJOR_PROJECT_SPARES");
                        assertEquals(expected, actual, input::toString);
                        checked++;
                    }
                }
            }
            assertEquals(13500, checked);
        }
    }

    @Test
    void keepsPublishedHistoryAndUsesOneDualDecisionOnlyInNewDraft() throws Exception {
        try (var db = connect()) {
            assertEquals("PUBLISHED", scalar(db, "SELECT status_code FROM cut_cutover_configuration_revision WHERE id=" + ORIGINAL));
            assertEquals("DRAFT", scalar(db, "SELECT status_code FROM cut_cutover_configuration_revision WHERE id=" + DRAFT));
            assertEquals("97", scalar(db, "SELECT COUNT(*) FROM cut_checklist_field_definition f JOIN cut_cutover_checklist_item_definition_revision i ON i.id=f.item_definition_revision_id WHERE i.configuration_revision_id=" + ORIGINAL + " AND i.item_type_code='DUAL_MACHINE_CHECK' AND f.field_code='normal'"));
            assertEquals("0", scalar(db, "SELECT COUNT(*) FROM cut_checklist_field_definition f JOIN cut_cutover_checklist_item_definition_revision i ON i.id=f.item_definition_revision_id WHERE i.configuration_revision_id=" + DRAFT + " AND i.item_type_code='DUAL_MACHINE_CHECK' AND f.field_code='normal'"));
            assertEquals("97", scalar(db, "SELECT COUNT(*) FROM cut_checklist_field_definition f JOIN cut_cutover_checklist_item_definition_revision i ON i.id=f.item_definition_revision_id WHERE i.configuration_revision_id=" + DRAFT + " AND i.item_type_code='DUAL_MACHINE_CHECK' AND f.field_code='executed' AND f.label='是否已执行' AND f.required_flag=b'1'"));
            assertEquals("97", scalar(db, "SELECT COUNT(*) FROM cut_checklist_field_definition f JOIN cut_cutover_checklist_item_definition_revision i ON i.id=f.item_definition_revision_id WHERE i.configuration_revision_id=" + DRAFT + " AND i.item_type_code='DUAL_MACHINE_CHECK' AND f.field_code='remark' AND f.visible_when_field_path='executed' AND f.visible_equals_boolean=b'0'"));
            assertEquals("0", scalar(db, "SELECT COUNT(*) FROM cut_cutover_checklist_item_definition_revision WHERE configuration_revision_id=" + DRAFT + " AND (interface_schema IS NOT NULL OR external_source_config IS NOT NULL)"));
        }
    }

    private static Set<String> expected(String type, String mode, List<String> devices, Set<String> dualKeys) {
        var result = new HashSet<>(List.of("SURVEY_CUTOVER_BACKGROUND", "SURVEY_BUSINESS_SUMMARY", "SURVEY_PERFORMANCE_BASELINE",
                "SURVEY_CONNECTIVITY_TEST_CASE", "RISK_CURRENT_VERSION_BULLETIN", "RISK_SYSTEM_LOG", "RISK_DIAGNOSTIC_LOG",
                "RISK_RUNNING_VERSION_BACKUP", "RISK_HOT_PATCH_BACKUP", "RISK_LICENSE_BACKUP", "RISK_CONFIG_BACKUP", "RISK_ROOM_OPERATION_COMMITMENT"));
        if (!"DEVICE_ONBOARD".equals(type)) result.addAll(List.of("SURVEY_IMPACT_SCOPE", "SURVEY_CONTINUITY_REQUIREMENT", "SURVEY_INTERRUPTION_COUNT", "SURVEY_CURRENT_TOPOLOGY"));
        if (Set.of("DEVICE_REPLACE_WHOLE", "DEVICE_REPLACE_VENDOR", "DEVICE_ONBOARD", "NETWORK_TOPOLOGY_CHANGE").contains(type))
            result.addAll(List.of("SURVEY_DEVICE_LOCATION_PLAN", "SURVEY_INTERFACE_INTERCONNECT_PLAN", "SURVEY_IP_VLAN_PLAN"));
        if ("DEVICE_REPLACE_VENDOR".equals(type)) result.add("SURVEY_VENDOR_CONFIG_TRANSLATION");
        if (!mode.isEmpty()) dualKeys.stream().filter(key -> key.startsWith("DUAL_" + mode + "_R")).forEach(result::add);
        if (Set.of("NORMAL_DUAL", "SILENT_DUAL", "DRP_DUAL").contains(mode)) result.addAll(List.of("RISK_DUAL_CONFIG_CONSISTENCY", "RISK_SESSION_SYNC"));
        if ("VERSION_UPGRADE".equals(type)) {
            if (!mode.isEmpty()) result.add("RISK_TARGET_VERSION_BULLETIN");
            if (devices.contains("FW") || devices.contains("DPX")) result.addAll(List.of("RISK_FILTER_NAT_QOS_COMPILE_COUNT", "RISK_COMPILE_LIMIT_ASSESSMENT"));
        }
        if (Set.of("DEVICE_REPLACE_BOARD", "VERSION_UPGRADE").contains(type) && devices.contains("DPX")) result.add("RISK_DUAL_CONTROLLER_VERSION");
        if (Set.of("VERSION_UPGRADE", "VERSION_PATCH", "SIGNATURE_UPGRADE").contains(type)) result.add("RISK_PACKAGE_MD5");
        if (Set.of("DEVICE_REPLACE_WHOLE", "DEVICE_REPLACE_VENDOR", "VERSION_UPGRADE", "DISASTER_RECOVERY_DRILL", "CONFIGURATION_CHANGE").contains(type)) {
            result.addAll(List.of("RISK_DYNAMIC_TABLE_COLLECTION", "RISK_MTU_JUMBO_FRAME", "RISK_HUNDRED_G_FEC", "RISK_LONG_CONNECTION", "RISK_SECOND_PASS_DEVICE", "RISK_STP"));
            if (devices.contains("ADX")) result.addAll(List.of("RISK_F5_DEFAULT", "RISK_ADWARE_DEFAULT"));
        }
        return result;
    }

    private static CutoverFrozenConfiguration configuration(Connection db) throws SQLException {
        var conditions = new HashMap<Long, Map<String, List<String>>>();
        try (var statement = db.createStatement(); var rows = statement.executeQuery("SELECT c.binding_rule_revision_id,d.dimension_code,c.value_text FROM cut_checklist_binding_condition c JOIN cut_configuration_dimension d ON d.id=c.configuration_dimension_id WHERE d.configuration_revision_id=" + DRAFT + " ORDER BY c.value_no")) {
            while (rows.next()) conditions.computeIfAbsent(rows.getLong(1), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(rows.getString(2), ignored -> new ArrayList<>()).add(rows.getString(3));
        }
        var items = new ArrayList<CutoverFrozenConfiguration.ItemDefinition>();
        try (var statement = db.createStatement(); var rows = statement.executeQuery("SELECT * FROM cut_cutover_checklist_item_definition_revision WHERE configuration_revision_id=" + DRAFT + " AND status_code='ENABLED' AND deleted=b'0'")) {
            while (rows.next()) items.add(new CutoverFrozenConfiguration.ItemDefinition(rows.getLong("id"), rows.getString("stable_item_key"), rows.getInt("item_definition_version"), rows.getString("item_type_code"), rows.getString("item_name"), rows.getString("item_description"), rows.getString("interface_format_code"), null, rows.getString("work_mode_code"), rows.getBoolean("required_flag"), rows.getInt("sort_order")));
        }
        var rules = new ArrayList<CutoverFrozenConfiguration.BindingRule>();
        try (var statement = db.createStatement(); var rows = statement.executeQuery("SELECT * FROM cut_cutover_checklist_binding_rule_revision WHERE configuration_revision_id=" + DRAFT + " AND status_code='ENABLED' AND deleted=b'0'")) {
            while (rows.next()) rules.add(new CutoverFrozenConfiguration.BindingRule(rows.getLong("id"), rows.getString("stable_rule_key"), rows.getLong("item_definition_revision_id"), rows.getInt("item_definition_version"), JsonUtils.toJsonString(conditions.get(rows.getLong("id"))), rows.getInt("priority"), rows.getBoolean("required_result_flag"), rows.getInt("version")));
        }
        return new CutoverFrozenConfiguration(DRAFT, "CUTOVER_STANDARD_V1", 2, "DRAFT", null, null, items, rules);
    }

    private static String scalar(Connection db, String query) throws SQLException {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(query)) { assertTrue(rows.next()); return rows.getString(1); }
    }

    private static Connection connect() throws SQLException {
        var env = System.getenv(); var database = Objects.requireNonNull(env.get("NPDMS_DB_NAME"));
        if (!database.startsWith("npdms_cut_issues_")) throw new IllegalStateException("必须使用本次独立割接验证库");
        return DriverManager.getConnection("jdbc:mysql://127.0.0.1:" + env.get("NPDMS_MYSQL_PORT") + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8", env.get("NPDMS_DB_USER"), env.get("NPDMS_DB_PASSWORD"));
    }
}
