package cn.iocoder.yudao.module.pms.engineering.domain.preparation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_WORK_BINDING_NOT_AVAILABLE;

/** 将PROJ冻结的PRE-02 itemConfiguration收窄为SOL初始化事实。 */
public final class PreparationTemplateRules {

    private static final Set<String> ITEM_FIELDS = Set.of(
            "itemCode", "itemName", "enabled", "formCode", "formVersion", "evidenceRequired",
            "sourceRequirementCode", "waiverAllowed", "approvalRoleCode", "sortOrder");
    private static final Set<String> SOURCE_REQUIREMENTS = Set.of("NONE", "OA_REQUIRED");
    private static final Set<String> APPROVAL_ROLES = Set.of("SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");

    private PreparationTemplateRules() {
    }

    public static List<ItemDefinition> parse(String snapshot) {
        try {
            JsonNode root = JsonUtils.parseTree(snapshot);
            if (root == null || !root.isArray() || root.isEmpty()) throw invalid();
            Set<String> itemCodes = new HashSet<>();
            Set<Integer> sortOrders = new HashSet<>();
            java.util.ArrayList<ItemDefinition> definitions = new java.util.ArrayList<>();
            for (JsonNode node : root) {
                if (!node.isObject()) throw invalid();
                Set<String> fields = new HashSet<>();
                node.properties().forEach(entry -> fields.add(entry.getKey()));
                if (!fields.equals(ITEM_FIELDS)) throw invalid();
                ItemDefinition definition = JsonUtils.parseObject(node.toString(), ItemDefinition.class);
                validate(definition, itemCodes, sortOrders);
                definitions.add(definition);
            }
            return definitions.stream().sorted(Comparator.comparing(ItemDefinition::sortOrder)
                    .thenComparing(ItemDefinition::itemCode)).toList();
        } catch (RuntimeException ex) {
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) throw ex;
            throw invalid();
        }
    }

    private static void validate(ItemDefinition item, Set<String> itemCodes, Set<Integer> sortOrders) {
        if (item == null || blank(item.itemCode()) || blank(item.itemName()) || item.enabled() == null
                || blank(item.formCode()) || item.formVersion() == null || item.formVersion() <= 0
                || item.evidenceRequired() == null || !SOURCE_REQUIREMENTS.contains(item.sourceRequirementCode())
                || item.waiverAllowed() == null || !APPROVAL_ROLES.contains(item.approvalRoleCode())
                || item.sortOrder() == null || item.sortOrder() < 0
                || !itemCodes.add(item.itemCode()) || !sortOrders.add(item.sortOrder())) {
            throw invalid();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static cn.iocoder.yudao.framework.common.exception.ServiceException invalid() {
        return exception(PREPARATION_WORK_BINDING_NOT_AVAILABLE);
    }

    public record ItemDefinition(String itemCode, String itemName, Boolean enabled,
                                 String formCode, Integer formVersion, Boolean evidenceRequired,
                                 String sourceRequirementCode, Boolean waiverAllowed,
                                 String approvalRoleCode, Integer sortOrder) {
    }
}
