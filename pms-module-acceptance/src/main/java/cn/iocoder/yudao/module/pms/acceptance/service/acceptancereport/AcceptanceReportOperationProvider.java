package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;

/** Authoring metadata for the existing Owner commands; does not enable all-entry execution control. */
@Component
public class AcceptanceReportOperationProvider implements ProjectBusinessOperationProvider {
    @Override
    public List<ProjectBusinessOperationDescriptor> operations() {
        return List.of(
                new ProjectBusinessOperationDescriptor("ACC.ACCEPTANCE_REPORT.CREATE_DRAFT", 1, "ACC", "ACCEPTANCE", "创建验收报告草稿", "MANAGE",
                        Set.of("PRE", "POST"), AcceptanceReportCommandService.class, "createDraft"),
                new ProjectBusinessOperationDescriptor("ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT", 1, "ACC", "ACCEPTANCE", "保存验收报告草稿", "MANAGE",
                        Set.of("PRE", "POST"), AcceptanceReportCommandService.class, "updateDraft"),
                new ProjectBusinessOperationDescriptor("ACC.ACCEPTANCE_REPORT.PUBLISH", 1, "ACC", "ACCEPTANCE", "发布验收报告", "MANAGE",
                        Set.of("PRE", "POST"), AcceptanceReportCommandService.class, "publish"),
                new ProjectBusinessOperationDescriptor("ACC.ACCEPTANCE_REPORT.REVOKE", 1, "ACC", "ACCEPTANCE", "撤销验收报告", "MANAGE",
                        Set.of("PRE", "POST"), AcceptanceReportCommandService.class, "revoke"));
    }
}
