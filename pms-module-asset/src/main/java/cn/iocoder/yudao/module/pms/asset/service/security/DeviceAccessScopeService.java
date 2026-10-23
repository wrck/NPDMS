package cn.iocoder.yudao.module.pms.asset.service.security;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DevicePageReadScope;
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
    private final ContractDeviceVisibilityApi contractVisibilityApi;
    private final cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi organizationScopeApi;
    private final ProjectViewReadScopeApi projectViewReadScopeApi;

    public DevicePageReadScope pageReadScope(
            ProjectAllScopeQuery query) {
        ProjectViewReadScopeApi.ReadScope projectScope = null;
        ContractDeviceVisibilityApi.ReadScope contractScope = null;
        if (query.tenantId() != null && query.subjectUserId() != null) {
            try { projectScope = projectViewReadScopeApi.prepare(query); }
            catch (RuntimeException exception) { /* Owner失败只关闭项目分支。 */ }
            try { contractScope = contractVisibilityApi.prepareReadScope(
                    new ContractDeviceVisibilityApi.ReadScopeQuery(
                            query.tenantId(), query.subjectUserId())); }
            catch (RuntimeException exception) { /* Owner失败只关闭合同公司分支。 */ }
        }
        return new DevicePageReadScope(projectScope, contractScope);
    }

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
        if (query.isUnfilteredPage()) {
            Set<String> numbers;
            try {
                numbers = contractVisibilityApi.getVisibleContractNumbers(tenantId, userId);
            } catch (RuntimeException ex) {
                return Set.of();
            }
            if (numbers == null || numbers.isEmpty()) return Set.of();
            // 默认页直接使用COM正式授权；最终可见SQL仍逐设备按合同原文精确匹配。
            // 无设备的合同自然不命中，无需先枚举全租户设备合同再求同一交集。
            return Set.copyOf(numbers);
        }
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
                DeviceContractCandidateQuery.builder()
                .tenantId(tenantId)
                .deviceId(deviceId)
                .organizationGrants(grants).build());
        if ((projectIds.isEmpty() && contractNumbers.isEmpty() && grants.isEmpty()) || !deviceMapper.existsVisibleDevice(
                new DeviceVisibilityQuery(tenantId, deviceId, projectIds, contractNumbers, grants))) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
    }
}
