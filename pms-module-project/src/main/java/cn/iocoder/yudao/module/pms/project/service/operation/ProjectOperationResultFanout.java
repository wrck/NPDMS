package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectOperationResultFanout {
    private final ProjectMasterMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper nodes;
    private final PlatformCommandExecutionApi commands;
    private final PlatformBusinessEventApi outbox;
    public record Receipt(List<String> recipients) { public Receipt { recipients = List.copyOf(recipients); } }

    @Transactional(rollbackFor = Exception.class)
    public Receipt accept(BusinessOperationResultEvent event) {
        event.requireEnvelope(event.eventId(),TenantContextHolder.getRequiredTenantId());
        var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(event.tenantId(),"PROJECT_RESULT_FANOUT",0L,event.eventId()),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(event)),Receipt.class,() -> fanout(event), receipt ->
                new PlatformCommandExecutionApi.SuccessFacts("PROJECT_RESULT_RECEIVED",event.objectType(),event.objectId(),event.correlationId(),
                        JsonUtils.toJsonString(receipt),null,null));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT || result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS)
            throw new IllegalStateException("BUSINESS_RESULT_RECEIPT_CONFLICT");
        if (result.response() == null) throw new IllegalStateException("BUSINESS_RESULT_RECEIPT_MISSING");
        return result.response();
    }
    private Receipt fanout(BusinessOperationResultEvent source) {
        var project = projects.selectByIdForUpdate(source.projectId());
        if (project == null || !source.tenantId().equals(project.getTenantId())) throw new IllegalArgumentException("RESULT_PROJECT_INVALID");
        var scope = new ProjectPlanScopeQuery(source.tenantId(),source.projectId());
        var plan = plans.selectEffective(scope);
        if (plan == null || !Objects.equals(plan.getId(),project.getActivePlanVersionId())) return new Receipt(List.of());
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(),TemplateExecutionSnapshot.class);
        var receipts = new ArrayList<String>();
        for (var round : nodes.selectCurrent(scope)) {
            if (!Objects.equals(source.tenantId(),round.getTenantId()) || !Objects.equals(plan.getId(),round.getPlanVersionId())) continue;
            List<TemplateExecutionSnapshot.BindingContract> matches;
            if ("TASK".equals(round.getNodeKind())) matches = snapshot.getTasks().stream()
                    .filter(n -> Objects.equals(n.getNodeKey(),round.getNodeKey())).map(TemplateExecutionSnapshot.TaskContract::getBinding).toList();
            else if ("STAGE".equals(round.getNodeKind())) matches = snapshot.getStages().stream()
                    .filter(n -> Objects.equals(n.getNodeKey(),round.getNodeKey())).map(TemplateExecutionSnapshot.StageContract::getBinding).toList();
            else continue;
            if (matches.size() != 1) throw new IllegalStateException("RESULT_BINDING_NOT_UNIQUE");
            var binding = matches.getFirst();
            if (binding == null || binding.getOperationContract() == null || !source.ownerContext().equals(binding.getTargetContextCode())
                    || !source.objectType().equals(binding.getTargetObjectType())) continue;
            // Object/range/round acceptance is deliberately left to the authoritative completion contract.
            String id = ProjectResultTargetEvent.id(source.eventId(),round.getNodeKind(),round.getNodeInstanceId(),plan.getId(),round.getId(),round.getContractId());
            var target = new ProjectResultTargetEvent(id,source,round.getNodeKind(),round.getNodeInstanceId(),round.getNodeKey(),plan.getId(),round.getId(),round.getContractId());
            outbox.append("ProjectResultTarget",round.getId().toString(),new BusinessEvent(id,ProjectResultTargetEvent.EVENT_TYPE,JsonUtils.toJsonString(target)));
            receipts.add(id);
        }
        // No subscription is a successful receipt, not a transport failure. Activation/recovery reads Owner facts again.
        return new Receipt(receipts);
    }
}
