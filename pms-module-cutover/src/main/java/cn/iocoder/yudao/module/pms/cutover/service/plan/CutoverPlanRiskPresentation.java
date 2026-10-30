package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 仅解释方案已冻结的结构化风险说明，不读取当前答案或改写原件。 */
@Service
@RequiredArgsConstructor
public class CutoverPlanRiskPresentation {
    private final CutoverChecklistItemMapper items;
    private final CutoverFieldDefinitionStore fields;

    public record Query(Long tenantId, CutoverPlanRevisionDO plan, JsonNode riskFact) { }

    public String describe(Query query) {
        String original = query.riskFact().path("factDescription").asText();
        JsonNode value;
        try { value = JsonUtils.parseTree(original); }
        catch (RuntimeException plainText) { return original; }
        if (value == null) return original;
        if (value.has("analysis")) value = value.path("analysis");
        if (value.has("value")) value = value.path("value");
        if (!value.isObject() || !value.has("executed")) return original;
        var plan = query.plan();
        if (TenantContextHolder.isIgnore() || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(query.tenantId(), plan.getTenantId()) || plan.getChecklistId() == null) {
            throw new IllegalArgumentException("方案风险字段来源不属于当前租户或清单");
        }
        var item = items.selectById(query.riskFact().path("checklistItemId").asLong());
        if (item == null || !Objects.equals(item.getTenantId(), query.tenantId())
                || !Objects.equals(item.getChecklistId(), plan.getChecklistId())) {
            throw new IllegalArgumentException("方案风险字段不属于原清单");
        }
        if (!"DUAL_MACHINE_CHECK".equals(item.getItemTypeCode())) return original;
        var schema = JsonUtils.parseTree(fields.withSchema(item).getInterfaceSchemaSnapshot());
        Map<String, String> labels = new LinkedHashMap<>();
        for (var field : schema.path("fields")) {
            String code = field.path("code").asText();
            labels.put(code, field.path("label").asText(field.path("name").asText(code)));
        }
        var lines = new java.util.ArrayList<String>();
        for (var field : value.properties()) {
            var answer = field.getValue();
            String text = answer.isBoolean() ? (answer.asBoolean() ? "是" : "否")
                    : answer.isNull() ? "—" : answer.isValueNode() ? answer.asText() : answer.toString();
            lines.add(labels.getOrDefault(field.getKey(), field.getKey()) + "：" + text);
        }
        return String.join("\n", lines);
    }
}
