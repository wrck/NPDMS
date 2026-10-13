package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSalesOrderFact;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsOrderFieldCarryMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query.OrderFieldCarryQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ErpOrderSyncQuery;
import cn.iocoder.yudao.module.pms.commerce.service.migration.DppmsExecutionOrganizationReferences;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDateTime;
import static cn.iocoder.yudao.module.pms.platform.support.migration.NativeFieldEnrichment.*;

/** Reviewed historical gaps use the COM field Owner, leaving ordinary source-version ingestion unchanged. */
@Service @RequiredArgsConstructor
public class DppmsOrderFieldCarryOwner {
    private final DppmsOrderFieldCarryMapper mapper;
    private final ErpOrderSyncMapper identities;
    private final DppmsExecutionOrganizationReferences organizations;
    public record Proof(Long targetId, Long expectedVersion, String sourceFingerprint,
                        String fieldsFingerprint, String protectedFingerprint) {}
    public record Entry(DataSyncAdapter.Row source, CommerceSalesOrderFact fact, Proof proof,
                        String sourceFingerprint, Map<String,Object> originalFields) {}
    private record Planned(Entry entry, DppmsOrderFieldCarryMapper.Current current, SalesOrderDO after, boolean changed) {}
    private static final List<String> FIELDS=List.of("contractNo","executionNo","orderCreateTime","customerRequiredTime",
            "customerCode","customerName","sourceProjectName","orderComment","salesType","customInfo");
    private static final List<String> ORIGINAL_COLUMNS=List.of("id","orderNumber","contractNo","orderExecNumber",
            "orderCreateTime","customerRequireTime","customerCode","customerName","projectName","orderComment",
            "orderType","compCode","salesType","source","customInfo","syncTime");

    public List<DataSyncAdapter.Change> preview(DataSyncAdapter.Batch batch,List<Entry> entries) { return execute(batch,entries,false); }
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<DataSyncAdapter.Change> apply(DataSyncAdapter.Batch batch,List<Entry> entries) { return execute(batch,entries,true); }

    private List<DataSyncAdapter.Change> execute(DataSyncAdapter.Batch batch,List<Entry> entries,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch.owner()==null || !batch.owner().matches("integration:"+tenant+":[0-9]+")
                || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy())
                || batch.adoptExisting() || batch.clearBeforeLoad() || entries.isEmpty() || entries.size()>10000
                || entries.size()!=batch.rows().size()) throw new IllegalArgumentException("DPPMS_ORDER_FIELD_CARRY_SCOPE_INVALID");
        Set<String> sourceKeys=new HashSet<>();List<Long> ids=new ArrayList<>();
        for(var entry:entries) {
            var proof=entry.proof();var row=entry.source();
            if(!"ORDER".equals(row.object()) || !sourceKeys.add(row.sourceKey()) || proof==null
                    || proof.targetId()==null || proof.targetId()<=0 || !Objects.equals(row.targetId(),proof.targetId())
                    || proof.expectedVersion()==null || proof.expectedVersion()<0
                    || !fingerprint(proof.sourceFingerprint()) || !fingerprint(proof.fieldsFingerprint())
                    || !fingerprint(proof.protectedFingerprint()) || !Objects.equals(proof.sourceFingerprint(),entry.sourceFingerprint())
                    || entry.originalFields()==null || !entry.originalFields().keySet().containsAll(ORIGINAL_COLUMNS)
                    || !row.sourceKey().equals(Objects.toString(entry.originalFields().get("id"),null)))
                throw new IllegalArgumentException("DPPMS_ORDER_FIELD_CARRY_PROOF_INVALID");
            ids.add(proof.targetId());
            verifySourceMapping(entry);
        }
        var query=new OrderFieldCarryQuery(tenant,ids.stream().distinct().sorted().toList());
        var loaded=apply?mapper.selectCurrentForUpdate(query):mapper.selectCurrent(query);
        Map<Long,DppmsOrderFieldCarryMapper.Current> current=new HashMap<>();
        loaded.forEach(row->{if(current.putIfAbsent(row.getId(),row)!=null)throw new IllegalStateException("DPPMS_ORDER_FIELD_TARGET_AMBIGUOUS");});
        // A reviewed binding does not authorize merging two current identities after company normalization.
        var peers=identities.selectOrders(new ErpOrderSyncQuery(tenant,entries.stream().map(e->e.fact().orderNo()).distinct().toList()));
        Map<String,Long> occupied=new HashMap<>();
        for(var row:peers) if(!Boolean.TRUE.equals(row.getDeleted())) {
            String company=row.getCompanyCode();
            if(DppmsCompanyCodeConverter.mappingValues().containsKey(company))company=DppmsCompanyCodeConverter.fromSource(company);
            String key=company+"|"+row.getOrderType()+"|"+row.getOrderNo();
            Long old=occupied.putIfAbsent(key,row.getId());
            if(old!=null && !old.equals(row.getId()))throw new IllegalArgumentException("DPPMS_ORDER_FIELD_COMPANY_IDENTITY_COLLISION");
        }
        var directory=organizations.load();List<Planned> plan=new ArrayList<>();Map<Long,SalesOrderDO> unique=new LinkedHashMap<>();
        for(var entry:entries) {
            var proof=entry.proof();var row=current.get(proof.targetId());var fact=entry.fact();
            String rawSystem=Objects.toString(entry.originalFields().get("source"),null);
            if(row==null || Boolean.TRUE.equals(row.getDeleted()) || !Long.valueOf(tenant).equals(row.getTenantId())
                    || !DppmsOrderSyncAdapter.sameDppmsOrderIdentity(row.getSourceKey(),rawSystem,fact.companyCode(),fact.orderType(),fact.orderNo())
                    || !Objects.equals(row.getSourceVersion(),fact.expectedPreviousSourceVersion())
                    || !Objects.equals(row.getSourceVersion(),fact.sourceVersion()) || !Objects.equals(row.getSourceUpdatedAt(),fact.sourceUpdatedAt())
                    || !("ERP".equals(row.getSourceSystem()) || Objects.equals(rawSystem,row.getSourceSystem()))
                    || !Objects.equals(row.getProtectedFingerprint(),proof.protectedFingerprint()))
                throw new IllegalArgumentException("DPPMS_ORDER_FIELD_IDENTITY_OR_PROTECTED_FIELDS_CHANGED");
            boolean original=Objects.equals(row.getFieldsFingerprint(),proof.fieldsFingerprint()) && Objects.equals(row.getVersion(),proof.expectedVersion());
            boolean carried=Objects.equals(row.getVersion(),Math.addExact(proof.expectedVersion(),1));
            if(!original && !carried)throw new IllegalArgumentException("DPPMS_ORDER_FIELD_TARGET_CHANGED_AFTER_REVIEW");
            var incoming=new SalesOrderDO();incoming.setContractNo(fact.contractNo());incoming.setExecutionNo(fact.executionNo());
            incoming.setOrderCreateTime(fact.orderCreateTime());incoming.setCustomerRequiredTime(fact.customerRequiredTime());
            incoming.setCustomerCode(fact.customerCode());incoming.setCustomerName(fact.customerName());
            incoming.setSourceProjectName(fact.sourceProjectName());incoming.setOrderComment(fact.orderComment());
            incoming.setSalesType(fact.salesType());incoming.setCustomInfo(fact.customInfo());
            if(fact.customInfo()!=null)JsonUtils.parseObject(fact.customInfo(),Object.class);
            var after=new SalesOrderDO();BeanUtils.copyProperties(row,after);
            // These two supported columns previously lost only their original leading/trailing whitespace.
            boolean whitespace=restoreWhitespace(after,incoming);
            if(!compatible(after,incoming,FIELDS))throw new IllegalArgumentException("DPPMS_ORDER_FIELD_CONTENT_CONFLICT");
            boolean changed=fillMissing(after,incoming,FIELDS)||whitespace;
            var company=directory.companies().get(fact.companyCode());
            if(company==null || row.getCompanyId()!=null && !Objects.equals(row.getCompanyId(),company.id())
                    || row.getCompanyName()!=null && !Objects.equals(row.getCompanyName(),company.name()))
                throw new IllegalArgumentException("DPPMS_ORDER_FIELD_COMPANY_REFERENCE_CONFLICT");
            if(!Objects.equals(after.getCompanyCode(),fact.companyCode()) || after.getCompanyId()==null || after.getCompanyName()==null
                    || !Objects.equals(after.getSourceSystem(),rawSystem))changed=true;
            after.setCompanyCode(fact.companyCode());after.setCompanyId(company.id());after.setCompanyName(company.name());after.setSourceSystem(rawSystem);
            if(!original && changed)throw new IllegalArgumentException("DPPMS_ORDER_FIELD_TARGET_CHANGED_AFTER_REVIEW");
            var duplicate=unique.putIfAbsent(row.getId(),after);
            if(duplicate!=null && !duplicate.equals(after))throw new IllegalArgumentException("DPPMS_ORDER_FIELD_SOURCE_CONTENT_CONFLICT");
            plan.add(new Planned(entry,row,after,changed));
        }
        if(apply) {
            var changed=new HashSet<Long>();
            for(var item:plan)if(item.changed() && changed.add(item.current().getId()))
                if(mapper.updateFields(new DppmsOrderFieldCarryMapper.FieldUpdate(tenant,item.current().getVersion(),item.after()))!=1)
                    throw new IllegalStateException("DPPMS_ORDER_FIELD_CONCURRENT_CHANGE");
        }
        var afterById=current;
        if(apply) {
            afterById=new HashMap<>();for(var row:mapper.selectCurrent(query))afterById.put(row.getId(),row);
        }
        Map<String,DataSyncAdapter.Binding> bindings=new HashMap<>();batch.bindings().forEach(b->bindings.put(b.object()+":"+b.sourceKey(),b));
        List<DataSyncAdapter.Change> result=new ArrayList<>();
        for(var item:plan) {
            var row=item.entry().source();Map<String,Object> after=new LinkedHashMap<>(row.fields());
            Map<String,Object> originals=new LinkedHashMap<>();item.entry().originalFields().forEach((key,value)->originals.put(key,value==null?tools.jackson.databind.node.NullNode.getInstance():value));
            after.put("originalFields",originals);after.put("_targetSourceKey",item.current().getSourceKey());
            var persisted=afterById.get(item.current().getId());if(persisted==null)throw new IllegalStateException("DPPMS_ORDER_FIELD_TARGET_MISSING_AFTER_CARRY");
            if(!Objects.equals(persisted.getProtectedFingerprint(),item.entry().proof().protectedFingerprint()))
                throw new IllegalStateException("DPPMS_ORDER_FIELD_PROTECTED_FIELDS_CHANGED_DURING_CARRY");
            after.put("fieldCarryTargetFingerprint",persisted.getFieldsFingerprint());after.put("fieldCarryTargetVersion",persisted.getVersion());
            var prior=bindings.get("ORDER:"+row.sourceKey());
            result.add(new DataSyncAdapter.Change("ORDER",row.sourceKey(),item.current().getId(),item.changed()?"UPDATED":"UNCHANGED",
                    prior==null?Map.of():prior.lastFields(),after,"原合同、执行单、公司及缺失字段按已批准映射承接；普通来源版本与历史保护保持"));
        }
        return List.copyOf(result);
    }
    private static boolean fingerprint(String value) { return value!=null && value.matches("[0-9a-f]{64}"); }
    private static void verifySourceMapping(Entry entry) {
        var raw=entry.originalFields();var fact=entry.fact();
        String company=DppmsCompanyCodeConverter.fromSource(rawText(raw,"compCode"));
        if(!Objects.equals(company,fact.companyCode()) || !Objects.equals(code(raw,"orderNumber"),fact.orderNo())
                || !Objects.equals(code(raw,"orderType"),fact.orderType())
                || !Objects.equals(code(raw,"contractNo"),fact.contractNo()) || !Objects.equals(code(raw,"orderExecNumber"),fact.executionNo())
                || !Objects.equals(code(raw,"customerCode"),fact.customerCode()) || !Objects.equals(rawText(raw,"customerName"),fact.customerName())
                || !Objects.equals(rawText(raw,"projectName"),fact.sourceProjectName()) || !Objects.equals(rawText(raw,"orderComment"),fact.orderComment())
                || !Objects.equals(code(raw,"salesType"),fact.salesType()) || !Objects.equals(rawText(raw,"customInfo"),fact.customInfo())
                || !Objects.equals(sourceTime(raw,"orderCreateTime"),fact.orderCreateTime())
                || !Objects.equals(sourceTime(raw,"customerRequireTime"),fact.customerRequiredTime()))
            throw new IllegalArgumentException("DPPMS_ORDER_FIELD_MAPPING_CHANGED_AFTER_REVIEW");
    }
    private static String rawText(Map<String,Object> fields,String name) {var value=fields.get(name);return value==null?null:value.toString();}
    private static String code(Map<String,Object> fields,String name) {var value=rawText(fields,name);return value==null||value.trim().isEmpty()?null:value.trim();}
    private static LocalDateTime sourceTime(Map<String,Object> fields,String name) {var value=rawText(fields,name);return value==null?null:LocalDateTime.parse(value.replace(' ','T'));}
    private static boolean restoreWhitespace(SalesOrderDO row,SalesOrderDO incoming) {
        boolean changed=false;
        if(whitespaceOnly(row.getOrderComment(),incoming.getOrderComment())) {row.setOrderComment(incoming.getOrderComment());changed=true;}
        if(whitespaceOnly(row.getSourceProjectName(),incoming.getSourceProjectName())) {row.setSourceProjectName(incoming.getSourceProjectName());changed=true;}
        return changed;
    }
    private static boolean whitespaceOnly(String old,String source) { return old!=null && source!=null && !old.equals(source) && old.trim().equals(source.trim()); }
}
