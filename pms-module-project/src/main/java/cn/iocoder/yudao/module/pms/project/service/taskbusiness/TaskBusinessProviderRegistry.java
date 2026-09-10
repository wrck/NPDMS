package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class TaskBusinessProviderRegistry {
    private final List<TaskBusinessObjectProvider> providers;
    public TaskBusinessProviderRegistry(List<TaskBusinessObjectProvider> providers) {
        this.providers = List.copyOf(providers);
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

    public TaskBusinessObjectProvider require(String ownerContext, String objectType) {
        if (ownerContext == null || objectType == null) throw TaskBusinessErrors.failure("OWNER_NOT_FROZEN");
        var matches = providers.stream().filter(p -> ownerContext.equals(p.ownerContext())
                && objectType.equals(p.objectType())).toList();
        if (matches.isEmpty()) throw TaskBusinessErrors.failure("OWNER_PROVIDER_NOT_REGISTERED");
        if (matches.size() != 1) throw TaskBusinessErrors.failure("OWNER_PROVIDER_AMBIGUOUS");
        return matches.getFirst();
    }
}
