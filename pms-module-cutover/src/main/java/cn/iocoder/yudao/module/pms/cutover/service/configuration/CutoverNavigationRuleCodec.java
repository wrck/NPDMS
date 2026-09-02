package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationSaveReqVO;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CutoverNavigationRuleCodec {

    private static final Set<String> EXACT_KEYS = Set.of("target");

    private CutoverNavigationRuleCodec() {
    }

    public static String encode(CutoverConfigurationSaveReqVO.NavigationRuleVO rule) {
        if (rule == null) {
            return null;
        }
        NavigationTarget target = parseTarget(rule.getTarget());
        return JsonUtils.toJsonString(Map.of("target", target.name()));
    }

    public static CutoverConfigurationSaveReqVO.NavigationRuleVO decode(String snapshot) {
        if (snapshot == null) {
            return null;
        }
        JsonNode root;
        try {
            root = JsonUtils.parseTree(snapshot);
        } catch (RuntimeException exception) {
            throw invalid(exception);
        }
        Set<String> fields = root != null && root.isObject()
                ? new HashSet<>(root.propertyNames()) : Set.of();
        if (root == null || !root.isObject() || !EXACT_KEYS.equals(fields) || !root.path("target").isTextual()) {
            throw invalid(null);
        }
        CutoverConfigurationSaveReqVO.NavigationRuleVO rule = new CutoverConfigurationSaveReqVO.NavigationRuleVO();
        rule.setTarget(parseTarget(root.path("target").textValue()).name());
        return rule;
    }

    public static NavigationTarget targetOrDefault(String snapshot) {
        CutoverConfigurationSaveReqVO.NavigationRuleVO rule = decode(snapshot);
        return rule == null ? NavigationTarget.CURRENT_STAGE_WORKBENCH : parseTarget(rule.getTarget());
    }

    private static NavigationTarget parseTarget(String value) {
        if (value == null || !value.equals(value.trim())) {
            throw invalid(null);
        }
        try {
            return NavigationTarget.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }
    }

    private static CutoverNavigationRuleException invalid(Throwable cause) {
        return new CutoverNavigationRuleException("导航规则快照非法", cause);
    }

    public enum NavigationTarget {
        CURRENT_STAGE_WORKBENCH,
        TASK_OVERVIEW
    }
}
