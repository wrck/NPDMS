package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectApprovalProcessCreationApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Objects;

/** Native BPM API takes a key, not a frozen ID plus business key; do not change that upstream contract. */
@Service
@RequiredArgsConstructor
public class PmsApprovalProcessCreationService implements ProjectApprovalProcessCreationApi {
    private final BpmProcessInstanceServiceImpl processes;
    private final RepositoryService repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @DataPermission(enable = false) // Original BPM user lookup semantics; start authorization remains in BPM.
    public String create(Command command) {
        if (command == null || !Objects.equals(command.tenantId(), TenantContextHolder.getRequiredTenantId())
                || command.actorId() == null || command.actorId() <= 0
                || command.definitionId() == null || command.definitionId().isBlank()
                || command.definitionKey() == null || command.definitionKey().isBlank()
                || command.businessKey() == null || command.businessKey().isBlank())
            throw new IllegalArgumentException("PROJECT_APPROVAL_CREATION_INVALID");
        var definition = repository.createProcessDefinitionQuery().processDefinitionId(command.definitionId())
                .processDefinitionTenantId(FlowableUtils.getTenantId()).active().singleResult();
        if (definition == null || !Objects.equals(command.definitionKey(), definition.getKey()))
            throw new IllegalArgumentException("PROJECT_APPROVAL_DEFINITION_UNAVAILABLE");
        var variables = command.variables() == null ? new HashMap<String, Object>() : new HashMap<>(command.variables());
        // https://www.flowable.com/open-source/docs/all-javadocs/org/flowable/engine/runtime/ProcessInstanceBuilder.html
        return FlowableUtils.executeAuthenticatedUserId(command.actorId(), () -> processes.createProcessInstance0(
                command.actorId(), definition, variables, command.businessKey(), command.selectedApprovers()));
    }
}
