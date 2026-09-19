package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Type;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/** Frozen subscription extraction, independent of commands, views and current business state. */
public final class ResultSubscriptionContract {
    private ResultSubscriptionContract() { }

    public record Node(String kind, String key, List<JsonNode> subscriptions) { }

    public static List<Node> nodes(TemplateExecutionSnapshot snapshot) {
        var result = new ArrayList<Node>();
        for (var stage : snapshot.getStages()) add(result, "STAGE", stage.getNodeKey(), stage.getExecution());
        for (var task : snapshot.getTasks()) add(result, "TASK", task.getNodeKey(), task.getExecution());
        return List.copyOf(result);
    }

    public static boolean present(TemplateExecutionSnapshot snapshot) { return !nodes(snapshot).isEmpty(); }

    public static TemplateExecutionConfiguration.Subscription read(String json) { return read(JsonUtils.parseTree(json)); }

    public static TemplateExecutionConfiguration.Subscription read(JsonNode json) {
        ObjectNode wrapper = (ObjectNode) JsonUtils.parseTree("{}");
        wrapper.putArray("subscriptions").add(json);
        return TemplateExecutionConfiguration.read(wrapper).subscriptions().getFirst();
    }

    public static Type type(TemplateExecutionConfiguration.Subscription subscription) {
        return new Type(subscription.ownerContext(), subscription.entityType(), subscription.resultType());
    }

    private static void add(List<Node> target, String kind, String key, JsonNode execution) {
        if (execution == null) return;
        var configuration = TemplateExecutionConfiguration.read(execution);
        if (configuration.subscriptions().isEmpty()) return;
        var rows = new ArrayList<JsonNode>();
        for (var row : execution.path("subscriptions")) rows.add(row.deepCopy());
        target.add(new Node(kind, key, List.copyOf(rows)));
    }
}
