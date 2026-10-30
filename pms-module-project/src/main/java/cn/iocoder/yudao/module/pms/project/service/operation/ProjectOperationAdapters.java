package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Lazy lookup avoids a registry -> Owner -> project runtime construction cycle. */
@Component
@RequiredArgsConstructor
public class ProjectOperationAdapters implements ProjectOperationRuntimeCapability {
    private final ObjectProvider<ProjectBusinessOperationCommandAdapter> adapters;
    private final ObjectProvider<ProjectOperationResultSink> sinks;
    public ProjectBusinessOperationCommandAdapter require(String code, int version) {
        var matches = adapters.orderedStream().filter(a -> a.supports(code, version)).toList();
        if (matches.size() != 1) throw new IllegalStateException("OPERATION_ADAPTER_NOT_UNIQUE");
        return matches.getFirst();
    }
    @Override public boolean supports(String code, int version) {
        return sinks.orderedStream().limit(2).count() == 1
                && adapters.orderedStream().filter(a -> a.supports(code, version)).limit(2).count() == 1;
    }
    public ProjectOperationResult invoke(String code, ProjectOperationCommand command) {
        return require(code, 1).invoke(code, command);
    }
}
