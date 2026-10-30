package cn.iocoder.yudao.module.pms.project.service.projectselection;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceShipmentApi;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactApi;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactException;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.CustomerServiceLevelFactQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

/** 当前主体的项目选择资料；不承接消费业务的创建资格、冻结快照或写事务。 */
@Service
@RequiredArgsConstructor
public class ProjectSelectionQueryService {
    private final ProjectBusinessContextApi projects;
    private final ProjectScopeApi scopes;
    private final ProjectDeviceShipmentApi shipments;
    private final ObjectProvider<CustomerServiceLevelFactApi> customerLevels;

    public record Actor(Long tenantId, Long actorId) {}
    public record OptionsQuery(Actor actor, String keyword) {}
    public record ProjectQuery(Actor actor, Long projectId) {}
    public record DevicePageQuery(Actor actor, Long projectId, String keyword, int pageNo, int pageSize) {}
    public record ProjectContext(ProjectBusinessContextApi.Presentation project, String customerServiceLevel) {}

    public List<ProjectBusinessContextApi.Option> options(OptionsQuery query) {
        requireActor(query.actor());
        return projects.searchOptions(ProjectBusinessContextApi.OptionsQuery.builder()
                .tenantId(query.actor().tenantId()).actorId(query.actor().actorId()).keyword(query.keyword()).build());
    }

    public ProjectContext context(ProjectQuery query) {
        var ownerQuery = authorizedProject(query);
        var context = projects.inspect(ownerQuery);
        var presentation = projects.presentation(ownerQuery);
        String level = null;
        var provider = customerLevels.getIfAvailable();
        if (context.customerId() != null && provider != null) {
            try {
                var result = provider.inspectCurrent(new CustomerServiceLevelFactQuery(query.actor().tenantId(), context.customerId()));
                var fact = result.currentFact();
                if (!Objects.equals(fact.tenantId(), query.actor().tenantId())
                        || !Objects.equals(fact.customerId(), context.customerId())
                        || result.decision() == cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.CustomerServiceLevelFactResult.Decision.STALE)
                    throw new CustomerServiceLevelFactException(CustomerServiceLevelFactException.Code.OWNER_DATA_CORRUPTED,
                            "客户服务等级事实与项目客户不一致");
                level = fact.serviceLevelCode();
            } catch (CustomerServiceLevelFactException error) {
                if (error.getCode() != CustomerServiceLevelFactException.Code.PROVIDER_UNAVAILABLE) throw error;
            }
        }
        return new ProjectContext(presentation, level);
    }

    public ProjectDeviceShipmentApi.DevicePage devices(DevicePageQuery query) {
        authorizedProject(new ProjectQuery(query.actor(), query.projectId()));
        return shipments.pageDevices(ProjectDeviceShipmentApi.DeviceQuery.builder()
                .tenantId(query.actor().tenantId()).actorId(query.actor().actorId()).projectId(query.projectId())
                .keyword(query.keyword()).pageNo(query.pageNo()).pageSize(query.pageSize()).build());
    }

    private ProjectBusinessContextApi.Query authorizedProject(ProjectQuery query) {
        requireActor(query.actor());
        if (query.projectId() == null || query.projectId() <= 0) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(query.actor().tenantId())
                .subjectUserId(query.actor().actorId()).anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        return ProjectBusinessContextApi.Query.builder().tenantId(query.actor().tenantId())
                .actorId(query.actor().actorId()).projectId(query.projectId()).expectedScopeVersion(scope.treeVersion()).build();
    }

    private static void requireActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || actor.actorId() <= 0
                || !Objects.equals(actor.tenantId(), TenantContextHolder.getTenantId())
                || !Objects.equals(actor.actorId(), SecurityFrameworkUtils.getLoginUserId()))
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
    }
}
