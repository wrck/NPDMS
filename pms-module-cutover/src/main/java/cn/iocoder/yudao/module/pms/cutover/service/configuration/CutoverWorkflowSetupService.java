package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverWriteIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CutoverWorkflowSetupService {
    private final CutoverWorkflowApi workflow;
    private final CutoverCommandExecutor commands;

    public String initialize(CutoverWriteIntent intent) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        return commands.execute(CutoverCommandExecutor.Write.<String>builder().tenantId(tenant).actorId(actor)
                .operationCode("CUTOVER_WORKFLOW_INITIALIZE").scopeKey("DEFAULT")
                .aggregateType("CutoverApprovalWorkflow").intent(intent).businessInput("INITIALIZE")
                .responseType(String.class).operation(() -> workflow.provision(tenant, actor))
                .resourceKey(value -> value).build());
    }
}
