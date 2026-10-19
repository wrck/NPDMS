package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;


import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;

import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class DppmsDirectExecutionOrderTask implements DataSyncAdapter {
    public static final String KEY = "DPPMS_CRM_EXECUTION_ORDER_DIRECT";
    private final DirectExecutionOrderMapper mapper;
    private final DirectExecutionOrganizationReferences organizationReferences;

    private final SyncTaskService tasks;
    private final cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper runs;
    private final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    private final javax.sql.DataSource dataSource;
    public DppmsDirectExecutionOrderTask(DirectExecutionOrderMapper mapper,
            cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi organizations,
            SyncTaskService tasks,cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper runs,
            cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions,javax.sql.DataSource dataSource) {
        this.mapper=mapper;this.organizationReferences=new DirectExecutionOrganizationReferences(organizations);
        this.tasks=tasks;this.runs=runs;this.permissions=permissions;this.dataSource=dataSource;
    }
    private Long guard(Batch batch,boolean apply) {
        if(!batch.full())throw new IllegalArgumentException("执行单直写必须完整读取");
        long tenant=TenantContextHolder.getRequiredTenantId();
        var parts=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(parts.length!=3 || !"integration".equals(parts[0]) || !Long.toString(tenant).equals(parts[1]))
            throw new IllegalArgumentException("执行单直写任务身份无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(parts[2]));
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!KEY.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || run==null
                || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))
            throw new IllegalArgumentException("执行单直写运行身份或版本失效");
        Long actor=SyncFieldMapper.primaryKey(run.getCreator());
        if(!permissions.hasAnyPermissions(actor,apply?new String[]{"pms:integration:execute"}:new String[]{"pms:integration:configure","pms:integration:execute"}))
            throw new IllegalArgumentException("执行单直写权限不足");
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("执行单直写必须处于活动事务");
        var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        try {if(connection.getTransactionIsolation()!=java.sql.Connection.TRANSACTION_REPEATABLE_READ)
            throw new IllegalStateException("执行单直写需要REPEATABLE_READ");}
        catch(java.sql.SQLException error){throw new IllegalStateException("无法确认执行单事务隔离",error);}
        finally{org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection,dataSource);}
        return actor;
    }
    @Override public Descriptor descriptor() {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("executionNo", "执行单编号", "STRING", true));
        fields.add(new Field("sourceSystem", "来源系统", "STRING", true));
        for (String name : SOURCE_FIELDS) fields.add(new Field(name, name, "STRING", false));
        fields.add(new Field("canonicalSourceId","整表最后原修订ID","LONG",false));
        for(String name:sourceFields())fields.add(new Field("canonical_"+name,"最后原修订 "+name,"STRING",false));
        return new Descriptor(KEY, "DPPMS项目执行单常规头", List.of(new ObjectDescriptor(
                "EXECUTION_ORDER", "项目执行单", fields, "COM", "CrmExecutionOrder", "com_crm_execution_order", false)),
                List.of("RETAIN"), List.of("UPSERT"), false, true);
    }

    @Override public List<Change> preview(Batch batch) { guard(batch,false); return plan(batch).changes(); }
    @Override public boolean supportsStreaming() { return true; }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public void refreshCaches() { }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Change> apply(Batch batch) {
        Long actor=guard(batch,true);
        Plan plan = plan(batch);
        if (plan.changes().stream().anyMatch(c -> "CONFLICT".equals(c.action()))) return plan.changes();
        if (plan.changes().stream().anyMatch(c -> "ISSUE".equals(c.action())))
            throw new IllegalArgumentException("执行单分块包含无效来源记录，当前分块未写入且不推进断点；请修正后重试");
        Map<String, Long> ids = new HashMap<>();
        List<DirectExecutionOrderRow> created = new ArrayList<>();
        List<DirectExecutionOrderRow> updated = new ArrayList<>();
        for (Candidate candidate : plan.candidates()) {
            if ("CREATED".equals(candidate.action())) created.add(candidate.row());
            else if ("UPDATED".equals(candidate.action())) updated.add(candidate.row());
        }
        for(var row:created) {
            row.setVersion(0L);row.setCreator(actor.toString());row.setUpdater(actor.toString());
            if(mapper.insertRow(row)!=1)throw new IllegalStateException("DPPMS_EXECUTION_CREATE_FAILED");
        }
        for(var row:updated) {
            row.setUpdater(actor.toString());
            if(mapper.updateRow(row)!=1)throw new IllegalStateException("DPPMS_EXECUTION_CONCURRENT_CHANGE");
        }
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
        var directory=organizationReferences.load();
        Map<String, Set<String>> numbersBySystem = new LinkedHashMap<>();
        for (Row row : batch.rows()) {
            String system = text(row, "sourceSystem"), number = text(row, "executionNo");
            if (system != null && number != null)
                numbersBySystem.computeIfAbsent(system, ignored -> new LinkedHashSet<>()).add(number);
        }
        Map<String, DirectExecutionOrderRow> existing = new HashMap<>();
        numbersBySystem.forEach((system, numbers) -> {
            List<String> keys = new ArrayList<>(numbers);
            for (int start = 0; start < keys.size(); start += 1000)
                mapper.selectIncoming(new DirectExecutionOrderMapper.IncomingQuery(tenantId, system,
                                keys.subList(start, Math.min(start + 1000, keys.size()))))
                        .forEach(row -> {
                            if(!Objects.equals(row.getTenantId(),tenantId) || !Objects.equals(row.getSourceSystem(),system) || !numbers.contains(row.getExecutionNo()))
                                throw new IllegalArgumentException("执行单目标业务身份不精确匹配");
                            if(existing.putIfAbsent(key(row.getSourceSystem(),row.getExecutionNo()),row)!=null)
                                throw new IllegalArgumentException("执行单目标业务身份重复");
                        });
        });
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        Map<String, Binding> bindings = new HashMap<>();
        for(var binding:batch.bindings())if(bindings.put(binding.object()+":"+binding.sourceKey(),binding)!=null)
            throw new IllegalArgumentException("执行单来源绑定重复");
        List<Change> changes = new ArrayList<>();
        for (Row source : batch.rows()) {
            try {
                SyncFieldMapper.primaryKey(source.sourceKey());
                if(!source.fields().containsKey("canonicalSourceId") || source.fields().containsKey("_sourcePrimaryKey"))
                    throw new IllegalArgumentException("执行单直写必须携带整表最后修订证据");
                var prior=bindings.get(source.object()+":"+source.sourceKey());
                if(prior!=null && !Objects.equals(prior.targetId(),source.targetId()))
                    throw new IllegalArgumentException("执行单来源绑定与当前目标不一致");
                DirectExecutionOrderRow row = convert(selectedRevision(source), tenantId);
                directory.associate(new DirectExecutionOrganizationReferences.Association(row,
                        DirectExecutionOrganizationReferences.Resolution.AVAILABLE));
                String businessKey = key(row.getSourceSystem(), row.getExecutionNo());
                DirectExecutionOrderRow old = existing.get(businessKey);
                if(old!=null && (!Objects.equals(old.getTenantId(),tenantId) || !Objects.equals(old.getSourceSystem(),row.getSourceSystem())
                        || !Objects.equals(old.getExecutionNo(),row.getExecutionNo())))
                    throw new IllegalArgumentException("执行单目标业务身份不精确匹配");
                if(source.targetId()!=null && (old==null || !Objects.equals(source.targetId(),old.getId()))) {
                    changes.add(change(source,null,"CONFLICT",Map.of(),businessKey,"已绑定来源不能重建执行单或改绑其他目标"));continue;
                }
                if (old != null && Boolean.TRUE.equals(old.getDeleted())) {
                    changes.add(change(source, old.getId(), "CONFLICT", Map.of(), businessKey, "目标执行单已删除，不能由同步重新创建"));
                    continue;
                }
                Map<String,Object> payload = payload(row);
                Candidate previous = candidates.get(businessKey);
                if (old != null && (old.getCompanyId()!=null && !Objects.equals(old.getCompanyId(),row.getCompanyId())
                        || old.getDepartmentId()!=null && !Objects.equals(old.getDepartmentId(),row.getDepartmentId())))
                    throw new IllegalArgumentException("DPPMS_EXECUTION_ORGANIZATION_REFERENCE_CONFLICT");
                String action = old == null ? "CREATED" : payload(old).equals(payload)
                        && Objects.equals(old.getCompanyId(),row.getCompanyId())
                        && Objects.equals(old.getDepartmentId(),row.getDepartmentId())
                        && Objects.equals(old.getCompanyName(),row.getCompanyName()) ? "UNCHANGED" : "UPDATED";
                if (old != null) {
                    row.setId(old.getId()); row.setVersion(old.getVersion());
                    row.setStatus(old.getStatus());row.setAfEvidenceStatus(old.getAfEvidenceStatus());
                    row.setPrimaryProjectId(old.getPrimaryProjectId());
                } else row.setStatus("ACTIVE");
                row.setSourceSyncTime(LocalDateTime.now());
                // Editable whole-table SQL selects the last revision across every page; legacy frozen
                // table tasks retain their previous ascending-id behavior. Each original row stays bound.
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

    private static List<String> sourceFields() {
        var names=new ArrayList<>(List.of("sourceSystem","executionNo"));names.addAll(SOURCE_FIELDS);return names;
    }
    private static Row selectedRevision(Row source) {
        if(!source.fields().containsKey("canonicalSourceId"))return source;
        var selected=new LinkedHashMap<String,Object>();
        String id=text(source,"canonicalSourceId");
        if(id==null || !id.matches("[1-9][0-9]*") || !source.sourceKey().matches("[1-9][0-9]*")
                || new BigDecimal(id).compareTo(new BigDecimal(source.sourceKey()))<0)
            throw new IllegalArgumentException("整表最后原修订身份无效");
        for(String name:sourceFields()) {
            if(!source.fields().containsKey("canonical_"+name))throw new IllegalArgumentException("整表最后原修订字段不完整");
            selected.put(name,source.fields().get("canonical_"+name));
        }
        if(!Objects.equals(text(source,"sourceSystem"),Objects.toString(selected.get("sourceSystem"),null))
                || !Objects.equals(text(source,"executionNo"),Objects.toString(selected.get("executionNo"),null)))
            throw new IllegalArgumentException("整表最后原修订身份与原业务键不一致");
        return new Row(source.object(),source.sourceKey(),selected,source.targetId());
    }
    private static DirectExecutionOrderRow convert(Row source, long tenantId) {
        DirectExecutionOrderRow r = new DirectExecutionOrderRow(); r.setTenantId(tenantId);
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
        String company=text(source,"companyCode");
        if (company!=null && !company.isBlank()) r.setCompanyCode(Set.of("001","002","003","004").contains(company)
                ?company:DppmsCompanyCodeConverter.fromSource(company));
        r.setCustomerProjectName(text(source,"customerProjectName"));
        r.setFinalCustomerName(text(source,"finalCustomerName")); r.setAgentName(text(source,"agentName"));
        r.setMajorProjectLevel(text(source,"majorProjectLevel")); r.setProjectAmount(decimal(source,"projectAmount"));
        r.setSubmitTime(time(source,"submitTime")); r.setPredictedBidTime(time(source,"predictedBidTime"));
        r.setContactName(text(source,"contactName")); r.setContactPhone(text(source,"contactPhone"));
        r.setAfEvidenceStatus("UNKNOWN"); return r;
    }

    private static Map<String,Object> payload(DirectExecutionOrderRow r) {
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
    private static String text(Row r,String name){Object v=r.fields().get(name);return v==null?null:v.toString();}
    private static String required(Row r,String name){String v=text(r,name);if(v==null||v.isBlank())throw new IllegalArgumentException("必填来源字段缺失: "+name);return v;}
    private static BigDecimal decimal(Row r,String name){String v=text(r,name);if(v==null||v.isBlank())return null;try{var amount=new BigDecimal(v.replace(",","").trim());if(amount.stripTrailingZeros().scale()>4||amount.precision()-amount.scale()>16)throw new NumberFormatException();return amount;}catch(NumberFormatException e){throw new IllegalArgumentException("数值字段格式或精度错误: "+name);}}
    private static LocalDateTime time(Row r,String name){String v=text(r,name);if(v==null||v.isBlank())return null;try{return LocalDateTime.parse(v.trim().replace(' ','T'));}catch(Exception e){throw new IllegalArgumentException("时间字段格式错误: "+name);}}
    private static final List<String> SOURCE_FIELDS=List.of("projectCode","projectName","salesRepCode","salesRepName","marketCode","marketName","systemSourceKey","systemName","expendSourceKey","expendName","industryCode","industryName","departmentCode","departmentName","serviceTypeName","channelName","engineeringFeeRaw","sourceObjectId","applyType","companyCode","customerProjectName","finalCustomerName","agentName","majorProjectLevel","projectAmount","submitTime","predictedBidTime","contactName","contactPhone");
    private record Candidate(String key,DirectExecutionOrderRow row,Map<String,Object> payload,String action){}
    private record Plan(List<Change> changes,List<Candidate> candidates){}
}
