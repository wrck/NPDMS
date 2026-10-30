package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.ProjectEquipmentExportMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ProjectEquipmentExportQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ProjectEquipmentExportSource;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class ProjectEquipmentExportService {
    private final ProjectEquipmentExportMapper mapper;
    private final ProjectScopeApi projects;
    private final PermissionApi permissions;
    private final ContractAccessService contracts;
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<ProjectEquipmentExportRow> read(Long projectId) {
        var user=SecurityFrameworkUtils.getLoginUser();
        Long tenant=TenantContextHolder.getTenantId();
        if (projectId==null || projectId<=0 || user==null || tenant==null || !tenant.equals(user.getTenantId())
                || !permissions.hasAnyPermissions(user.getId(),"pms:device:query")
                || !permissions.hasAnyPermissions(user.getId(),"pms:project-device:export")) throw exception(FORBIDDEN);
        var scope=projects.resolveCurrent(new ProjectCurrentScopeQuery(tenant,user.getId(),projectId,ProjectScopeApi.ACTION_VIEW));
        if (scope==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
        var orderScope=Objects.requireNonNull(contracts.orderLineReadScope(new ContractAccessService.OrderLineReadScopeQuery(tenant,user.getId())));
        List<ProjectEquipmentExportSource> sources=new ArrayList<>();
        var query=ProjectEquipmentExportQuery.builder().tenantId(tenant).projectId(projectId).limit(500)
                .companyCodes(orderScope.companyCodes()).orderLineProjectIds(orderScope.projectIds()).build();
        while (true) {
            var batch=Objects.requireNonNull(mapper.selectBatch(query));
            sources.addAll(batch);
            if (batch.size()<query.limit()) break;
            query=query.toBuilder().afterId(batch.getLast().scopeId()).build();
        }
        // 与现有列表一致：按订单行聚合当前分配，不能把分配数量或设备数当作订单发货数量。
        sources.sort(Comparator.comparing(ProjectEquipmentExportSource::orderLineId,Comparator.nullsFirst(Long::compareTo))
                .thenComparing(ProjectEquipmentExportSource::scopeId,Comparator.reverseOrder()));
        Map<String,List<ProjectEquipmentExportSource>> grouped=sources.stream().collect(Collectors.groupingBy(
                row->row.productCode()==null || row.productCode().isEmpty()?"—":row.productCode(),LinkedHashMap::new,Collectors.toList()));
        List<ProjectEquipmentExportRow> result=new ArrayList<>();
        for (var entry:grouped.entrySet()) {
            var row=new ProjectEquipmentExportRow();var group=entry.getValue();String code=entry.getKey();
            row.setNo(result.size()+1);row.setProductCode(code);
            row.setProjectQty(sum(group,ProjectEquipmentExportSource::allocatedQuantity));
            row.setOrderNo(group.stream().map(value->text(value.orderNo())).filter(value->!value.isEmpty()).distinct().collect(Collectors.joining("、")));
            row.setProductModel(group.getFirst().productModel());
            row.setProductName(group.stream().map(value->text(value.productDesc())).filter(value->!value.isEmpty()).findFirst()
                    .orElse(group.getFirst().productName()));
            Map<Long,ProjectEquipmentExportSource> lines=new LinkedHashMap<>();
            group.forEach(value->lines.putIfAbsent(value.orderLineId(),value));
            if (lines.values().stream().allMatch(value->value.matchedLineId()!=null
                    && value.matchedLineId().equals(value.orderLineId()) && code.equals(value.lineProductCode()))) {
                row.setDeliveredQty(sum(lines.values(),ProjectEquipmentExportSource::deliveredQuantity));
                row.setOpenQty(sum(lines.values(),ProjectEquipmentExportSource::openQuantity));
            }
            result.add(row);
        }
        return result;
    }

    private static String text(String value) { return value==null?"":value.trim(); }
    private static <T> BigDecimal sum(Collection<T> values,Function<T,BigDecimal> field) {
        if (values.stream().anyMatch(value->field.apply(value)==null)) return null;
        return values.stream().map(field).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
}
