package cn.iocoder.yudao.module.pms.commerce.api.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectCommerceMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ExactContractProjectQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectContractQueryApiImpl implements ProjectContractQueryApi {
    private final ProjectCommerceMapper mapper;

    @Override
    public Set<String> getWarrantyShipmentContractNumbers(Long projectId) {
        if (projectId == null || projectId <= 0) throw new IllegalArgumentException("项目编号无效");
        return Set.copyOf(mapper.selectWarrantyShipmentContractNumbers(new ProjectCommerceQuery(
                TenantContextHolder.getRequiredTenantId(), projectId)));
    }

    @Override
    public List<ProjectContractFact> getProjectContractFacts(ProjectContractFactsQuery query) {
        if (query == null || query.projectIds() == null || query.effectiveAt() == null
                || query.projectIds().size() > 1000 || query.projectIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("项目合同投影查询无效");
        }
        if (query.projectIds().isEmpty()) return List.of();
        return mapper.selectProjectContractFacts(new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractFactsQuery(
                        TenantContextHolder.getRequiredTenantId(), query.projectIds(), query.effectiveAt())).stream()
                .map(row -> new ProjectContractFact(row.getProjectId(), row.isCurrentFlag(), row.getContractNo()))
                .toList();
    }

    @Override
    public Set<Long> findProjectIdsByExactContractNo(ExactContractQuery query) {
        if (query == null || query.contractNo() == null || query.contractNo().isBlank()
                || query.contractNo().length() > 512) throw new IllegalArgumentException("合同号无效");
        return Set.copyOf(mapper.selectProjectIdsByExactContractNo(new ExactContractProjectQuery(
                TenantContextHolder.getRequiredTenantId(), query.contractNo())));
    }

    @Override
    public Set<String> getCurrentContractNumbers(Long projectId) {
        if (projectId == null || projectId <= 0) throw new IllegalArgumentException("项目编号无效");
        return mapper.selectContracts(new ProjectCommerceQuery(
                        TenantContextHolder.getRequiredTenantId(), projectId)).stream()
                .map(row -> row.getContractNo())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim).collect(Collectors.toSet());
    }
}
