package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractJson;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Authoring can precede runtime delivery; publication/activation must never silently use the legacy executor. */
@Component
@RequiredArgsConstructor
public class TemplateOperationPublicationValidator {
    private final ProjectBusinessOperationRegistry registry;

    public List<Issue> validate(TemplateDesignerDocument source) {
        List<Issue> issues = new ArrayList<>();
        for (var binding : TemplateOperationCompilation.bindings(source)) {
            try {
                var contract = TemplateOperationContractJson.readAuthoring(binding.binding().getOperationContract());
                for (var operation : contract.operations()) {
                    if (!registry.runtimeAvailable(operation.operationCode(), operation.operationVersion()))
                        issues.add(new Issue(binding.path(), "OPERATION_RUNTIME_NOT_INSTALLED",
                                "操作 " + operation.operationCode() + " 的精确运行适配尚未安装，不能发布或生效；可继续保存草稿"));
                }
            } catch (RuntimeException invalid) {
                issues.add(new Issue(binding.path(), "OPERATION_CONTRACT_INVALID", "操作子契约不可解释"));
            }
        }
        return List.copyOf(issues);
    }
}
