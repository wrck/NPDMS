package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsOrderLineFieldCarryMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query.OrderLineFieldCarryQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ErpOrderSyncQuery;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.platform.support.migration.NativeFieldEnrichment.*;

/** Copies the reviewed header carry workflow for existing lines; quantities and ordinary ingestion remain protected. */
@Service @RequiredArgsConstructor
public class DppmsOrderLineFieldCarryOwner {
    private final DppmsOrderLineFieldCarryMapper mapper;
    private final ErpOrderSyncMapper identities;
    public record Proof(Long targetId,Integer expectedVersion,String sourceFingerprint,String fieldsFingerprint,String protectedFingerprint) {}
    private record Entry(DataSyncAdapter.Row source,Proof proof,Map<String,Object> raw,SalesOrderLineDO incoming,String erp,String company,LocalDateTime clock) {}
    private record Planned(Entry entry,DppmsOrderLineFieldCarryMapper.Current current,SalesOrderLineDO after,boolean changed) {}
    private static final List<String> ORIGINALS=List.of("id","orderNumber","lineNum","itemCode","itemDesc","orderQuantity",
            "openQuantity","bundleCode","warrantyMonth","lineType","compCode","profitCenter","realOrderExecNumber","source","customInfo","syncTime");
    private static final List<String> FIELDS=List.of("productCode","productDesc","bundleCode","warrantyMonth","profitCenter","realExecutionNo","customInfo");

    public List<DataSyncAdapter.Change> preview(DataSyncAdapter.Batch batch) {return execute(batch,false);}
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<DataSyncAdapter.Change> apply(DataSyncAdapter.Batch batch) {return execute(batch,true);}
    private List<DataSyncAdapter.Change> execute(DataSyncAdapter.Batch batch,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch.owner()==null || !batch.owner().matches("integration:"+tenant+":[0-9]+")
                || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy())
                || batch.adoptExisting() || batch.clearBeforeLoad() || batch.rows().isEmpty() || batch.rows().size()>10000)
            throw new IllegalArgumentException("DPPMS_LINE_FIELD_CARRY_SCOPE_INVALID");
        List<Entry> entries=new ArrayList<>();Set<String> keys=new HashSet<>();
        for(var source:batch.rows()) {
            var fields=source.fields();var proof=JsonUtils.parseObject(JsonUtils.toJsonString(fields.get("lineFieldCarryProof")),Proof.class);
            Map<String,Object> raw=JsonUtils.getObjectMapper().readerFor(new tools.jackson.core.type.TypeReference<Map<String,Object>>() {})
                    .with(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(text(fields,"originalSourceFields"));
            if(!"LINE".equals(source.object()) || !keys.add(source.sourceKey()) || proof==null
                    || proof.targetId()==null || proof.targetId()<=0 || !Objects.equals(source.targetId(),proof.targetId())
                    || proof.expectedVersion()==null || proof.expectedVersion()<0 || !fingerprint(proof.sourceFingerprint())
                    || !fingerprint(proof.fieldsFingerprint()) || !fingerprint(proof.protectedFingerprint())
                    || !Objects.equals(proof.sourceFingerprint(),text(fields,"originalSourceFingerprint"))
                    || raw==null || !raw.keySet().containsAll(ORIGINALS) || !source.sourceKey().equals(text(raw,"id"))
                    || fields.get("migrationIssue")!=null) throw new IllegalArgumentException("DPPMS_LINE_FIELD_CARRY_PROOF_INVALID");
            String erp=text(raw,"source"),company=DppmsCompanyCodeConverter.fromSource(text(raw,"compCode"));
            var incoming=new SalesOrderLineDO();incoming.setOrderNo(code(raw,"orderNumber"));incoming.setOrderType(code(raw,"lineType"));
            incoming.setLineNo(code(raw,"lineNum"));incoming.setProductCode(text(raw,"itemCode"));incoming.setProductDesc(text(raw,"itemDesc"));
            incoming.setBundleCode(text(raw,"bundleCode"));incoming.setProfitCenter(text(raw,"profitCenter"));incoming.setRealExecutionNo(text(raw,"realOrderExecNumber"));
            incoming.setWarrantyMonth(decimal(raw,"warrantyMonth")==null?null:decimal(raw,"warrantyMonth").intValueExact());incoming.setCustomInfo(text(fields,"customInfo"));
            incoming.setOrderQty(decimal(raw,"orderQuantity"));incoming.setOpenQty(decimal(raw,"openQuantity"));
            incoming.setDeliveredQty(incoming.getOrderQty()==null || incoming.getOpenQty()==null?null:incoming.getOrderQty().subtract(incoming.getOpenQty()));
            LocalDateTime clock=time(fields.get("sourceUpdatedAt")),originalClock=time(raw.get("syncTime"));
            if(!Set.of("SAP","D365","SMS").contains(erp) || !Set.of("0","1").contains(incoming.getOrderType())
                    || incoming.getOrderNo()==null || incoming.getLineNo()==null || clock==null || originalClock==null || originalClock.isAfter(clock)
                    || !Objects.equals(erp,code(fields,"erpSource")) || !Objects.equals(company,code(fields,"companyCode"))
                    || !Objects.equals(incoming.getOrderNo(),code(fields,"orderNo")) || !Objects.equals(incoming.getOrderType(),code(fields,"orderType"))
                    || !Objects.equals(incoming.getLineNo(),code(fields,"lineNo")) || fields.get("unitCode")!=null
                    || !Objects.equals(incoming.getProductCode(),text(fields,"productCode")) || !Objects.equals(incoming.getProductDesc(),text(fields,"productDesc"))
                    || !Objects.equals(incoming.getBundleCode(),text(fields,"bundleCode")) || !Objects.equals(incoming.getProfitCenter(),text(fields,"profitCenter"))
                    || !Objects.equals(incoming.getRealExecutionNo(),text(fields,"realExecutionNo")) || !sameJson(raw.get("customInfo"),incoming.getCustomInfo())
                    || !sameDecimal(incoming.getOrderQty(),decimal(fields,"orderQuantity")) || !sameDecimal(incoming.getOpenQty(),decimal(fields,"openQuantity"))
                    || !sameDecimal(decimal(raw,"warrantyMonth"),decimal(fields,"warrantyMonth")))
                throw new IllegalArgumentException("DPPMS_LINE_FIELD_MAPPING_CHANGED_AFTER_REVIEW");
            if(incoming.getCustomInfo()!=null)JsonUtils.parseObject(incoming.getCustomInfo(),Object.class);
            entries.add(new Entry(source,proof,raw,incoming,erp,company,clock));
        }
        var query=new OrderLineFieldCarryQuery(tenant,entries.stream().map(e->e.proof().targetId()).distinct().sorted().toList());
        Map<Long,DppmsOrderLineFieldCarryMapper.Current> current=new HashMap<>();
        for(var row:apply?mapper.selectCurrentForUpdate(query):mapper.selectCurrent(query))
            if(current.putIfAbsent(row.getId(),row)!=null)throw new IllegalStateException("DPPMS_LINE_FIELD_TARGET_AMBIGUOUS");
        var identityQuery=new ErpOrderSyncQuery(tenant,entries.stream().map(e->e.incoming().getOrderNo()).distinct().toList());
        Map<Long,SalesOrderDO> parents=new HashMap<>();Map<String,Long> parentKeys=new HashMap<>(),lineKeys=new HashMap<>();
        for(var parent:identities.selectOrders(identityQuery))if(!Boolean.TRUE.equals(parent.getDeleted())) {
            parents.put(parent.getId(),parent);unique(parentKeys,sourceErp(parent.getSourceKey(),parent.getSourceSystem())+"|"+businessKey(parent.getCompanyCode(),parent.getOrderType(),parent.getOrderNo()),parent.getId());
        }
        for(var line:identities.selectLines(identityQuery))if(!Boolean.TRUE.equals(line.getDeleted()))
            unique(lineKeys,sourceErp(line.getSourceKey(),line.getSourceSystem())+"|"+businessKey(line.getCompanyCode(),line.getOrderType(),line.getOrderNo())+"|"+line.getLineNo(),line.getId());
        List<Planned> plan=new ArrayList<>();Map<Long,SalesOrderLineDO> unique=new HashMap<>();
        for(var entry:entries) {
            var row=current.get(entry.proof().targetId());var incoming=entry.incoming();var parent=row==null?null:parents.get(row.getOrderId());
            String[] sourceParts=row==null || row.getSourceKey()==null?new String[0]:row.getSourceKey().split("\\|",-1);
            String parentKey=sourceParts.length==6?String.join("|",Arrays.copyOf(sourceParts,5)):null;
            if(row==null || Boolean.TRUE.equals(row.getDeleted()) || !Long.valueOf(tenant).equals(row.getTenantId()) || parent==null
                    || !Long.valueOf(tenant).equals(parent.getTenantId()) || sourceParts.length!=6 || !Objects.equals(sourceParts[5],incoming.getLineNo())
                    || !DppmsOrderSyncAdapter.sameDppmsOrderIdentity(parentKey,entry.erp(),entry.company(),incoming.getOrderType(),incoming.getOrderNo())
                    || !DppmsOrderSyncAdapter.sameDppmsOrderIdentity(parent.getSourceKey(),entry.erp(),entry.company(),incoming.getOrderType(),incoming.getOrderNo())
                    || !businessKey(row.getCompanyCode(),row.getOrderType(),row.getOrderNo()).equals(businessKey(entry.company(),incoming.getOrderType(),incoming.getOrderNo()))
                    || !Objects.equals(row.getLineNo(),incoming.getLineNo()) || !Objects.equals(row.getLineType(),incoming.getOrderType())
                    || !("ERP".equals(row.getSourceSystem()) || entry.erp().equals(row.getSourceSystem()))
                    || !Objects.equals(row.getSourceUpdatedAt(),entry.clock()) || !Objects.equals(row.getProtectedFingerprint(),entry.proof().protectedFingerprint())
                    || !sameDecimal(row.getOrderQty(),incoming.getOrderQty()) || !sameDecimal(row.getOpenQty(),incoming.getOpenQty())
                    || !sameDecimal(row.getDeliveredQty(),incoming.getDeliveredQty()))
                throw new IllegalArgumentException("DPPMS_LINE_FIELD_IDENTITY_OR_PROTECTED_FIELDS_CHANGED");
            boolean original=Objects.equals(row.getVersion(),entry.proof().expectedVersion()) && Objects.equals(row.getFieldsFingerprint(),entry.proof().fieldsFingerprint());
            boolean carried=Objects.equals(row.getVersion(),Math.addExact(entry.proof().expectedVersion(),1));
            if(!original && !carried)throw new IllegalArgumentException("DPPMS_LINE_FIELD_TARGET_CHANGED_AFTER_REVIEW");
            var after=new SalesOrderLineDO();BeanUtils.copyProperties(row,after);
            for(String field:FIELDS)if(entry.raw().get(switch(field) {
                case "productCode" -> "itemCode";case "productDesc" -> "itemDesc";case "realExecutionNo" -> "realOrderExecNumber";
                default -> field;
            })==null && new org.springframework.beans.BeanWrapperImpl(row).getPropertyValue(field)!=null)
                throw new IllegalArgumentException("DPPMS_LINE_FIELD_CONTENT_CONFLICT");
            if(!compatible(after,incoming,FIELDS))throw new IllegalArgumentException("DPPMS_LINE_FIELD_CONTENT_CONFLICT");
            boolean changed=fillMissing(after,incoming,FIELDS);
            if(parent.getCompanyId()==null || parent.getCompanyName()==null || !entry.company().equals(parent.getCompanyCode())
                    || row.getCompanyId()!=null && !Objects.equals(row.getCompanyId(),parent.getCompanyId())
                    || row.getCompanyName()!=null && !Objects.equals(row.getCompanyName(),parent.getCompanyName()))
                throw new IllegalArgumentException("DPPMS_LINE_FIELD_COMPANY_REFERENCE_CONFLICT");
            if(!Objects.equals(after.getCompanyCode(),entry.company()) || after.getCompanyId()==null || after.getCompanyName()==null || !entry.erp().equals(after.getSourceSystem()))changed=true;
            after.setCompanyCode(entry.company());after.setCompanyId(parent.getCompanyId());after.setCompanyName(parent.getCompanyName());after.setSourceSystem(entry.erp());
            if(!original && changed)throw new IllegalArgumentException("DPPMS_LINE_FIELD_TARGET_CHANGED_AFTER_REVIEW");
            var duplicate=unique.putIfAbsent(row.getId(),after);
            if(duplicate!=null && !duplicate.equals(after))throw new IllegalArgumentException("DPPMS_LINE_FIELD_SOURCE_CONTENT_CONFLICT");
            plan.add(new Planned(entry,row,after,changed));
        }
        if(apply) {
            Set<Long> changed=new HashSet<>();for(var item:plan)if(item.changed() && changed.add(item.current().getId()))
                if(mapper.updateFields(new DppmsOrderLineFieldCarryMapper.FieldUpdate(tenant,item.current().getVersion(),item.after()))!=1)
                    throw new IllegalStateException("DPPMS_LINE_FIELD_CONCURRENT_CHANGE");
            current=new HashMap<>();for(var row:mapper.selectCurrent(query))current.put(row.getId(),row);
        }
        Map<String,DataSyncAdapter.Binding> bindings=new HashMap<>();batch.bindings().forEach(b->bindings.put(b.object()+":"+b.sourceKey(),b));
        List<DataSyncAdapter.Change> result=new ArrayList<>();
        for(var item:plan) {
            var row=item.entry().source();Map<String,Object> after=new LinkedHashMap<>(row.fields());var raw=new LinkedHashMap<String,Object>();
            item.entry().raw().forEach((key,value)->raw.put(key,value==null?tools.jackson.databind.node.NullNode.getInstance():value));after.put("originalFields",raw);
            var persisted=current.get(item.current().getId());if(persisted==null || !Objects.equals(persisted.getProtectedFingerprint(),item.entry().proof().protectedFingerprint()))
                throw new IllegalStateException("DPPMS_LINE_FIELD_PROTECTED_FIELDS_CHANGED_DURING_CARRY");
            after.put("_targetSourceKey",item.current().getSourceKey());after.put("fieldCarryTargetFingerprint",persisted.getFieldsFingerprint());after.put("fieldCarryTargetVersion",persisted.getVersion());
            var prior=bindings.get("LINE:"+row.sourceKey());result.add(new DataSyncAdapter.Change("LINE",row.sourceKey(),item.current().getId(),item.changed()?"UPDATED":"UNCHANGED",
                    prior==null?Map.of():prior.lastFields(),after,"原订单行字段按已批准映射承接；原父订单、数量、单位、状态及历史保持"));
        }
        return List.copyOf(result);
    }
    private static void unique(Map<String,Long> keys,String key,Long id) {var old=keys.putIfAbsent(key,id);if(old!=null && !old.equals(id))throw new IllegalArgumentException("DPPMS_LINE_FIELD_CURRENT_IDENTITY_COLLISION");}
    private static String sourceErp(String key,String system) {String[] parts=key==null?new String[0]:key.split("\\|",-1);return parts.length>1 && "DPPMS".equals(parts[0])?parts[1]:system;}
    private static String businessKey(String company,String type,String number) {return (DppmsCompanyCodeConverter.mappingValues().containsKey(company)?DppmsCompanyCodeConverter.fromSource(company):company)+"|"+type+"|"+number;}
    private static boolean fingerprint(String value) {return value!=null && value.matches("[0-9a-f]{64}");}
    private static String text(Map<String,Object> fields,String name) {var value=fields.get(name);return value==null?null:value.toString();}
    private static String code(Map<String,Object> fields,String name) {var value=text(fields,name);return value==null || value.isBlank()?null:value.trim();}
    private static BigDecimal decimal(Map<String,Object> fields,String name) {var value=code(fields,name);return value==null?null:new BigDecimal(value);}
    private static boolean sameDecimal(BigDecimal a,BigDecimal b) {return a==null?b==null:b!=null && a.compareTo(b)==0;}
    private static boolean sameJson(Object original,String mapped) {
        if(original==null || mapped==null)return original==null && mapped==null;
        String json=original instanceof String value?value:JsonUtils.toJsonString(original);
        return Objects.equals(JsonUtils.parseObject(json,Object.class),JsonUtils.parseObject(mapped,Object.class));
    }
    private static LocalDateTime time(Object value) {return value==null?null:value instanceof Number number?Instant.ofEpochMilli(number.longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime():LocalDateTime.parse(value.toString().replace(' ','T'));}
}
