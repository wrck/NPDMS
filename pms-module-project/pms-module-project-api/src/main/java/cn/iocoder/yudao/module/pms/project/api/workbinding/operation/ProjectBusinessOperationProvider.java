package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import java.util.List;
import java.util.Map;

/** Deployment metadata for real Owner application commands, not permission grants or runtime execution. */
public interface ProjectBusinessOperationProvider {
    List<ProjectBusinessOperationDescriptor> operations();

    /**
     * Optional authoring metadata: existing operation code -> native functional permission code.
     * This does not replace method/object authorization. Omitted entries retain exact legacy lookup,
     * but cannot be selected by a permission shorthand. Do not infer permissions from labels/actions.
     */
    default Map<String, String> permissionCodes() { return Map.of(); }
}
