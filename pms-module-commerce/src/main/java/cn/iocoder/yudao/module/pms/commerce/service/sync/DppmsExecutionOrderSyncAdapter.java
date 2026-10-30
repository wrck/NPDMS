package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.CrmExecutionOrderSyncQuery;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DppmsExecutionOrderSyncAdapter implements DataSyncAdapter {
    public static final String KEY = "DPPMS_CRM_EXECUTION_ORDER";
    private final CrmExecutionOrderMapper mapper;

    @Override public Descriptor descriptor() {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("executionNo", "执行单编号", "STRING", true));
        fields.add(new Field("sourceSystem", "来源系统", "STRING", true));
        for (String name : SOURCE_FIELDS) fields.add(new Field(name, name, "STRING", false));
        return new Descriptor(KEY, "DPPMS项目执行单常规头", List.of(new ObjectDescriptor(
                "EXECUTION_ORDER", "项目执行单", fields, "COM", "CrmExecutionOrder", "com_crm_execution_order", false)),
                List.of("RETAIN"), List.of("UPSERT"), false);
    }

    @Override public List<Change> preview(Batch batch) { return plan(batch).changes(); }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public void refreshCaches() { }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Change> apply(Batch batch) {
        Plan plan = plan(batch);
        if (plan.changes().stream().anyMatch(c -> "CONFLICT".equals(c.action()) || "ISSUE".equals(c.action()))) return plan.changes();
        Map<String, Long> ids = new HashMap<>();
        List<CrmExecutionOrderDO> created = new ArrayList<>();
        List<CrmExecutionOrderDO> updated = new ArrayList<>();
        for (Candidate candidate : plan.candidates()) {
            if ("CREATED".equals(candidate.action())) created.add(candidate.row());
            else if ("UPDATED".equals(candidate.action())) updated.add(candidate.row());
        }
        if (!created.isEmpty()) mapper.insertBatch(created, 1000);
        if (!updated.isEmpty()) mapper.updateBatch(updated, 1000);
        for (Candidate candidate : plan.candidates()) {
            ids.put(candidate.key(), candidate.row().getId());
        }
        return plan.changes().stream().map(c -> new Change(c.object(), c.sourceKey(),
                ids.getOrDefault(Objects.toString(c.after().get("_businessKey"), ""), c.targetId()),
                c.action(), c.before(), c.after(), c.message())).toList();
    }

    private Plan plan(Batch batch) {
        if (!"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy())
                || batch.clearBeforeLoad() || batch.adoptExisting())
            throw new IllegalArgumentException("DPPMS执行单只允许幂等更新并保留来源缺失记录");
        if (batch.rows().stream().anyMatch(r -> !"EXECUTION_ORDER".equals(r.object())))
            throw new IllegalArgumentException("未知项目执行单同步对象");
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<String, Set<String>> numbersBySystem = new LinkedHashMap<>();
        for (Row row : batch.rows()) {
            String system = text(row, "sourceSystem"), number = text(row, "executionNo");
            if (system != null && number != null)
                numbersBySystem.computeIfAbsent(system, ignored -> new LinkedHashSet<>()).add(number);
        }
        Map<String, CrmExecutionOrderDO> existing = new HashMap<>();
        numbersBySystem.forEach((system, numbers) -> {
            List<String> keys = new ArrayList<>(numbers);
            for (int start = 0; start < keys.size(); start += 1000)
                mapper.selectIncoming(new CrmExecutionOrderSyncQuery(tenantId, system,
                                keys.subList(start, Math.min(start + 1000, keys.size()))))
                        .forEach(row -> existing.put(key(row.getSourceSystem(), row.getExecutionNo()), row));
        });
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        Map<String, Binding> bindings = new HashMap<>();
        batch.bindings().forEach(b -> bindings.put(b.object() + ":" + b.sourceKey(), b));
        List<Change> changes = new ArrayList<>();
        for (Row source : batch.rows()) {
            try {
                CrmExecutionOrderDO row = convert(source, tenantId);
                String businessKey = key(row.getSourceSystem(), row.getExecutionNo());
                CrmExecutionOrderDO old = existing.get(businessKey);
                if (old != null && Boolean.TRUE.equals(old.getDeleted())) {
                    changes.add(change(source, old.getId(), "CONFLICT", Map.of(), businessKey, "目标执行单已删除，不能由同步重新创建"));
                    continue;
                }
                Map<String,Object> payload = payload(row);
                Candidate previous = candidates.get(businessKey);
                String action = old == null ? "CREATED" : payload(old).equals(payload) ? "UNCHANGED" : "UPDATED";
                if (old != null) { row.setId(old.getId()); row.setVersion(old.getVersion()); }
                row.setSourceSyncTime(LocalDateTime.now()); row.setStatus("ACTIVE");
                // The source table keeps historical revisions under different ids. Rows are read by ascending id,
                // therefore the last revision in a page is the authoritative value for the shared business key.
                if (!"UNCHANGED".equals(action) || previous != null)
                    candidates.put(businessKey, new Candidate(businessKey, row, payload, action));
                Binding binding = bindings.get(source.object() + ":" + source.sourceKey());
                changes.add(change(source, old == null ? null : old.getId(), action,
                        binding == null ? Map.of() : binding.lastFields(), businessKey, null));
            } catch (IllegalArgumentException exception) {
                changes.add(change(source, null, "ISSUE", Map.of(), "", exception.getMessage()));
            }
        }
        return new Plan(changes, new ArrayList<>(candidates.values()));
    }

    private static CrmExecutionOrderDO convert(Row source, long tenantId) {
        CrmExecutionOrderDO r = new CrmExecutionOrderDO(); r.setTenantId(tenantId);
        r.setExecutionNo(required(source,"executionNo")); r.setSourceSystem(required(source,"sourceSystem"));
        r.setProjectCode(text(source,"projectCode")); r.setProjectName(text(source,"projectName"));
        r.setSalesRepCode(text(source,"salesRepCode")); r.setSalesRepName(text(source,"salesRepName"));
        r.setMarketCode(text(source,"marketCode")); r.setMarketName(text(source,"marketName"));
        r.setSystemSourceKey(text(source,"systemSourceKey")); r.setSystemName(text(source,"systemName"));
        r.setExpendSourceKey(text(source,"expendSourceKey")); r.setExpendName(text(source,"expendName"));
        r.setIndustryCode(text(source,"industryCode")); r.setIndustryName(text(source,"industryName"));
        r.setDepartmentCode(text(source,"departmentCode")); r.setDepartmentName(text(source,"departmentName"));
        r.setServiceTypeName(text(source,"serviceTypeName")); r.setChannelName(text(source,"channelName"));
        r.setEngineeringFeeRaw(text(source,"engineeringFeeRaw")); r.setEngineeringFee(decimal(source,"engineeringFeeRaw"));
        r.setSourceObjectId(text(source,"sourceObjectId")); r.setApplyType(text(source,"applyType"));
        r.setCompanyCode(text(source,"companyCode")); r.setCustomerProjectName(text(source,"customerProjectName"));
        r.setFinalCustomerName(text(source,"finalCustomerName")); r.setAgentName(text(source,"agentName"));
        r.setMajorProjectLevel(text(source,"majorProjectLevel")); r.setProjectAmount(decimal(source,"projectAmount"));
        r.setSubmitTime(time(source,"submitTime")); r.setPredictedBidTime(time(source,"predictedBidTime"));
        r.setContactName(text(source,"contactName")); r.setContactPhone(text(source,"contactPhone"));
        r.setAfEvidenceStatus("UNKNOWN"); return r;
    }

    private static Map<String,Object> payload(CrmExecutionOrderDO r) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("sourceSystem",r.getSourceSystem());m.put("executionNo",r.getExecutionNo());m.put("projectCode",r.getProjectCode());
        m.put("projectName",r.getProjectName());m.put("salesRepCode",r.getSalesRepCode());m.put("salesRepName",r.getSalesRepName());
        m.put("marketCode",r.getMarketCode());m.put("marketName",r.getMarketName());m.put("systemSourceKey",r.getSystemSourceKey());
        m.put("systemName",r.getSystemName());m.put("expendSourceKey",r.getExpendSourceKey());m.put("expendName",r.getExpendName());
        m.put("industryCode",r.getIndustryCode());m.put("industryName",r.getIndustryName());m.put("departmentCode",r.getDepartmentCode());
        m.put("departmentName",r.getDepartmentName());m.put("serviceTypeName",r.getServiceTypeName());m.put("channelName",r.getChannelName());
        m.put("engineeringFeeRaw",r.getEngineeringFeeRaw());m.put("sourceObjectId",r.getSourceObjectId());m.put("applyType",r.getApplyType());
        m.put("companyCode",r.getCompanyCode());m.put("customerProjectName",r.getCustomerProjectName());
        m.put("finalCustomerName",r.getFinalCustomerName());m.put("agentName",r.getAgentName());m.put("majorProjectLevel",r.getMajorProjectLevel());
        m.put("projectAmount",r.getProjectAmount() == null ? null : r.getProjectAmount().stripTrailingZeros());m.put("submitTime",r.getSubmitTime());m.put("predictedBidTime",r.getPredictedBidTime());
        m.put("contactName",r.getContactName());m.put("contactPhone",r.getContactPhone()); return m;
    }
    private static Change change(Row r, Long id, String action, Map<String,Object> before, String key, String message) {
        Map<String,Object> after = new LinkedHashMap<>(r.fields()); after.put("_businessKey", key);
        return new Change(r.object(),r.sourceKey(),id,action,before,after,message);
    }
    private static String key(String source,String no){return source+"|"+no;}
    private static String text(Row r,String name){Object v=r.fields().get(name);return v==null||v.toString().isBlank()?null:v.toString().trim();}
    private static String required(Row r,String name){String v=text(r,name);if(v==null)throw new IllegalArgumentException("必填来源字段缺失: "+name);return v;}
    private static BigDecimal decimal(Row r,String name){String v=text(r,name);if(v==null)return null;try{return new BigDecimal(v.replace(",",""));}catch(NumberFormatException e){throw new IllegalArgumentException("数值字段格式错误: "+name);}}
    private static LocalDateTime time(Row r,String name){String v=text(r,name);if(v==null)return null;try{return LocalDateTime.parse(v.replace(' ','T'));}catch(Exception e){throw new IllegalArgumentException("时间字段格式错误: "+name);}}
    private static final List<String> SOURCE_FIELDS=List.of("projectCode","projectName","salesRepCode","salesRepName","marketCode","marketName","systemSourceKey","systemName","expendSourceKey","expendName","industryCode","industryName","departmentCode","departmentName","serviceTypeName","channelName","engineeringFeeRaw","sourceObjectId","applyType","companyCode","customerProjectName","finalCustomerName","agentName","majorProjectLevel","projectAmount","submitTime","predictedBidTime","contactName","contactPhone");
    private record Candidate(String key,CrmExecutionOrderDO row,Map<String,Object> payload,String action){}
    private record Plan(List<Change> changes,List<Candidate> candidates){}
}
