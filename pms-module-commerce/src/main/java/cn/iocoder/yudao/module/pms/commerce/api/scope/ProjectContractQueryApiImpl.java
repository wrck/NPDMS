package cn.iocoder.yudao.module.pms.commerce.api.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectCommerceMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectContractQueryApiImpl implements ProjectContractQueryApi {
    private final ProjectCommerceMapper mapper;

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
