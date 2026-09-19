package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Type;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration.Subscription;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectBusinessResultSources;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Validates declared Owner capabilities only; readiness of evidence and recovery is a separate publication gate. */
@Component
@RequiredArgsConstructor
public class TemplateResultSubscriptionCapabilities {
    private final ProjectBusinessResultSources sources;

    public List<Issue> validate(Subscription subscription, String path) {
        var descriptor = sources.descriptor(new Type(subscription.ownerContext(), subscription.entityType(), subscription.resultType()));
        if (descriptor == null) return List.of(new Issue(path, "RESULT_SOURCE_UNAVAILABLE", "没有对应Owner、实体和结果类型的受信来源"));
        var issues = new ArrayList<Issue>();
        var policy = subscription.policy();
        if ("PINNED_RESULT".equals(policy.acquisition()) && !descriptor.exactLookup())
            issues.add(new Issue(path + ".policy.acquisition", "RESULT_EXACT_LOOKUP_UNSUPPORTED", "来源不支持指定结果读取"));
        if ("HISTORICAL_FACT".equals(policy.validity()) && !descriptor.historicalLookup())
            issues.add(new Issue(path + ".policy.validity", "RESULT_HISTORY_UNSUPPORTED", "来源没有已核实的历史结果读取能力"));
        if ("REUSE_EXISTING".equals(policy.acquisition()) && !descriptor.currentLookup())
            issues.add(new Issue(path + ".policy.acquisition", "RESULT_CURRENT_LOOKUP_UNSUPPORTED", "来源不支持当前对象结果读取"));
        // Exact retained revisions do not prove when a concurrent result committed relative to this node round.
        if ("NEW_RESULT".equals(policy.acquisition()))
            issues.add(new Issue(path + ".policy.acquisition", "RESULT_FORMATION_BOUNDARY_UNAVAILABLE", "来源尚未接通可证明的提交形成边界"));
        return List.copyOf(issues);
    }
}
