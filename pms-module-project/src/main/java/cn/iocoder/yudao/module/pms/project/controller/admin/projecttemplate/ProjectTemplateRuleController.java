package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_RULE_SIMULATION_INVALID;

@RestController
@RequestMapping("/api/v1/pms/project-templates/rules")
@RequiredArgsConstructor
public class ProjectTemplateRuleController {
    private final ProjectRuleSimulationService simulation;

    @GetMapping("/fields")
    @Operation(summary = "模板规则可用项目字段目录，不读取实例数据")
    @PreAuthorize("@ss.hasAnyPermissions('pms:project-template:query', 'pms:project-plan:manage')")
    public CommonResult<List<ProjectRuleFields.Field>> fields() {
        return success(ProjectRuleFields.catalog());
    }

    public record SimulationRequest(@NotNull List<cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule> rules,
                                    @jakarta.validation.constraints.NotBlank String ruleKey,
                                    @NotNull Map<String, JsonNode> facts) { }

    @PostMapping("/simulate")
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    @Operation(summary = "使用模拟输入试算规则，不推进项目或业务状态")
    @PreAuthorize("@ss.hasAnyPermissions('pms:project-template:query', 'pms:project-plan:manage')")
    public CommonResult<ProjectRuleSimulationService.Simulation> simulate(@Valid @RequestBody SimulationRequest body) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        try {
            return success(simulation.simulate(tenantId, body.rules(), body.ruleKey(), body.facts()));
        } catch (RuntimeException invalid) {
            // The global unexpected-error handler persists request bodies. A read-only trial must not
            // send configured literals or hypothetical sensitive inputs to that handler, even on bad XML/EL.
            throw exception(PROJECT_TEMPLATE_RULE_SIMULATION_INVALID);
        }
    }
}
