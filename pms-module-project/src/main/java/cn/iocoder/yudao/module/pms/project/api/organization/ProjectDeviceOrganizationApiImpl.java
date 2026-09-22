package cn.iocoder.yudao.module.pms.project.api.organization;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.DeviceProjectOrganizationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.*;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
@Service
@RequiredArgsConstructor
public class ProjectDeviceOrganizationApiImpl implements ProjectDeviceOrganizationApi {
    private final DeviceProjectOrganizationMapper mapper;
    private final OrganizationScopeApi scopes;
    public Set<Long> visibleProjectIds(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) return Set.of();
        var grants=scopes.getActiveScopes(userId).stream()
                .filter(s->s != null && s.getId()!=null && s.getVersion()!=null && s.getCompanyId()!=null)
                .map(s->new DeviceOrganizationProjectQuery.Grant(s.getCompanyId(),s.getDepartmentId())).distinct().toList();
        if(grants.isEmpty()) return Set.of();
        return Set.copyOf(mapper.selectVisibleProjectIds(new DeviceOrganizationProjectQuery(tenantId,grants)));
    }
    public List<Organization> getOrganizations(Long tenantId, Set<Long> projectIds) {
        if(tenantId==null || projectIds==null || projectIds.isEmpty()) return List.of();
        return mapper.selectOrganizations(new DeviceProjectOrganizationListQuery(tenantId,projectIds));
    }
}
