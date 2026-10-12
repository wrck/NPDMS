package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DppmsCommerceEntityResolveService implements DppmsCommerceEntityResolveApi {
    private final DppmsCommerceEntityMapper mapper;

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY,
            rollbackFor=Exception.class)
    public List<ShipmentLine> lockShipmentLines(Command command,List<ShipmentLine> lines) {
        if(command==null || command.actorId()==null || command.actorId()<=0 || command.projectId()==null
                || lines==null || lines.isEmpty()) throw new IllegalArgumentException("DPPMS_SHIPMENT_LINES_INVALID");
        var resolved=resolve(command);
        var keys=new HashSet<String>();var ids=new HashSet<Long>();
        for(var expected:lines) {
            if(expected==null || expected.sourceKey()==null || !keys.add(expected.sourceKey()) || expected.id()==null
                    || expected.id()<=0 || !ids.add(expected.id()) || expected.inventoryTransactionId()==null
                    || expected.inventoryTransactionId().isBlank()) throw new IllegalArgumentException("DPPMS_SHIPMENT_LINE_IDENTITY_INVALID");
            var identity=command.lines().stream().filter(line->Objects.equals(line.sourceKey(),expected.sourceKey())).toList();
            var matches=resolved.stream().filter(row->"order-lines".equals(row.domain())
                    && Objects.equals(row.sourceKey(),expected.sourceKey())).flatMap(row->row.targets().stream())
                    .filter(target->"COM".equals(target.targetContext()) && "com_sales_order_line".equals(target.targetTable())
                            && Objects.equals(target.targetId(),expected.id())).toList();
            if(identity.size()!=1 || matches.size()!=1
                    || !Objects.equals(identity.getFirst().source(),expected.sourceSystem())
                    || !Objects.equals(identity.getFirst().companyCode(),expected.companyCode())
                    || !Objects.equals(identity.getFirst().number(),expected.orderNo())
                    || !Objects.equals(identity.getFirst().lineNo(),expected.lineNo()))
                throw new IllegalArgumentException("DPPMS_SHIPMENT_LINE_NOT_ASSOCIATED");
            var actual=mapper.selectShipmentLineForUpdate(new ShipmentLineLockQuery(command.tenantId(),expected.id()));
            if(actual==null || !Objects.equals(actual.getId(),expected.id()) || Boolean.TRUE.equals(actual.getDeleted()) || !Objects.equals(actual.getTenantId(),command.tenantId())
                    || !Objects.equals(actual.getSourceSystem(),expected.sourceSystem())
                    || !Objects.equals(actual.getCompanyCode(),DppmsCompanyCodeConverter.fromSource(expected.companyCode()))
                    || !Objects.equals(actual.getOrderNo(),expected.orderNo()) || !Objects.equals(actual.getLineNo(),expected.lineNo())
                    || actual.getCustomInfo()==null || !Objects.equals(expected.inventoryTransactionId(),
                    cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(actual.getCustomInfo()).path("inventTransId").asText(null)))
                throw new IllegalArgumentException("DPPMS_SHIPMENT_LINE_CONTENT_CHANGED");
        }
        return List.copyOf(lines);
    }

    @Override
    public List<LicenseContract> resolveLicenseContracts(LicenseContracts query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.actorId()==null || query.actorId()<=0 || query.projectId()==null || query.projectId()<=0
                || query.contractNos()==null || query.contractNos().size()>200
                || query.contractNos().stream().anyMatch(no->no==null || no.isBlank()))
            throw new IllegalArgumentException("DPPMS_LICENSE_CONTRACT_QUERY_INVALID");
        return query.contractNos().stream().distinct().map(no->{
            var ids=mapper.selectContractIds(new ContractQuery(query.tenantId(),query.projectId(),no,LocalDateTime.now()));
            return new LicenseContract(no,ids.size()==1?ids.getFirst():null);
        }).toList();
    }

    @Override
    public List<NativeEntityMigrationRow> resolve(Command command) {
        if (!Objects.equals(command.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("DPPMS_NATIVE_TENANT_MISMATCH");
        // Validate every company before any target lookup; do not infer aliases from project contracts.
        command.orders().forEach(order -> DppmsCompanyCodeConverter.fromSource(order.companyCode()));
        command.lines().forEach(line -> DppmsCompanyCodeConverter.fromSource(line.companyCode()));
        var contracts = mapper.selectContractIds(new ContractQuery(command.tenantId(), command.projectId(), command.contractNo(), LocalDateTime.now()));
        if (contracts.size()!=1) throw new IllegalArgumentException("DPPMS_NATIVE_CONTRACT_NOT_ASSOCIATED");
        List<NativeEntityMigrationRow> result = new ArrayList<>();
        if (command.contractSourceKey()!=null) result.add(resolve("contracts", command.contractSourceKey(), contracts, "Contract", "com_contract"));
        Map<String,Long> resolvedOrders=new HashMap<>();
        for (var order:command.orders()) {
            String company = DppmsCompanyCodeConverter.fromSource(order.companyCode());
            String key = key(order.source(),company,order.type(),order.number());
            var ids=key==null?List.<Long>of():mapper.selectAssociatedOrderIds(AssociatedOrderQuery.builder()
                    .tenantId(command.tenantId()).projectId(command.projectId()).contractNo(command.contractNo())
                    .source(order.source()).companyCode(company).rawCompanyCode(order.companyCode())
                    .type(order.type()).number(order.number()).at(LocalDateTime.now()).build());
            if (ids.size()==1) resolvedOrders.put(key,ids.getFirst());
            result.add(resolve("orders", order.sourceKey(),ids,"SalesOrder","com_sales_order"));
        }
        for (var line:command.lines()) {
            String company = DppmsCompanyCodeConverter.fromSource(line.companyCode());
            var parents = command.orders().stream().filter(o->Objects.equals(o.number(),line.number())
                    && Objects.equals(o.source(),line.source()) && Objects.equals(o.type(),line.type())
                    && Objects.equals(DppmsCompanyCodeConverter.fromSource(o.companyCode()),company)).toList();
            String parent = parents.size()==1?key(line.source(),company,parents.getFirst().type(),line.number()):null;
            result.add(resolve("order-lines",line.sourceKey(),parent==null||!resolvedOrders.containsKey(parent)?List.of():mapper.selectAssociatedLineIds(new AssociatedLineQuery(command.tenantId(),resolvedOrders.get(parent),line.lineNo())), "SalesOrderLine", "com_sales_order_line"));
        }
        for (var execution:command.executions()) result.add(resolve("executions",execution.sourceKey(),
                mapper.selectExecutionIds(new ExecutionQuery(command.tenantId(),command.projectId(),execution.source(),execution.number())),"CrmExecutionOrder","com_crm_execution_order"));
        for (var pack:command.packages()) result.add(resolve("shipment-logistics",pack.sourceKey(),
                mapper.selectPackageIds(new PackageQuery(command.tenantId(),pack.packageNo())),"ShipmentPackage","com_shipment_package"));
        return List.copyOf(result);
    }

    private static String key(String source,String company,String type,String number) {
        return source==null||company==null||type==null||number==null?null:"DPPMS|"+source+"|"+company+"|"+type+"|"+number;
    }

    private static NativeEntityMigrationRow resolve(String domain,String source,List<Long> ids,String type,String table) {
        return ids.size()==1?NativeEntityMigrationRow.mapped(domain,source,new ExternalTargetMapping("COM",type,table,ids.getFirst(),"BUSINESS_ENTITY",0))
                :NativeEntityMigrationRow.retained(domain,source,ids.isEmpty()?"NATIVE_SOURCE_IDENTITY_UNRESOLVED":"NATIVE_SOURCE_IDENTITY_AMBIGUOUS");
    }
}
