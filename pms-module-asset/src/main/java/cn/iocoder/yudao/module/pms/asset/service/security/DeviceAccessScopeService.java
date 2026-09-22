package cn.iocoder.yudao.module.pms.asset.service.security;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceVisibilityQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceContractCandidateQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class DeviceAccessScopeService {

    private final ProjectScopeApi projectScopeApi;
    private final DeviceMapper deviceMapper;
    private final cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi contractVisibilityApi;
    private final cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi organizationScopeApi;

    public Set<Long> visibleProjectIds(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return Set.of();
        }
        try {
            Set<Long> projectIds = projectScopeApi.resolveAllCurrent(
                    new ProjectAllScopeQuery(tenantId, userId, ProjectScopeApi.ACTION_VIEW));
            return projectIds == null ? Set.of() : Set.copyOf(projectIds);
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    private Set<String> visibleContractNumbers(Long tenantId, Long userId, Set<String> candidates) {
        if (tenantId == null || userId == null || candidates.isEmpty()) return Set.of();
        try {
            Set<String> numbers = contractVisibilityApi.getVisibleContractNumbers(tenantId, userId, candidates);
            return intersect(numbers, candidates);
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    public java.util.List<cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant> organizationGrants(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) return java.util.List.of();
        try {
            return organizationScopeApi.getActiveScopes(userId).stream()
                    .filter(s -> s != null && s.getId() != null && s.getVersion() != null && s.getCompanyId() != null)
                    .map(s -> new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant(
                            s.getCompanyId(), s.getDepartmentId(), s.getDepartmentCode())).distinct().toList();
        } catch (RuntimeException ex) { return java.util.List.of(); }
    }

    public Set<String> contractScope(Long tenantId, Long userId, DeviceContractCandidateQuery query) {
        if (tenantId == null || userId == null || !tenantId.equals(query.tenantId())) return Set.of();
        return visibleContractNumbers(tenantId, userId, deviceMapper.selectContractCandidates(query));
    }

    private Set<String> intersect(Set<String> numbers, Set<String> candidates) {
        if (numbers == null || numbers.isEmpty()) return Set.of();
        var result = new java.util.HashSet<>(numbers);
        result.retainAll(candidates);
        return Set.copyOf(result);
    }

    public void assertVisible(Long tenantId, Long userId, Long deviceId) {
        Set<Long> projectIds = visibleProjectIds(tenantId, userId);
        var grants = organizationGrants(tenantId, userId);
        Set<String> contractNumbers = contractScope(tenantId, userId,
                new DeviceContractCandidateQuery(tenantId, deviceId, null, null, null, null, null, null, grants));
        if ((projectIds.isEmpty() && contractNumbers.isEmpty() && grants.isEmpty()) || !deviceMapper.existsVisibleDevice(
                new DeviceVisibilityQuery(tenantId, deviceId, projectIds, contractNumbers, grants))) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
    }
}
