package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContract;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractJson;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 在编译器私有副本上解析原权限，复用旧类型化操作和规则编译，不执行Owner命令。 */
@Component
@RequiredArgsConstructor
public class TemplateExecutionConfigurationCompilation {
    private final ProjectBusinessOperationRegistry registry;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TemplatePresentationRoutes presentationRoutes;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TemplateResultSubscriptionCapabilities resultCapabilities;

    public List<Issue> prepare(TemplateDesignerDocument normalized) {
        List<Issue> issues = new ArrayList<>();
        for (var node : TemplateExecutionConfiguration.nodes(normalized)) {
            var config = TemplateExecutionConfiguration.read(node.value());
            for (int i = 0; i < config.subscriptions().size(); i++) {
                String path = node.path() + ".subscriptions[" + i + "]";
                if (resultCapabilities == null)
                    issues.add(new Issue(path, "RESULT_SOURCE_UNAVAILABLE", "结果来源目录未安装"));
                else issues.addAll(resultCapabilities.validate(config.subscriptions().get(i), path));
            }
            if (!config.subscriptions().isEmpty())
                issues.add(new Issue(node.path() + ".subscriptions", "RESULT_SUBSCRIPTION_NOT_INSTALLED",
                        "独立结果订阅的来源、证据和恢复尚未接通；可保存草稿，不能发布"));
            if (config.presentation() != null) {
                try {
                    if (presentationRoutes == null) throw new IllegalArgumentException("PRESENTATION_ROUTE_NOT_INSTALLED");
                    presentationRoutes.validate(config.presentation(), node.binding());
                } catch (IllegalArgumentException invalid) {
                    issues.add(new Issue(node.path() + ".presentation", invalid.getMessage(),
                            "页面路径、冻结业务视图或参数不匹配已部署登记；可保留草稿，不能发布"));
                }
            }
            if (config.operations().isEmpty()) continue;
            List<TemplateOperationContract.Operation> resolved = new ArrayList<>();
            var unique = new HashSet<String>();
            for (int i = 0; i < config.operations().size(); i++) {
                var operation = config.operations().get(i);
                String path = node.path() + ".operations[" + i + "]";
                if (node.binding() == null || !Objects.equals(operation.ownerContext(), node.binding().getTargetContextCode())
                        || !Objects.equals(operation.entityType(), node.binding().getTargetObjectType())) {
                    issues.add(new Issue(path, "OPERATION_BINDING_MISMATCH", "操作Owner/实体与主绑定不一致"));
                    continue;
                }
                var resolution = registry.resolvePermission(operation.ownerContext(), operation.entityType(),
                        operation.permissionCode(), operation.operationCode());
                var selected = resolution.selected();
                if (selected == null) {
                    issues.add(new Issue(path, "OPERATION_" + resolution.status().name(), "原权限码不能唯一解析到业务动作"));
                    continue;
                }
                if (!unique.add(selected.operationCode())) {
                    issues.add(new Issue(path, "DUPLICATE_OPERATION", "多个配置解析到同一业务动作"));
                    continue;
                }
                if (!registry.runtimeAvailable(selected.operationCode(), selected.operationVersion())) {
                    issues.add(new Issue(path, "OPERATION_RUNTIME_NOT_INSTALLED", "所选动作的精确运行适配尚未安装"));
                    continue;
                }
                resolved.add(new TemplateOperationContract.Operation(selected.operationCode(), selected.operationVersion(),
                        operation.pre(), operation.post()));
                ((ObjectNode) node.value().path("operations").get(i)).put("operationCode", selected.operationCode());
            }
            if (resolved.size() == config.operations().size()) {
                node.binding().setOperationContract(TemplateOperationContractJson.authoring(
                        new TemplateOperationContract(TemplateOperationContract.VERSION, resolved)));
            }
        }
        return List.copyOf(issues);
    }
}
