package cn.iocoder.yudao.module.pms.project.domain.template.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only deployment selection. A permission is not a unique command or an authorization result. */
public final class ProjectOperationPermissionIndex {
    public enum Status { RESOLVED, NOT_FOUND, AMBIGUOUS_OPERATION, AMBIGUOUS_VERSION, INVALID_REQUEST }
    public record Resolution(Status status, List<ProjectBusinessOperationDescriptor> candidates) {
        public Resolution { candidates = List.copyOf(candidates); }
        public ProjectBusinessOperationDescriptor selected() {
            return status == Status.RESOLVED && candidates.size() == 1 ? candidates.getFirst() : null;
        }
    }
    private record Key(String code, int version) { }
    private final Map<Key, ProjectBusinessOperationDescriptor> descriptors;
    private final Map<Key, String> permissions;
    private final List<ProjectBusinessOperationDescriptor> ordered;

    public ProjectOperationPermissionIndex(List<ProjectBusinessOperationProvider> providers) {
        if (providers == null) throw new IllegalStateException("OPERATION_CATALOG_UNAVAILABLE");
        Map<Key, ProjectBusinessOperationDescriptor> all = new LinkedHashMap<>();
        Map<Key, String> permissionIndex = new LinkedHashMap<>();
        for (var provider : providers) {
            if (provider == null) throw new IllegalStateException("OPERATION_PROVIDER_INVALID");
            var declared = provider.operations();
            var mapping = provider.permissionCodes();
            if (declared == null || mapping == null) throw new IllegalStateException("OPERATION_PROVIDER_INVALID");
            var ownCodes = new HashSet<String>();
            for (var descriptor : declared) {
                if (descriptor == null) throw new IllegalStateException("OPERATION_TARGET_NOT_DEPLOYED");
                var key = new Key(descriptor.operationCode(), descriptor.operationVersion());
                if (all.putIfAbsent(key, descriptor) != null) throw new IllegalStateException("OPERATION_DESCRIPTOR_DUPLICATE");
                ownCodes.add(descriptor.operationCode());
            }
            for (var entry : mapping.entrySet()) {
                if (blank(entry.getKey()) || blank(entry.getValue()) || !ownCodes.contains(entry.getKey()))
                    throw new IllegalStateException("OPERATION_PERMISSION_MAPPING_INVALID");
            }
            for (var descriptor : declared) {
                var permission = mapping.get(descriptor.operationCode());
                if (permission != null) permissionIndex.put(new Key(descriptor.operationCode(), descriptor.operationVersion()), permission);
            }
        }
        descriptors = Collections.unmodifiableMap(all);
        permissions = Collections.unmodifiableMap(permissionIndex);
        ordered = all.values().stream().sorted(Comparator.comparing(ProjectBusinessOperationDescriptor::operationCode)
                .thenComparingInt(ProjectBusinessOperationDescriptor::operationVersion)).toList();
    }

    public List<ProjectBusinessOperationDescriptor> all() { return ordered; }
    public ProjectBusinessOperationDescriptor find(String code, int version) { return descriptors.get(new Key(code, version)); }
    public String permissionCode(String code, int version) { return permissions.get(new Key(code, version)); }

    /** No implicit latest/version=1 selection. Optional code disambiguates a shared permission, not its versions. */
    public Resolution resolve(String owner, String objectType, String permission, String operationCode) {
        if (blank(owner) || blank(objectType) || blank(permission) || operationCode != null && blank(operationCode))
            return new Resolution(Status.INVALID_REQUEST, List.of());
        var candidates = ordered.stream()
                .filter(item -> owner.equals(item.ownerContext()) && objectType.equals(item.objectType())
                        && permission.equals(permissionCode(item.operationCode(), item.operationVersion()))
                        && (operationCode == null || operationCode.equals(item.operationCode())))
                .toList();
        if (candidates.isEmpty()) return new Resolution(Status.NOT_FOUND, candidates);
        if (candidates.size() == 1) return new Resolution(Status.RESOLVED, candidates);
        boolean sameOperation = candidates.stream().map(ProjectBusinessOperationDescriptor::operationCode).distinct().count() == 1;
        return new Resolution(sameOperation ? Status.AMBIGUOUS_VERSION : Status.AMBIGUOUS_OPERATION, candidates);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
