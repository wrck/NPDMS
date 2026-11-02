package cn.iocoder.yudao.module.pms.asset.service.device;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.ProjectShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.ProjectShipmentPageQuery;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectShipmentLogisticsApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
@Service
@RequiredArgsConstructor
public class ProjectShipmentQueryService {
    private final ProjectShipmentMapper shipments;
    private final ProjectScopeApi projectScope;
    private final ProjectContractQueryApi contracts;
    private final ProjectShipmentLogisticsApi logistics;
    public record Query(Long tenantId, Long userId, Long projectId, String sn, int pageNo, int pageSize) {}
    public record Item(DeviceShipmentDO shipment, ProjectShipmentLogisticsApi.Fact logistics) {}
    public PageResult<Item> page(Query query) {
        if (query == null || query.tenantId() == null || query.userId() == null
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0
                || query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100
                || query.sn() != null && query.sn().length() > 100)
            throw new IllegalArgumentException("项目发货查询范围或分页无效");
        var scope = projectScope.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(), query.userId(), query.projectId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(query.projectId()))
            throw new AccessDeniedException("无权查看该项目发货记录");
        var numbers = contracts.getCurrentContractNumbers(query.projectId());
        if (numbers.isEmpty()) return new PageResult<>(List.of(), 0L);
        var page = new ProjectShipmentPageQuery(query.tenantId(), numbers, query.sn(), (long)(query.pageNo()-1)*query.pageSize(), query.pageSize());
        long total = shipments.count(page);
        if (total == 0) return new PageResult<>(List.of(), 0L);
        var rows = shipments.selectPage(page);
        var references = rows.stream().filter(row -> row.getSourceSystem() != null && !row.getSourceSystem().isBlank()
                && row.getPackageNo() != null && !row.getPackageNo().isBlank())
                .map(ProjectShipmentQueryService::reference).distinct().toList();
        var facts = new java.util.HashMap<ProjectShipmentLogisticsApi.Reference, ProjectShipmentLogisticsApi.Fact>();
        if (!references.isEmpty()) for (var fact : logistics.read(new ProjectShipmentLogisticsApi.Query(
                query.tenantId(), query.userId(), query.projectId(), references))) facts.put(fact.reference(), fact);
        return new PageResult<>(rows.stream().map(row -> new Item(row, facts.get(reference(row)))).toList(), total);
    }
    private static ProjectShipmentLogisticsApi.Reference reference(DeviceShipmentDO row) {
        return new ProjectShipmentLogisticsApi.Reference(row.getSourceSystem(), row.getPackageNo(), row.getContractNo());
    }
}
