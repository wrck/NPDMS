package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectCommerceSourceApi;
import cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectCommerceSourceBindCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectContractRelationMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractIdLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractRelationListQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractIdentityLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.ExecutionNoListQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.ExecutionPrimaryProjectUpdate;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ContractCreationOrderQuery;
import cn.iocoder.yudao.module.pms.commerce.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 项目创建的商务来源绑定实现（ADR-0032 MANDATORY 事务、ADR-0038 公司范围重验+授权快照）。
 * 合同行锁串行化同合同并发创建；老系统语义：同一合同号只能创建一个项目。
 */
@Service
@RequiredArgsConstructor
public class ProjectCommerceSourceServiceImpl implements ProjectCommerceSourceApi {

    private final OrganizationScopeApi organizationScopeApi;
    private final ContractMapper contractMapper;
    private final ProjectContractRelationMapper relationMapper;
    private final SalesOrderMapper orderMapper;
    private final CrmExecutionOrderMapper executionOrderMapper;
    private final OperationAuditApi operationAuditApi;
    private final ContractAccessService accessService;

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    public void bindProjectCommerceSource(ProjectCommerceSourceBindCommand command) {
        validate(command);
        ContractDO contract = contractMapper.selectByIdForUpdate(
                new ContractIdLockQuery(command.tenantId(), command.contractId()));
        if (contract == null || contract.getCompanyCode() == null) {
            throw exception(ErrorCodeConstants.COMMERCE_CREATION_SOURCE_CONTRACT_NOT_FOUND);
        }
        List<UserCompanyDepartmentScopeRespDTO> matches = readScopeMatches(
                command.subjectUserId(), contract.getCompanyCode());
        if (matches.isEmpty()) {
            throw ContractAccessService.inaccessible();
        }
        for (ProjectContractRelationDO existing : relationMapper.selectCurrentByContract(
                new ContractRelationListQuery(command.tenantId(), command.contractId()))) {
            if (!Objects.equals(existing.getProjectId(), command.projectId())) {
                throw exception(ErrorCodeConstants.COMMERCE_CONTRACT_ALREADY_BOUND);
            }
        }
        ProjectContractIdentityLockQuery identity = new ProjectContractIdentityLockQuery(
                command.tenantId(), command.projectId(), command.contractId(), "RELATED");
        ProjectContractRelationDO relation = relationMapper.selectByIdentityForUpdate(identity);
        boolean replay = relation != null;
        if (!replay) {
            relation = new ProjectContractRelationDO();
            relation.setTenantId(command.tenantId());
            relation.setProjectId(command.projectId());
            relation.setContractId(command.contractId());
            relation.setRelationRole("RELATED");
            relation.setSourceSystem("PMS");
            relation.setSourceTable("COM_PROJECT_CONTRACT_RELATION");
            relation.setSourceRecordKey(command.operationId());
            relation.setEffectiveFrom(LocalDateTime.now());
            relation.setStatus("ACTIVE");
            relation.setVersion(0);
            relationMapper.insert(relation);
        }
        bindExecutionOrders(command, contract);
        List<Map<String, Object>> authorizationSnapshot = matches.stream()
                .map(scope -> Map.<String, Object>of("scopeId", scope.getId(), "version", scope.getVersion()))
                .toList();
        operationAuditApi.record(command.tenantId(), command.subjectUserId(), command.operationId(),
                "COM_PROJECT_CREATION_BIND", "ProjectContractRelation", String.valueOf(relation.getId()),
                "SUCCESS", Map.of("authorizationSnapshot", authorizationSnapshot,
                        "contractId", command.contractId(), "projectId", command.projectId(),
                        "replay", replay));
    }

    private void bindExecutionOrders(ProjectCommerceSourceBindCommand command, ContractDO contract) {
        List<SalesOrderDO> orders = orderMapper.selectCreationOrdersByContract(
                new ContractCreationOrderQuery(command.tenantId(), command.contractId(),
                        contract.getContractNo(), contract.getCompanyCode()));
        List<String> executionNos = orders.stream().map(SalesOrderDO::getExecutionNo)
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        if (executionNos.isEmpty()) {
            return;
        }
        List<CrmExecutionOrderDO> executions = executionOrderMapper.selectActiveByExecutionNos(
                new ExecutionNoListQuery(command.tenantId(), executionNos));
        for (CrmExecutionOrderDO execution : executions) {
            if (execution.getPrimaryProjectId() != null
                    && !Objects.equals(execution.getPrimaryProjectId(), command.projectId())) {
                throw exception(ErrorCodeConstants.COMMERCE_EXECUTION_ORDER_ALREADY_BOUND,
                        execution.getExecutionNo());
            }
            int affected = executionOrderMapper.updatePrimaryProjectIfUnbound(
                    new ExecutionPrimaryProjectUpdate(command.tenantId(), execution.getId(),
                            command.projectId(), command.subjectUserId()));
            if (affected == 0) {
                // 并发下已被其他项目抢占：重读给出精确拒绝，事务整体回滚。
                CrmExecutionOrderDO current = executionOrderMapper.selectById(execution.getId());
                if (current != null && current.getPrimaryProjectId() != null
                        && !Objects.equals(current.getPrimaryProjectId(), command.projectId())) {
                    throw exception(ErrorCodeConstants.COMMERCE_EXECUTION_ORDER_ALREADY_BOUND,
                            current.getExecutionNo());
                }
                throw new IllegalStateException("COMMERCE_EXECUTION_ORDER_BIND_CONFLICT");
            }
        }
    }

    private List<UserCompanyDepartmentScopeRespDTO> readScopeMatches(Long subjectUserId, String companyCode) {
        List<UserCompanyDepartmentScopeRespDTO> scopes;
        try {
            scopes = organizationScopeApi.getActiveScopes(subjectUserId);
        } catch (RuntimeException exception) {
            throw ContractAccessService.inaccessible();
        }
        if (scopes == null) {
            return List.of();
        }
        return scopes.stream()
                .filter(Objects::nonNull)
                .filter(scope -> scope.getId() != null && scope.getVersion() != null)
                .filter(scope -> Objects.equals(scope.getCompanyCode(), companyCode))
                .sorted(Comparator.comparing(UserCompanyDepartmentScopeRespDTO::getId))
                .toList();
    }

    private void validate(ProjectCommerceSourceBindCommand command) {
        if (command == null || command.tenantId() == null || command.projectId() == null
                || command.contractId() == null || command.subjectUserId() == null
                || command.operationId() == null || command.operationId().isBlank()) {
            throw new IllegalArgumentException("COMMERCE_CREATION_BIND_INVALID_ARGUMENT");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CreationSourceResolution resolveCreationSource(ProjectCommerceSourceResolveCommand command) {
        if (command == null || command.tenantId() == null || command.contractId() == null
                || command.subjectUserId() == null) {
            throw new IllegalArgumentException("COMMERCE_CREATION_RESOLVE_INVALID_ARGUMENT");
        }
        ContractAccessService.CreationSourceDetail detail = accessService.getCreationSource(
                command.tenantId(), command.subjectUserId(),
                "CREATION_RESOLVE:" + command.contractId(), command.contractId());
        var primary = CreationSourceResolver.resolve(detail.orders(), detail.executionOrders());
        return new CreationSourceResolution(detail.contract().getId(), detail.contract().getContractNo(),
                detail.contract().getContractName(), detail.contract().getCompanyCode(),
                detail.contract().getCompanyName(), primary.projectName(),
                primary.customerProjectName(), primary.majorProjectLevel(), primary.projectType(),
                primary.marketCode(), primary.marketName(), primary.systemCode(), primary.systemName(),
                primary.expendCode(), primary.expendName(), primary.industryCode(), primary.industryName(),
                primary.executionOrderId(), primary.executionNo());
    }

    static ServiceException inaccessible() {
        return ContractAccessService.inaccessible();
    }
}
