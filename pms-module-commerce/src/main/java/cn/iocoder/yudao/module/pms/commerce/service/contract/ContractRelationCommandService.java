package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectContractRelationMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractIdLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractIdentityLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContractRelationCommandService {

    private final OrganizationScopeApi organizationScopeApi;
    private final ProjectScopeApi projectScopeApi;
    private final ContractMapper contractMapper;
    private final ProjectContractRelationMapper relationMapper;
    private final OperationAuditApi operationAuditApi;

    @Transactional(rollbackFor = Exception.class)
    public ContractRelationResult relate(ContractRelationCommand command) {
        validate(command);
        requireProjectManageScope(command);
        List<UserCompanyDepartmentScopeRespDTO> scopes = readScopes(command.subjectUserId());
        if (scopes.isEmpty()) {
            throw ContractAccessService.inaccessible();
        }
        ContractDO contract = contractMapper.selectByIdForUpdate(
                new ContractIdLockQuery(command.tenantId(), command.contractId()));
        if (contract == null || contract.getCompanyCode() == null) {
            throw ContractAccessService.inaccessible();
        }
        List<UserCompanyDepartmentScopeRespDTO> matches = scopes.stream()
                .filter(Objects::nonNull)
                .filter(scope -> scope.getId() != null && scope.getVersion() != null)
                .filter(scope -> Objects.equals(scope.getCompanyCode(), contract.getCompanyCode()))
                .sorted(Comparator.comparing(UserCompanyDepartmentScopeRespDTO::getId))
                .toList();
        if (matches.isEmpty()) {
            throw ContractAccessService.inaccessible();
        }
        String role = command.relationRole() == null || command.relationRole().isBlank()
                ? "RELATED" : command.relationRole();
        ProjectContractIdentityLockQuery identity = new ProjectContractIdentityLockQuery(
                command.tenantId(), command.projectId(), command.contractId(), role);
        ProjectContractRelationDO existing = relationMapper.selectByIdentityForUpdate(identity);
        if (existing != null) {
            return new ContractRelationResult(existing.getId(), true);
        }
        ProjectContractRelationDO relation = new ProjectContractRelationDO();
        relation.setTenantId(command.tenantId());
        relation.setProjectId(command.projectId());
        relation.setContractId(command.contractId());
        relation.setRelationRole(role);
        relation.setSourceSystem("PMS");
        relation.setSourceTable("COM_PROJECT_CONTRACT_RELATION");
        relation.setSourceRecordKey(command.operationId());
        relation.setEffectiveFrom(LocalDateTime.now());
        relation.setStatus("ACTIVE");
        relation.setVersion(0);
        relationMapper.insert(relation);
        List<Map<String, Object>> authorizationSnapshot = matches.stream()
                .map(scope -> Map.<String, Object>of("scopeId", scope.getId(), "version", scope.getVersion()))
                .toList();
        operationAuditApi.record(command.tenantId(), command.subjectUserId(), command.operationId(),
                "COM_PROJECT_CONTRACT_RELATE", "ProjectContractRelation", String.valueOf(relation.getId()),
                "SUCCESS", Map.of("authorizationSnapshot", authorizationSnapshot,
                        "reason", command.reason(), "contractId", command.contractId(),
                        "projectId", command.projectId()));
        return new ContractRelationResult(relation.getId(), false);
    }

    private void requireProjectManageScope(ContractRelationCommand command) {
        ProjectScopeResult scope;
        try {
            scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                    command.tenantId(), command.subjectUserId(), command.projectId(), ProjectScopeApi.ACTION_MANAGE));
        } catch (RuntimeException exception) {
            throw ContractAccessService.inaccessible();
        }
        if (scope == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(command.projectId())) {
            throw ContractAccessService.inaccessible();
        }
    }

    private List<UserCompanyDepartmentScopeRespDTO> readScopes(Long subjectUserId) {
        try {
            List<UserCompanyDepartmentScopeRespDTO> scopes = organizationScopeApi.getActiveScopes(subjectUserId);
            return scopes == null ? List.of() : scopes;
        } catch (RuntimeException exception) {
            throw ContractAccessService.inaccessible();
        }
    }

    private void validate(ContractRelationCommand command) {
        if (command == null || command.tenantId() == null || command.subjectUserId() == null
                || command.contractId() == null || command.projectId() == null
                || command.operationId() == null || command.operationId().isBlank()
                || command.reason() == null || command.reason().isBlank()
                || command.relationRole() != null && !command.relationRole().isBlank()
                && !"RELATED".equals(command.relationRole())) {
            throw new IllegalArgumentException("COMMERCE_RELATION_INVALID_ARGUMENT");
        }
    }
}
