package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TaskBindingHostRegistry {

    private final Map<String, TaskBindingHostProvider> providers;

    public TaskBindingHostRegistry(List<TaskBindingHostProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                TaskBindingHostProvider::bindingType, Function.identity()));
    }

    public Optional<TaskBindingHostProvider> providerFor(String bindingType) {
        return Optional.ofNullable(providers.get(bindingType));
    }

    public TaskBindingInspection inspect(String bindingType, TaskBindingInspectionQuery query) {
        TaskBindingHostProvider provider = providers.get(bindingType);
        if (provider == null) {
            return TaskBindingInspection.failed(bindingType, "BINDING_PROVIDER_UNREGISTERED");
        }
        try {
            TaskBindingInspection result = provider.inspect(query);
            return result == null
                    ? TaskBindingInspection.failed(bindingType, "BINDING_FACT_UNKNOWN") : result;
        } catch (RuntimeException ex) {
            return TaskBindingInspection.failed(bindingType, "BINDING_PROVIDER_UNAVAILABLE");
        }
    }
}
