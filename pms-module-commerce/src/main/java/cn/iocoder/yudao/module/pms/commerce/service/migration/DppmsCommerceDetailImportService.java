package cn.iocoder.yudao.module.pms.commerce.service.migration;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import org.springframework.beans.BeanWrapperImpl;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.associated.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import java.util.*;
import static cn.iocoder.yudao.module.pms.platform.support.migration.NativeFieldEnrichment.*;
@Service
@RequiredArgsConstructor
public class DppmsCommerceDetailImportService implements DppmsCommerceDetailImportApi {
    private final DppmsCommerceEntityResolveApi resolver;
    private final DppmsCommerceDetailMapper mapper;
    private final SalesOrderMapper orders;
    private final SalesOrderLineMapper lines;
    private final ShipmentPackageMapper packages;
    private final DppmsExecutionOrganizationReferences organizationReferences;
    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<NativeEntityMigrationRow> importDetails(Command command) {
        var identity=command.identities(); tenant(identity.tenantId());
        var result=new LinkedHashMap<String,NativeEntityMigrationRow>();
        resolver.resolve(identity).forEach(r->result.put(r.domain()+":"+r.sourceKey(),r));
        var directory=command.executions().isEmpty()?null:organizationReferences.load();
        for (var source:command.executions()) {
            String key="executions:"+source.sourceKey(); var outcome=result.get(key);
            if (outcome==null || outcome.targets().isEmpty()) continue;
            var incoming=JsonUtils.parseObject(JsonUtils.toJsonString(source),CrmExecutionOrderDO.class);
            incoming.setCompanyCode(DppmsCompanyCodeConverter.fromSource(source.sourceCompanyCode()));
            incoming.setSubmitTime(source.submitTime());incoming.setPredictedBidTime(source.predictedBidTime());
            if (source.engineeringFeeRaw()!=null && !source.engineeringFeeRaw().isBlank())
                incoming.setEngineeringFee(new java.math.BigDecimal(source.engineeringFeeRaw().replace(",","").trim()));
            try { directory.associate(incoming); }
            catch (IllegalArgumentException unresolved) {
                result.put(key,NativeEntityMigrationRow.retained("executions",source.sourceKey(),unresolved.getMessage()));continue;
            }
            var row=mapper.selectExecutionForUpdate(new DppmsCommerceDetailMapper.ExecutionIdentityQuery(
                    identity.tenantId(),identity.projectId(),outcome.targets().getFirst().targetId(),source.sourceSystem(),source.executionNo()));
            if (row==null) {
                result.put(key,NativeEntityMigrationRow.retained("executions",source.sourceKey(),"TARGET_IDENTITY_CONFLICT"));continue;
            }
            String conflict=executionConflict(row,incoming);
            if (conflict!=null) {
                result.put(key,NativeEntityMigrationRow.retained("executions",source.sourceKey(),conflict));continue;
            }
            if (fillMissing(row,incoming,EXECUTION_ENRICHMENT_FIELDS) && mapper.updateExecutionFields(new DppmsCommerceDetailMapper.ExecutionEnrichmentUpdate(
                    identity.tenantId(),identity.projectId(),row.getVersion(),identity.actorId(),java.time.LocalDateTime.now(),row))!=1)
                throw new IllegalStateException("DPPMS_NATIVE_EXECUTION_CONCURRENT_CHANGE");
        }
        for (var source:command.orders()) {
            String key="orders:"+source.sourceKey(); var outcome=result.get(key);
            if (outcome==null || outcome.targets().isEmpty()) continue;
            var row=mapper.selectOrderForUpdate(new DppmsCommerceDetailMapper.IdQuery(identity.tenantId(),outcome.targets().getFirst().targetId()));
            if (row==null) { result.put(key,NativeEntityMigrationRow.retained("orders",source.sourceKey(),"TARGET_IDENTITY_CONFLICT"));continue; }
            var incoming=JsonUtils.parseObject(JsonUtils.toJsonString(source),SalesOrderDO.class);
            // Assign directly: serialization must not truncate the original fractional seconds.
            incoming.setOrderCreateTime(source.orderCreateTime());
            incoming.setCustomerRequiredTime(source.customerRequiredTime());
            incoming.setSourceUpdatedAt(source.originalSourceSyncTime());
            String actualSystem=source.sourceErpSystem();
            String convertedCompany=source.sourceCompanyCode()==null?null:DppmsCompanyCodeConverter.fromSource(source.sourceCompanyCode());
            if (actualSystem==null || !Set.of("SAP","D365","SMS").contains(actualSystem)
                    || row.getSourceRecordKey()==null || !row.getSourceRecordKey().startsWith("DPPMS|"+actualSystem+"|")
                    || row.getSourceSystem()!=null && !"ERP".equals(row.getSourceSystem()) && !actualSystem.equals(row.getSourceSystem())
                    || convertedCompany!=null && !Objects.equals(convertedCompany,row.getCompanyCode())) {
                result.put(key,NativeEntityMigrationRow.retained("orders",source.sourceKey(),"TARGET_IDENTITY_CONFLICT"));continue;
            }
            var fields=List.of("contractNo","executionNo","orderCreateTime","customerRequiredTime","customerCode","customerName","sourceProjectName","orderComment","salesType","customInfo","sourceUpdatedAt");
            if (!compatible(row,incoming,fields)) {
                result.put(key,NativeEntityMigrationRow.retained("orders",source.sourceKey(),"COMMERCE_SOURCE_CONTENT_CHANGED"));continue;
            }
            boolean changed=fillMissing(row,incoming,fields);
            if (!actualSystem.equals(row.getSourceSystem())) { row.setSourceSystem(actualSystem);changed=true; }
            if (changed && orders.updateById(row)!=1)
                throw new IllegalStateException("DPPMS_NATIVE_ORDER_CONCURRENT_CHANGE");
        }
        for (var source:command.lines()) {
            String key="order-lines:"+source.sourceKey(); var outcome=result.get(key);
            if (outcome==null || outcome.targets().isEmpty()) continue;
            var row=mapper.selectLineForUpdate(new DppmsCommerceDetailMapper.IdQuery(identity.tenantId(),outcome.targets().getFirst().targetId()));
            if (row==null) { result.put(key,NativeEntityMigrationRow.retained("order-lines",source.sourceKey(),"TARGET_IDENTITY_CONFLICT"));continue; }
            var incoming=JsonUtils.parseObject(JsonUtils.toJsonString(source),SalesOrderLineDO.class);
            incoming.setSourceUpdatedAt(source.originalSourceSyncTime());
            String actualSystem=source.sourceErpSystem();
            String convertedCompany=source.sourceCompanyCode()==null?null:DppmsCompanyCodeConverter.fromSource(source.sourceCompanyCode());
            if (actualSystem==null || !Set.of("SAP","D365","SMS").contains(actualSystem)
                    || row.getSourceRecordKey()==null || !row.getSourceRecordKey().startsWith("DPPMS|"+actualSystem+"|")
                    || row.getSourceSystem()!=null && !"ERP".equals(row.getSourceSystem()) && !actualSystem.equals(row.getSourceSystem())
                    || convertedCompany!=null && !Objects.equals(convertedCompany,row.getCompanyCode())) {
                result.put(key,NativeEntityMigrationRow.retained("order-lines",source.sourceKey(),"TARGET_IDENTITY_CONFLICT"));continue;
            }
            var fields=List.of("productCode","productDesc","orderQty","openQty","bundleCode","warrantyMonth","lineType","profitCenter","realExecutionNo","customInfo","sourceUpdatedAt");
            if (!compatible(row,incoming,fields)) {
                result.put(key,NativeEntityMigrationRow.retained("order-lines",source.sourceKey(),"COMMERCE_SOURCE_CONTENT_CHANGED"));continue;
            }
            boolean changed=fillMissing(row,incoming,fields);
            if (!actualSystem.equals(row.getSourceSystem())) { row.setSourceSystem(actualSystem);changed=true; }
            if (changed && lines.updateById(row)!=1)
                throw new IllegalStateException("DPPMS_NATIVE_ORDER_CONCURRENT_CHANGE");
        }
        for (var source:command.packages()) {
            var found=mapper.selectPackageForUpdate(new DppmsCommerceDetailMapper.PackageQuery(identity.tenantId(),
                    identity.projectId(),identity.contractNo(),source.packageNo()));
            if (found.size()!=1) {
                result.put("shipment-packages:"+source.sourceKey(),NativeEntityMigrationRow.retained("shipment-packages",
                        source.sourceKey(),"NATIVE_SOURCE_IDENTITY_UNRESOLVED"));continue;
            }
            var row=found.getFirst();
            if (!Objects.equals(row.getSourceRecordKey(),source.sourceKey())) {
                result.put("shipment-packages:"+source.sourceKey(),NativeEntityMigrationRow.retained("shipment-packages",
                        source.sourceKey(),"NATIVE_SOURCE_IDENTITY_UNRESOLVED"));continue;
            }
            if (source.sourceContractKey()!=null) {
                var reference=mapper.selectPackageContractIdentity(new DppmsCommerceDetailMapper.IdQuery(identity.tenantId(),row.getId()));
                if (reference==null || !"DPPMS".equals(reference.sourceSystem())
                        || !Objects.equals(source.sourceContractKey(),reference.sourceRecordKey())) {
                    result.put("shipment-packages:"+source.sourceKey(),NativeEntityMigrationRow.retained("shipment-packages",
                            source.sourceKey(),"NATIVE_SOURCE_IDENTITY_UNRESOLVED"));continue;
                }
            }
            var incoming=JsonUtils.parseObject(JsonUtils.toJsonString(source),ShipmentPackageDO.class);
            String currentCarrierName=row.getCarrierName();
            boolean carrierCompatible=source.carrierName()==null || Objects.equals(currentCarrierName,source.carrierName())
                    || currentCarrierName==null || currentCarrierName.isBlank()
                    || (source.carrierCode()!=null && Objects.equals(currentCarrierName,source.carrierCode()));
            var fields=List.of("packageNo","shipmentTime","warrantyStartTime","warrantyEndTime","receiverName","expressNo","carrierCode");
            if (!carrierCompatible || !compatible(row,incoming,fields)) {
                result.put("shipment-packages:"+source.sourceKey(),NativeEntityMigrationRow.retained("shipment-packages",
                        source.sourceKey(),"COMMERCE_SOURCE_CONTENT_CHANGED"));continue;
            }
            // Correct only an empty name or this source's exact carrier code after every field passes validation.
            boolean changed=fillMissing(row,incoming,fields);
            if (source.carrierName()!=null && !Objects.equals(currentCarrierName,source.carrierName())) {
                row.setCarrierName(source.carrierName());changed=true;
            }
            if (changed && packages.updateById(row)!=1)
                throw new IllegalStateException("DPPMS_NATIVE_PACKAGE_CONCURRENT_CHANGE");
            result.put("shipment-packages:"+source.sourceKey(),mapped("shipment-packages",source.sourceKey(),row.getId(),"ShipmentPackage","com_shipment_package"));
        }
        // Plans are written only by the designated INT adapter and COM plan Owner.
        // Native snapshot promotion cannot use a refreshable technical ID to create or merge business plans.
        for (var source:command.plans()) {
            result.put("plans:"+source.sourceKey(),NativeEntityMigrationRow.retained("plans",source.sourceKey(),
                    "CONTRACT_COLLECTION_PLAN_SYNC_REQUIRED"));
        }
        return List.copyOf(result.values());
    }
    private static final List<String> EXECUTION_SOURCE_FIELDS=List.of("executionNo","sourceSystem","projectCode","projectName",
            "salesRepCode","salesRepName","marketCode","marketName","systemSourceKey","systemName","expendSourceKey","expendName",
            "industryCode","industryName","departmentCode","departmentName","serviceTypeName","channelName","engineeringFeeRaw",
            "engineeringFee","sourceObjectId","applyType","companyCode","customerProjectName","finalCustomerName","agentName",
            "majorProjectLevel","projectAmount","submitTime","predictedBidTime","contactName","contactPhone");
    private static String executionConflict(CrmExecutionOrderDO row,CrmExecutionOrderDO incoming) {
        var old=new BeanWrapperImpl(row);var source=new BeanWrapperImpl(incoming);
        for (String field:EXECUTION_SOURCE_FIELDS) {
            Object previous=old.getPropertyValue(field),next=source.getPropertyValue(field);
            if ((next==null && previous!=null) || !compatible(row,incoming,List.of(field)))
                return "EXECUTION_SOURCE_CONTENT_CHANGED:"+field;
        }
        if (!compatible(row,incoming,List.of("companyId","companyName","departmentId")))
            return "EXECUTION_ORGANIZATION_REFERENCE_CONFLICT";
        return null;
    }
    private static final List<String> EXECUTION_ENRICHMENT_FIELDS;
    static {
        var fields=new ArrayList<>(EXECUTION_SOURCE_FIELDS);
        fields.addAll(List.of("companyId","companyName","departmentId"));
        EXECUTION_ENRICHMENT_FIELDS=List.copyOf(fields);
    }
    private static NativeEntityMigrationRow mapped(String domain,String key,Long id,String type,String table) {
        return NativeEntityMigrationRow.mapped(domain,key,new ExternalTargetMapping("COM",type,table,id,"BUSINESS_ENTITY",0));
    }
    @Override public Page page(Query query) {
        tenant(query.tenantId());
        if (query.actorId()==null||query.actorId()<=0||query.pageNo()<1||query.pageSize()<1||query.pageSize()>100)
            throw new IllegalArgumentException("DPPMS_NATIVE_PAGE_INVALID");
        var scope=new DppmsCommerceDetailMapper.PageQuery(query.tenantId(),query.projectId(),Math.multiplyExact(query.pageNo()-1,query.pageSize()),query.pageSize());
        return switch(query.kind()) {
            case "executions" -> new Page(mapper.selectExecutions(scope).stream().map(r->row(r,r.getId(),r.getSourceSystem()+"|"+r.getExecutionNo())).toList(),mapper.countExecutions(scope));
            case "orders" -> new Page(mapper.selectOrders(scope).stream().map(r->orderRow(r)).toList(),mapper.countOrders(scope));
            case "order-lines" -> new Page(mapper.selectLines(scope).stream().map(r->lineRow(r)).toList(),mapper.countLines(scope));
            case "plans" -> new Page(mapper.selectPlans(scope).stream().map(r->row(r,r.getId(),null)).toList(),mapper.countPlans(scope));
            case "shipment-packages" -> new Page(mapper.selectPackages(scope).stream().map(r->row(r,r.getId(),r.getSourceRecordKey())).toList(),mapper.countPackages(scope));
            default -> throw new IllegalArgumentException("DPPMS_NATIVE_KIND_INVALID");
        };
    }
    private static Row orderRow(SalesOrderDO value) {
        var result=row(value,value.getId(),value.getSourceRecordKey());
        var selected=new LinkedHashMap<String,Object>();
        for (String field:List.of("contractNo","executionNo","orderCreateTime","customerRequiredTime","customerCode","customerName","sourceProjectName","orderComment","salesType","customInfo","companyId","companyCode","companyName","sourceSystem","orderNo","orderType","sourceSyncTime","sourceUpdatedAt")) selected.put(field,result.values().get(field));
        return new Row(result.id(),result.sourceKey(),selected);
    }
    private static Row lineRow(SalesOrderLineDO value) {
        var result=row(value,value.getId(),value.getSourceRecordKey());
        var selected=new LinkedHashMap<String,Object>();
        for (String field:List.of("productCode","productDesc","orderQty","openQty","bundleCode","warrantyMonth","lineType","profitCenter","realExecutionNo","customInfo","companyId","companyCode","companyName","sourceSystem","orderId","orderNo","orderType","lineNo","unitCode","deliveredQty","quantityStatus","sourceSyncTime","sourceUpdatedAt")) selected.put(field,result.values().get(field));
        return new Row(result.id(),result.sourceKey(),selected);
    }
    private static Row row(Object data,Long id,String key) {
        Map<String,Object> values=JsonUtils.parseObject(JsonUtils.toJsonString(data),new TypeReference<Map<String,Object>>(){});
        return new Row(id,key,values);
    }
    private static void tenant(Long id) {
        if (!Objects.equals(id,TenantContextHolder.getRequiredTenantId())) throw new IllegalArgumentException("DPPMS_NATIVE_TENANT_MISMATCH");
    }
}
