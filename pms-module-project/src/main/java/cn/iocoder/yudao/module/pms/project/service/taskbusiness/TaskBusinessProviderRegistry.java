package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionKind;

@Component
public class TaskBusinessProviderRegistry {
    /** Configuration-time directory entry; labels only, never an authorization or a fact claim. */
    public record CompletionFactCatalogEntry(String ownerContext, String objectType, String factCode, String label) { }
    public record CompletionBinding(DeliveryDefinitionKind nodeKind, String bindingType,
                                    String ownerContext, String objectType) { }

    private final List<TaskBusinessObjectProvider> providers;
    public TaskBusinessProviderRegistry(List<TaskBusinessObjectProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    /** Directory of every completion fact declared by deployed Owner beans, for template configuration UIs. */
    public List<CompletionFactCatalogEntry> completionFactCatalog() {
        return providers.stream()
                .flatMap(provider -> provider.completionFactCodes().stream().map(code -> {
                    var labels = provider.completionFactLabels();
                    return new CompletionFactCatalogEntry(provider.ownerContext(), provider.objectType(), code,
                            labels == null ? code : labels.getOrDefault(code, code));
                }))
                .toList();
    }

    /** Independent rule publication: at least one unambiguous registered Owner declares the fact. */
    public boolean supportsCompletionFact(String factCode) {
        return factCode != null && providers.stream().anyMatch(p -> p.completionFactCodes().contains(factCode)
                && supportsCompletionFact(p.ownerContext(), p.objectType(), factCode));
    }

    /** Node composition: never borrow a same-named fact from another Owner or object type. */
    public boolean supportsCompletionFact(String ownerContext, String objectType, String factCode) {
        if (ownerContext == null || objectType == null || factCode == null) return false;
        var matches = providers.stream().filter(p -> ownerContext.equals(p.ownerContext())
                && objectType.equals(p.objectType())).toList();
        return matches.size() == 1 && matches.getFirst().completionFactCodes().contains(factCode);
    }

    /** An existing task fact does not imply that its Owner implements the stage-receiver contract. */
    public boolean supportsStageCompletionFact(String ownerContext, String objectType, String factCode) {
        return supportsCompletionFact(ownerContext, objectType, factCode)
                && require(ownerContext, objectType).supportsStageCompletionFacts();
    }

    /** Shared by asset composition and version-owned node rules; metadata only. */
    public boolean supportsBoundCompletionFact(CompletionBinding binding, String factCode) {
        if (binding == null || binding.bindingType() == null
                || !Set.of("BUSINESS_OBJECT", "BUSINESS_COMPONENT").contains(binding.bindingType())) return false;
        if (binding.nodeKind() == DeliveryDefinitionKind.STAGE)
            return supportsStageCompletionFact(binding.ownerContext(), binding.objectType(), factCode);
        return binding.nodeKind() == DeliveryDefinitionKind.TASK
                && supportsCompletionFact(binding.ownerContext(), binding.objectType(), factCode);
    }

    public TaskBusinessObjectProvider require(String ownerContext, String objectType) {
        if (ownerContext == null || objectType == null) throw TaskBusinessErrors.failure("OWNER_NOT_FROZEN");
        var matches = providers.stream().filter(p -> ownerContext.equals(p.ownerContext())
                && objectType.equals(p.objectType())).toList();
        if (matches.isEmpty()) throw TaskBusinessErrors.failure("OWNER_PROVIDER_NOT_REGISTERED");
        if (matches.size() != 1) throw TaskBusinessErrors.failure("OWNER_PROVIDER_AMBIGUOUS");
        return matches.getFirst();
    }
}
