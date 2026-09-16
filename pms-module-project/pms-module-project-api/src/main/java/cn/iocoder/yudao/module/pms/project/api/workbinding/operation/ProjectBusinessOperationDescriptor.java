package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import java.util.Objects;
import java.util.Set;

/** The Java target is supplied by trusted code and is never returned as a configurable invocation path. */
public record ProjectBusinessOperationDescriptor(String operationCode, int operationVersion,
        String ownerContext, String objectType, String label, String ownerAction,
        Set<String> checkpoints, Class<?> applicationService, String methodName) {
    public ProjectBusinessOperationDescriptor {
        if (operationCode == null || operationCode.isBlank() || operationVersion < 1
                || ownerContext == null || ownerContext.isBlank() || objectType == null || objectType.isBlank()
                || label == null || label.isBlank() || ownerAction == null || ownerAction.isBlank()
                || checkpoints == null || !Set.of("PRE", "POST").containsAll(checkpoints)
                || methodName == null || methodName.isBlank())
            throw new IllegalArgumentException("OPERATION_DESCRIPTOR_INVALID");
        Objects.requireNonNull(applicationService, "applicationService");
        checkpoints = Set.copyOf(checkpoints);
    }
}
