package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeDetailIdsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopePageQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommerceDeliveryScopeQueryService {

    private final ProjectScopeApi projectScopeApi;
    private final DeliveryScopeMapper scopeMapper;
    private final DeliveryScopeDetailMapper detailMapper;

    public PageResult<DeliveryScopeView> page(Long tenantId, Long subjectUserId, Long projectId,
                                               Long orderLineId, boolean includeHistory, int offset, int limit) {
        validate(tenantId, subjectUserId, projectId, orderLineId, offset, limit);
        List<Long> visibleProjectIds = visibleProjectIds(tenantId, subjectUserId);
        if (visibleProjectIds.isEmpty() || projectId != null && !visibleProjectIds.contains(projectId)) {
            return PageResult.empty();
        }
        DeliveryScopePageQuery query = new DeliveryScopePageQuery(
                tenantId, visibleProjectIds, projectId, orderLineId, includeHistory, offset, limit);
        long total = scopeMapper.selectCountByProjectScope(query);
        if (total == 0L) {
            return PageResult.empty();
        }
        List<DeliveryScopeDO> scopes = scopeMapper.selectPageByProjectScope(query);
        if (scopes.isEmpty()) {
            return new PageResult<>(List.of(), total);
        }
        List<Long> scopeIds = scopes.stream().map(DeliveryScopeDO::getId).toList();
        Map<Long, List<DeliveryScopeDetailDO>> detailsByScope = detailMapper.selectByScopeIds(
                        new DeliveryScopeDetailIdsQuery(tenantId, scopeIds)).stream()
                .collect(Collectors.groupingBy(DeliveryScopeDetailDO::getDeliveryScopeId));
        List<DeliveryScopeView> views = scopes.stream()
                .map(scope -> new DeliveryScopeView(scope,
                        List.copyOf(detailsByScope.getOrDefault(scope.getId(), List.of()))))
                .toList();
        return new PageResult<>(views, total);
    }

    private List<Long> visibleProjectIds(Long tenantId, Long subjectUserId) {
        Set<Long> projectIds;
        try {
            projectIds = projectScopeApi.resolveAllCurrent(new ProjectAllScopeQuery(
                    tenantId, subjectUserId, ProjectScopeApi.ACTION_VIEW));
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (projectIds == null) {
            return List.of();
        }
        return projectIds.stream().filter(java.util.Objects::nonNull)
                .sorted(Comparator.naturalOrder()).toList();
    }

    private void validate(Long tenantId, Long subjectUserId, Long projectId,
                          Long orderLineId, int offset, int limit) {
        if (tenantId == null || subjectUserId == null || projectId != null && projectId <= 0
                || orderLineId != null && orderLineId <= 0 || offset < 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("COMMERCE_SCOPE_QUERY_INVALID_ARGUMENT");
        }
    }
}
