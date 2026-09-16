package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationRuntimeCapability;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact-version deployment catalog. Listing metadata must never query or change an Owner object. */
@Component
public class ProjectBusinessOperationRegistry {
    private final Map<String, ProjectBusinessOperationDescriptor> descriptors;
    private final List<ProjectOperationRuntimeCapability> runtimeCapabilities;

    public ProjectBusinessOperationRegistry(List<ProjectBusinessOperationProvider> providers,
            List<ProjectOperationRuntimeCapability> runtimeCapabilities) {
        Map<String, ProjectBusinessOperationDescriptor> result = new LinkedHashMap<>();
        for (var provider : providers) {
            for (var descriptor : provider.operations()) {
                if (descriptor == null || Arrays.stream(descriptor.applicationService().getMethods())
                        .noneMatch(method -> method.getName().equals(descriptor.methodName())))
                    throw new IllegalStateException("OPERATION_TARGET_NOT_DEPLOYED");
                if (result.putIfAbsent(key(descriptor.operationCode(), descriptor.operationVersion()), descriptor) != null)
                    throw new IllegalStateException("OPERATION_DESCRIPTOR_DUPLICATE");
            }
        }
        this.descriptors = java.util.Collections.unmodifiableMap(result);
        this.runtimeCapabilities = List.copyOf(runtimeCapabilities);
    }

    public List<ProjectBusinessOperationDescriptor> all() {
        return descriptors.values().stream().sorted(java.util.Comparator
                .comparing(ProjectBusinessOperationDescriptor::operationCode)
                .thenComparingInt(ProjectBusinessOperationDescriptor::operationVersion)).toList();
    }

    public ProjectBusinessOperationDescriptor find(String code, int version) {
        return descriptors.get(key(code, version));
    }

    public boolean runtimeAvailable(String code, int version) {
        return find(code, version) != null
                && runtimeCapabilities.stream().filter(capability -> capability.supports(code, version)).count() == 1;
    }

    private static String key(String code, int version) { return code + "@" + version; }
}
