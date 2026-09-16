package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;

/** Authoring metadata for the existing Owner commands; does not enable all-entry execution control. */
@Component
public class RequirementAnalysisOperationProvider implements ProjectBusinessOperationProvider {
    @Override
    public List<ProjectBusinessOperationDescriptor> operations() {
        return List.of(
                new ProjectBusinessOperationDescriptor("SOL.REQUIREMENT_ANALYSIS.CREATE", 1, "SOL", "REQUIREMENT_ANALYSIS", "创建需求分析草稿", "CREATE_INITIAL_DRAFT",
                        Set.of("PRE", "POST"), RequirementAnalysisEntityCommands.class, "create"),
                new ProjectBusinessOperationDescriptor("SOL.REQUIREMENT_ANALYSIS.SAVE", 1, "SOL", "REQUIREMENT_ANALYSIS", "保存需求分析修订", "PATCH_FORM",
                        Set.of("PRE", "POST"), RequirementAnalysisEntityCommands.class, "save"),
                new ProjectBusinessOperationDescriptor("SOL.REQUIREMENT_ANALYSIS.COMPLETE", 1, "SOL", "REQUIREMENT_ANALYSIS", "完成需求分析修订", "COMPLETE",
                        Set.of("PRE", "POST"), RequirementAnalysisEntityCommands.class, "complete"),
                new ProjectBusinessOperationDescriptor("SOL.REQUIREMENT_ANALYSIS.COPY", 1, "SOL", "REQUIREMENT_ANALYSIS", "复制需求分析修订", "CREATE_DRAFT",
                        Set.of("PRE", "POST"), RequirementAnalysisEntityCommands.class, "copy"));
    }
}
