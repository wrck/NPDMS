package cn.iocoder.yudao.module.pms.asset.service.warranty;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.WarrantyShipmentEligibilityMapper;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class WarrantyShipmentEligibilityService implements WarrantyShipmentEligibilityApi {
    private final WarrantyShipmentEligibilityMapper mapper;
    private final ShipmentSourceFactsApi commerce;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    @Override @Transactional(readOnly=true)
    public List<Result> resolve(Query query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.actorId()==null || query.actorId()<=0 || query.deviceSns()==null || query.deviceSns().size()>500
                || query.deviceSns().stream().anyMatch(sn->sn==null||sn.isBlank()))throw new IllegalArgumentException("WARRANTY_ELIGIBILITY_QUERY_INVALID");
        users.validateUser(query.actorId());
        if(!permissions.hasAnyPermissions(query.actorId(),"pms:integration:execute"))throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
        if(query.deviceSns().isEmpty())return List.of();
        var shipments=mapper.selectShipments(new WarrantyShipmentEligibilityMapper.Query(query.tenantId(),query.deviceSns()));
        var bySn=new HashMap<String,List<WarrantyShipmentEligibilityMapper.Shipment>>();var packageKeys=new LinkedHashSet<String>();
        for(var row:shipments) {
            if(!query.deviceSns().contains(row.deviceSn()))throw new IllegalArgumentException("WARRANTY_ELIGIBILITY_SOURCE_CHANGED");
            bySn.computeIfAbsent(row.deviceSn(),ignored->new ArrayList<>()).add(row);
            if(nonRma(row.rmaNo()) && row.packageSourceKey()!=null && !row.packageSourceKey().isBlank())packageKeys.add(row.packageSourceKey());
        }
        var parents=new HashMap<String,ShipmentSourceFactsApi.Fact>();var keys=List.copyOf(packageKeys);
        for(int i=0;i<keys.size();i+=500) {
            var page=keys.subList(i,Math.min(i+500,keys.size()));
            for(var fact:commerce.read(new ShipmentSourceFactsApi.Query(query.tenantId(),query.actorId(),page)))
                if(!page.contains(fact.packageSourceKey()) || parents.putIfAbsent(fact.packageSourceKey(),fact)!=null)
                    throw new IllegalArgumentException("WARRANTY_ELIGIBILITY_PARENT_AMBIGUOUS");
        }
        var result=new ArrayList<Result>();
        for(String sn:new LinkedHashSet<>(query.deviceSns())) {
            var rows=bySn.getOrDefault(sn,List.of());boolean eligible=false;String issue=rows.isEmpty()?"WARRANTY_LOCAL_SHIPMENT_MISSING":null;
            for(var row:rows)if(nonRma(row.rmaNo())) {
                var parent=parents.get(row.packageSourceKey());
                if(parent==null || parent.contractNo()==null || parent.contractType()==null)issue="WARRANTY_LOCAL_PARENT_MISSING";
                else if(Set.of("0","11").contains(parent.contractType()))eligible=true;
            }
            result.add(new Result(sn,issue==null&&eligible,issue));
        }
        return List.copyOf(result);
    }
    private static boolean nonRma(String value) {return value==null || value.isEmpty() || "null".equals(value);}
}
