package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Type;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration.Subscription;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectBusinessResultSources;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Publication requires the Owner lookup, recovery and transactional change capabilities used by subscriptions. */
@Component
@RequiredArgsConstructor
public class TemplateResultSubscriptionCapabilities {
    private final ProjectBusinessResultSources sources;

    public List<Issue> validate(Subscription subscription, String path) {
        var type = new Type(subscription.ownerContext(), subscription.entityType(), subscription.resultType());
        var descriptor = sources.descriptor(type);
        if (descriptor == null) return List.of(new Issue(path, "RESULT_SOURCE_UNAVAILABLE", "没有对应Owner、实体和结果类型的受信来源"));
        var issues = new ArrayList<Issue>();
        var policy = subscription.policy();
        if ("PINNED_RESULT".equals(policy.acquisition()) && !descriptor.exactLookup())
            issues.add(new Issue(path + ".policy.acquisition", "RESULT_EXACT_LOOKUP_UNSUPPORTED", "来源不支持指定结果读取"));
        if ("HISTORICAL_FACT".equals(policy.validity()) && !descriptor.historicalLookup())
            issues.add(new Issue(path + ".policy.validity", "RESULT_HISTORY_UNSUPPORTED", "来源没有已核实的历史结果读取能力"));
        if (!sources.changeSupported(type))
            issues.add(new Issue(path, "RESULT_CHANGE_SOURCE_UNAVAILABLE", "来源未接通可靠变化读取"));
        if (!"PINNED_RESULT".equals(policy.acquisition()) && !sources.inventorySupported(type))
            issues.add(new Issue(path, "RESULT_INVENTORY_UNAVAILABLE", "来源未接通有界存量读取"));
        if (!"PINNED_RESULT".equals(policy.acquisition()) && "CURRENT_VALID".equals(policy.validity()) && !descriptor.currentLookup())
            issues.add(new Issue(path, "RESULT_CURRENT_LOOKUP_UNSUPPORTED", "来源不支持当前有效结果读取"));
        // The journal's locked transactional channel proves commit order, never MAX(id) or arrival time.
        if (!sources.commitBarrierSupported(type))
            issues.add(new Issue(path, "RESULT_COMMIT_BARRIER_UNAVAILABLE", "来源未覆盖结果变化的事务提交屏障"));
        return List.copyOf(issues);
    }
}
