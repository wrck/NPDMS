package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationRuntimeCapability;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.ProjectOperationPermissionIndex;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** Exact-version deployment catalog. Listing metadata must never query or change an Owner object. */
@Component
public class ProjectBusinessOperationRegistry {
    private final ProjectOperationPermissionIndex index;
    private final List<ProjectOperationRuntimeCapability> runtimeCapabilities;

    public ProjectBusinessOperationRegistry(List<ProjectBusinessOperationProvider> providers,
            List<ProjectOperationRuntimeCapability> runtimeCapabilities) {
        index = new ProjectOperationPermissionIndex(providers);
        for (var descriptor : index.all()) {
            if (Arrays.stream(descriptor.applicationService().getMethods())
                    .noneMatch(method -> method.getName().equals(descriptor.methodName())))
                throw new IllegalStateException("OPERATION_TARGET_NOT_DEPLOYED");
        }
        this.runtimeCapabilities = List.copyOf(runtimeCapabilities);
    }

    public List<ProjectBusinessOperationDescriptor> all() { return index.all(); }
    public ProjectBusinessOperationDescriptor find(String code, int version) { return index.find(code, version); }
    public String permissionCode(String code, int version) { return index.permissionCode(code, version); }
    public ProjectOperationPermissionIndex.Resolution resolvePermission(String owner, String objectType,
            String permissionCode, String operationCode) {
        return index.resolve(owner, objectType, permissionCode, operationCode);
    }

    public boolean runtimeAvailable(String code, int version) {
        return find(code, version) != null
                && runtimeCapabilities.stream().filter(capability -> capability.supports(code, version)).count() == 1;
    }
}
