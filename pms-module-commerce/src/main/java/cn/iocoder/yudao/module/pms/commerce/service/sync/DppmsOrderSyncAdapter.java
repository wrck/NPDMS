package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ErpOrderSyncQuery;
import cn.iocoder.yudao.module.pms.commerce.service.authority.AuthorityPayloadCanonicalizer;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** DPPMS source chunks enter the existing COM authority transaction, never a second table writer. */
@Component
@RequiredArgsConstructor
public class DppmsOrderSyncAdapter implements DataSyncAdapter {
    public static final String KEY = "DPPMS_ERP_ORDER";
    private final ErpOrderSyncMapper mapper;
    private final CommerceAuthorityIngestApi authority;
    private final AuthorityPayloadCanonicalizer canonicalizer;
    private final DppmsOrderFieldCarryOwner fieldCarryOwner;
    private final DppmsOrderLineFieldCarryOwner lineFieldCarryOwner;

    @Override
    public Descriptor descriptor() {
        List<Field> common = List.of(field("erpSource", "ERP来源", true), field("companyCode", "公司编码", true),
                field("orderNo", "订单号", true), field("orderType", "订单类型（0销售/1退货）", true),
                field("sourceUpdatedAt", "来源版本时间", true), field("migrationIssue", "来源预检问题", false));
        List<Field> head = new ArrayList<>(common);
        for (String name : List.of("customerCode", "customerName", "salesType", "sourceProjectName", "orderComment",
                "orderCreateTime", "customerRequiredTime", "customInfo")) head.add(field(name, name, false));
        head.add(field("contractNo", "主合同号", false));
        head.add(field("executionNo", "主执行单号", false));
        head.add(new Field("fieldCarryProof", "限定字段承接保护", "JSON", false));
        head.add(field("originalSourceFingerprint", "完整原字段核验", false));
        head.add(new Field("originalSourceFields", "完整16原字段", "JSON", false));
        List<Field> line = new ArrayList<>(common);
        line.add(field("migrationParentExists", "来源父订单预检通过", false));
        line.add(field("lineNo", "行号", true));
        for (String name : List.of("productCode", "productDesc", "orderQuantity", "openQuantity", "bundleCode",
                "warrantyMonth", "profitCenter", "realExecutionNo", "unitCode", "customInfo")) line.add(field(name, name, false));
        line.add(new Field("lineFieldCarryProof", "限定订单行字段保护", "JSON", false));
        line.add(field("originalSourceFingerprint", "完整原字段核验", false));
        line.add(new Field("originalSourceFields", "完整16原字段", "JSON", false));
        return new Descriptor(KEY, "DPPMS ERP销售订单与订单行",
                List.of(new ObjectDescriptor("ORDER", "销售订单", head, "COM", "SalesOrder", "com_sales_order", false),
                        new ObjectDescriptor("LINE", "订单行", line, "COM", "OrderLine", "com_sales_order_line", false)),
                List.of("RETAIN"), List.of("UPSERT"), false, true);
    }

    private static Field field(String name, String label, boolean required) {
        return new Field(name, label, "STRING", required);
    }

    @Override public List<Change> preview(Batch batch) {
        if(hasLineFieldCarry(batch) || hasFieldCarry(batch))return carry(batch,false);
        return plan(batch,true).changes();
    }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public boolean supportsStreaming() { return true; }
    @Override public void refreshCaches() { /* COM order queries do not cache these records. */ }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Change> apply(Batch batch) {
        if(hasLineFieldCarry(batch) || hasFieldCarry(batch))return carry(batch,true);
        Plan plan = plan(batch,false);
        if (plan.changes().stream().anyMatch(c -> "CONFLICT".equals(c.action()))) return plan.changes();
        if (!plan.orders().isEmpty() || !plan.lines().isEmpty()) {
            String event = "dppms-order-sync:" + UUID.randomUUID();
            authority.ingestBatch(new CommerceAuthorityBatchCommand(TenantContextHolder.getRequiredTenantId(),
                    event, event, "ERP", event, List.of(), plan.orders(), plan.lines(), List.of(), LocalDateTime.now(), event));
        }
        Map<String, Long> ids = new HashMap<>();
        if (plan.hasOrderRows())
            mapper.selectOrders(plan.query()).forEach(r -> ids.put("ORDER:" + r.getSourceKey(), r.getId()));
        if (plan.hasLineRows())
            mapper.selectLines(plan.query()).forEach(r -> ids.put("LINE:" + r.getSourceKey(), r.getId()));
        return plan.changes().stream().map(c -> {
            if ("ISSUE".equals(c.action())) return c;
            Long id = ids.get(c.object() + ":" + c.after().get("_targetSourceKey"));
            if (id == null) throw new IllegalStateException("商务接收成功后未找到目标记录");
            return new Change(c.object(), c.sourceKey(), id, c.action(), c.before(), c.after(), c.message());
        }).toList();
    }

    private Plan plan(Batch batch,boolean preview) {
        if (!"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy())
                || batch.clearBeforeLoad() || batch.adoptExisting())
            throw new IllegalArgumentException("DPPMS订单使用追加更新并保留源端消失记录，不清空或接管其他来源");
        boolean hasOrderRows=batch.rows().stream().anyMatch(r->"ORDER".equals(r.object()));
        boolean hasLineRows=batch.rows().stream().anyMatch(r->"LINE".equals(r.object()));
        var query = new ErpOrderSyncQuery(TenantContextHolder.getRequiredTenantId(), batch.rows().stream()
                .map(r -> text(r, "orderNo")).filter(Objects::nonNull).distinct().toList());
        Map<String, SalesOrderDO> orders = new HashMap<>();
        Map<String, String> orderBusiness = new HashMap<>();
        Set<String> ambiguousOrderBusinesses = new HashSet<>();
        if(hasOrderRows||hasLineRows) for (var r : mapper.selectOrders(query)) {
            if (orders.put(r.getSourceKey(), r)!=null) throw new IllegalStateException("DPPMS_ORDER_SOURCE_IDENTITY_AMBIGUOUS");
            String company=targetCompanyCode(r);
            String businessKey=company + "|" + r.getOrderType() + "|" + r.getOrderNo();
            String previous=orderBusiness.putIfAbsent(businessKey,r.getSourceKey());
            if (previous!=null && !previous.equals(r.getSourceKey())) {
                var earlier=orders.get(previous);
                String[] identity=r.getSourceKey().split("\\|",-1);
                boolean sameDppms=identity.length==5 && sameDppmsOrderIdentity(previous,identity[1],company,r.getOrderType(),r.getOrderNo())
                        && sameDppmsOrderIdentity(r.getSourceKey(),identity[1],company,r.getOrderType(),r.getOrderNo());
                if(!sameDppms || Boolean.TRUE.equals(earlier.getDeleted())==Boolean.TRUE.equals(r.getDeleted())) {
                    ambiguousOrderBusinesses.add(businessKey);
                    continue;
                }
                // Retain the deleted alias in the identity map: a binding to it still fails below.
                if(Boolean.TRUE.equals(earlier.getDeleted()))orderBusiness.put(businessKey,r.getSourceKey());
            }
        }
        Map<String, SalesOrderLineDO> lines = new HashMap<>();
        if(hasLineRows) for (var r : mapper.selectLines(query)) lines.put(r.getSourceKey(), r);
        Map<String, CommerceSalesOrderFact> incomingOrders = new LinkedHashMap<>();
        Map<String, CommerceOrderLineFact> incomingLines = new LinkedHashMap<>();
        Map<String, Binding> bindings = new HashMap<>();
        batch.bindings().forEach(b -> bindings.put(b.object() + ":" + b.sourceKey(), b));
        List<Change> changes = new ArrayList<>();
        // The caller may configure source objects in either order; COM still receives parents first.
        List<Row> rows = batch.rows().stream().sorted(Comparator.comparing(r -> !"ORDER".equals(r.object()))).toList();
        for (Row row : rows) {
            try {
                String issue = text(row, "migrationIssue");
                if (issue != null) { changes.add(issue(row, issue)); continue; }
                String source = required(row, "erpSource"), company = required(row, "companyCode");
                if (DppmsCompanyCodeConverter.mappingValues().containsKey(company)) company=DppmsCompanyCodeConverter.fromSource(company);
                else if (!DppmsCompanyCodeConverter.mappingValues().containsValue(company))
                    throw new IllegalArgumentException("DPPMS_COMPANY_CODE_UNMAPPED: "+company);
                String type = required(row, "orderType"), number = required(row, "orderNo");
                if (!Set.of("SAP", "D365", "SMS").contains(source) || !Set.of("0", "1").contains(type))
                    throw new IllegalArgumentException("未知ERP来源或订单类型");
                String parentKey = "DPPMS|" + source + "|" + company + "|" + type + "|" + number;
                String businessKey=company+"|"+type+"|"+number;
                if (ambiguousOrderBusinesses.contains(businessKey)) {
                    changes.add(conflict(row,"DPPMS_ORDER_BUSINESS_IDENTITY_AMBIGUOUS: 多个现存订单身份，禁止任选目标"));
                    continue;
                }
                String occupied=orderBusiness.get(businessKey);
                if (occupied!=null && !occupied.equals(parentKey)) {
                    if (!sameDppmsOrderIdentity(occupied,source,company,type,number)) {
                        changes.add(conflict(row,"订单业务键已由其他来源身份占用"));continue;
                    }
                    // Reuse the approved company's existing Owner identity, including its historic raw-company key.
                    parentKey=occupied;
                }
                LocalDateTime time = time(row, "sourceUpdatedAt");
                if (time == null) throw new IllegalArgumentException("缺失来源版本时间");
                String version = time.toString();
                CommerceSourceLifecycleStatus lifecycle = "1".equals(type)
                        ? CommerceSourceLifecycleStatus.RETURNED : CommerceSourceLifecycleStatus.ACTIVE;
                String targetKey;
                String action;
                Long id;
                if ("ORDER".equals(row.object())) {
                    targetKey = parentKey;
                    SalesOrderDO old = orders.get(targetKey);
                    if(row.targetId()!=null && (old==null || !Objects.equals(row.targetId(),old.getId()))) {
                        changes.add(conflict(row,"已绑定来源不能重建订单或改绑其他目标"));continue;
                    }
                    if (old != null && Boolean.TRUE.equals(old.getDeleted())) {
                        changes.add(conflict(row, "目标订单已删除，不能重新创建")); continue;
                    }
                    if (old!=null && !"ERP".equals(old.getSourceSystem()) && !source.equals(old.getSourceSystem())) {
                        changes.add(conflict(row,"目标订单ERP系统与来源身份不一致"));continue;
                    }
                    var fact = new CommerceSalesOrderFact(targetKey, old == null ? null : old.getSourceVersion(),
                            version, company, number, type, text(row, "customerCode"), originalText(row, "customerName"),
                            null, null, lifecycle, time, text(row, "salesType"), originalText(row, "sourceProjectName"),
                            originalText(row, "orderComment"), time(row, "orderCreateTime"), time(row, "customerRequiredTime"),
                            originalText(row, "customInfo"), text(row, "contractNo"), text(row, "executionNo"));
                    var previous = incomingOrders.get(targetKey);
                    if (previous != null && !canonicalizer.orderPayload(previous).equals(canonicalizer.orderPayload(fact))) {
                        throw new IllegalArgumentException("同一批次订单主档字段冲突");
                    }
                    action = decision(old == null ? null : old.getSourceVersion(), version,
                            old == null ? null : old.getSourceUpdatedAt(), time,
                            old == null ? null : canonicalizer.orderPayload(old), canonicalizer.orderPayload(fact));
                    if ("UNCHANGED".equals(action) && !source.equals(old.getSourceSystem())) action="UPDATED";
                    id = old == null ? null : old.getId();
                    if (!"UNCHANGED".equals(action)) incomingOrders.put(targetKey, fact);
                } else if ("LINE".equals(row.object())) {
                    SalesOrderDO parent = orders.get(parentKey);
                    boolean sourceParent=preview && Set.of("1","true").contains(Objects.toString(text(row,"migrationParentExists"),""));
                    if (!incomingOrders.containsKey(parentKey) && (parent == null || Boolean.TRUE.equals(parent.getDeleted()))
                            && !(sourceParent && parent==null)) {
                        changes.add(issue(row, "PARENT_ORDER_MISSING: 未找到同ERP来源、公司、类型和订单号的父订单")); continue;
                    }
                    String lineNo = required(row, "lineNo");
                    targetKey = parentKey + "|" + lineNo;
                    SalesOrderLineDO old = lines.get(targetKey);
                    if(row.targetId()!=null && (old==null || !Objects.equals(row.targetId(),old.getId()))) {
                        changes.add(conflict(row,"已绑定来源不能重建订单行或改绑其他目标"));continue;
                    }
                    if (old != null && Boolean.TRUE.equals(old.getDeleted())) {
                        changes.add(conflict(row, "目标订单行已删除，不能重新创建")); continue;
                    }
                    if (old!=null && !"ERP".equals(old.getSourceSystem()) && !source.equals(old.getSourceSystem())) {
                        changes.add(conflict(row,"目标订单行ERP系统与来源身份不一致"));continue;
                    }
                    BigDecimal qty = decimal(row, "orderQuantity"), open = decimal(row, "openQuantity");
                    BigDecimal delivered=qty==null||open==null?null:qty.subtract(open);
                    int scale=0;
                    for (BigDecimal value:new BigDecimal[]{qty,open,delivered})
                        if (value!=null) scale=Math.max(scale,Math.max(0,value.stripTrailingZeros().scale()));
                    String unit = text(row, "unitCode");
                    var fact = new CommerceOrderLineFact(targetKey, old == null ? null : old.getSourceVersion(),
                            version, parentKey, lineNo, originalText(row, "productCode"), originalText(row, "productDesc"), null,
                            qty, open, delivered, unit, scale,
                            unit == null || qty == null ? "PENDING_AUTHORITY" : "CONFIRMED", lifecycle, time,
                            type, originalText(row, "bundleCode"), originalText(row, "profitCenter"), originalText(row, "realExecutionNo"),
                            decimal(row, "warrantyMonth") == null ? null : decimal(row, "warrantyMonth").intValueExact(),
                            originalText(row, "customInfo"));
                    var previous = incomingLines.get(targetKey);
                    if (previous != null && !canonicalizer.linePayload(previous).equals(canonicalizer.linePayload(fact)))
                        throw new IllegalArgumentException("同一批次订单行字段冲突");
                    action = decision(old == null ? null : old.getSourceVersion(), version,
                            old == null ? null : old.getSourceUpdatedAt(), time,
                            old == null ? null : canonicalizer.linePayload(old, parentKey), canonicalizer.linePayload(fact));
                    if ("UNCHANGED".equals(action) && !source.equals(old.getSourceSystem())) action="UPDATED";
                    id = old == null ? null : old.getId();
                    if (!"UNCHANGED".equals(action)) incomingLines.put(targetKey, fact);
                } else throw new IllegalArgumentException("未知订单迁移对象");
                Map<String, Object> after = new LinkedHashMap<>(row.fields());
                after.put("_targetSourceKey", targetKey);
                Binding prior = bindings.get(row.object() + ":" + row.sourceKey());
                changes.add(new Change(row.object(), row.sourceKey(), id, action,
                        prior == null ? Map.of() : prior.lastFields(), after,
                        "LINE".equals(row.object()) && text(row, "unitCode") == null ? "计量单位缺失，保留为空并待核对" : null));
            } catch (CommerceAuthorityIngestException | IllegalArgumentException exception) {
                changes.add(issue(row, exception.getMessage()));
            }
        }
        // Conflicting rows must not leave an earlier candidate for the same business key writable.
        if (changes.stream().anyMatch(c -> "ISSUE".equals(c.action()) && c.message().startsWith("同一批次")))
            throw new IllegalArgumentException("来源预检未隔离同业务键冲突，整批停止");
        return new Plan(query, changes, List.copyOf(incomingOrders.values()), List.copyOf(incomingLines.values()),
                hasOrderRows,hasLineRows);
    }

    private static String decision(String oldVersion, String version, LocalDateTime oldSourceTime,
                                   LocalDateTime sourceTime, String oldPayload, String payload) {
        if (oldVersion == null) return "CREATED";
        if (oldVersion.equals(version)) {
            if (!Objects.equals(oldPayload, payload)) throw new IllegalArgumentException("SOURCE_VERSION_PAYLOAD_CONFLICT: 同版本内容不同");
            return "UNCHANGED";
        }
        // Owner sourceVersion is an opaque revision token; ordering uses the separate source clock.
        if (oldSourceTime == null)
            throw new IllegalArgumentException("SOURCE_TIME_MISSING: existing source timestamp is required");
        if (sourceTime.isBefore(oldSourceTime))
            throw new IllegalArgumentException("STALE_SOURCE_VERSION: 来源版本早于目标版本");
        return "UPDATED";
    }
    private static Change issue(Row r, String message) {
        return new Change(r.object(), r.sourceKey(), null, "ISSUE", Map.of(), r.fields(), message);
    }
    private static String targetCompanyCode(SalesOrderDO row) {
        String company=row.getCompanyCode();
        if(row.getSourceKey()!=null && row.getSourceKey().startsWith("DPPMS|")
                && DppmsCompanyCodeConverter.mappingValues().containsKey(company))
            return DppmsCompanyCodeConverter.fromSource(company);
        return company;
    }
    static boolean sameDppmsOrderIdentity(String key,String source,String company,String type,String number) {
        if(key==null || source==null)return false;
        String[] parts=key.split("\\|",-1);
        if (parts.length!=5 || !"DPPMS".equals(parts[0]) || !source.equals(parts[1])
                || !type.equals(parts[3]) || !number.equals(parts[4])) return false;
        if (company.equals(parts[2])) return true;
        try { return company.equals(DppmsCompanyCodeConverter.fromSource(parts[2])); }
        catch (IllegalArgumentException unknownCompany) { return false; }
    }
    private static String originalText(Row row,String field) {
        Object value=row.fields().get(field);return value==null?null:value.toString();
    }

    private static boolean hasFieldCarry(Batch batch) { return batch.rows().stream().anyMatch(r->r.fields().containsKey("fieldCarryProof")); }
    private static boolean hasLineFieldCarry(Batch batch) { return batch.rows().stream().anyMatch(r->r.fields().containsKey("lineFieldCarryProof")); }
    private List<Change> carry(Batch batch,boolean apply) {
        boolean line=hasLineFieldCarry(batch);
        String object=line?"LINE":"ORDER",proofKey=line?"lineFieldCarryProof":"fieldCarryProof";
        if (line && hasFieldCarry(batch) || batch.rows().stream().anyMatch(row->!object.equals(row.object()))
                || batch.clearBeforeLoad() || batch.adoptExisting() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("DPPMS_FIELD_CARRY_SCOPE_INVALID");
        var eligible=new ArrayList<Row>();var results=new ArrayList<Change>();
        for (var row:batch.rows()) {
            String problem=text(row,"migrationIssue");
            if (problem!=null) results.add(issue(row,problem));
            else if (row.fields().get(proofKey)==null)
                results.add(issue(row,"FIELD_CARRY_PROOF_MISSING: 来源或目标仍有未解决冲突，禁止自动接管"));
            else eligible.add(row);
        }
        if (!eligible.isEmpty()) {
            var selected=new Batch(batch.owner(),List.copyOf(eligible),batch.bindings(),batch.full(),
                    batch.missingPolicy(),batch.adoptExisting(),batch.loadingMode(),batch.clearBeforeLoad());
            if (line) results.addAll(apply?lineFieldCarryOwner.apply(selected):lineFieldCarryOwner.preview(selected));
            else results.addAll(apply?fieldCarryOwner.apply(selected,fieldCarryEntries(selected))
                    :fieldCarryOwner.preview(selected,fieldCarryEntries(selected)));
        }
        return List.copyOf(results);
    }
    private List<DppmsOrderFieldCarryOwner.Entry> fieldCarryEntries(Batch batch) {
        var query=new ErpOrderSyncQuery(TenantContextHolder.getRequiredTenantId(),batch.rows().stream()
                .map(r->required(r,"orderNo")).distinct().toList());
        Map<Long,SalesOrderDO> targets=new HashMap<>();mapper.selectOrders(query).forEach(r->targets.put(r.getId(),r));
        List<DppmsOrderFieldCarryOwner.Entry> entries=new ArrayList<>();
        for(var row:batch.rows()) {
            if(!"ORDER".equals(row.object()) || text(row,"migrationIssue")!=null || row.fields().get("fieldCarryProof")==null)
                throw new IllegalArgumentException("DPPMS_ORDER_FIELD_CARRY_SOURCE_INVALID");
            var proof=JsonUtils.parseObject(JsonUtils.toJsonString(row.fields().get("fieldCarryProof")),DppmsOrderFieldCarryOwner.Proof.class);
            var target=targets.get(row.targetId());if(target==null)throw new IllegalArgumentException("DPPMS_ORDER_FIELD_CARRY_BINDING_REQUIRED");
            String raw=originalText(row,"originalSourceFields");
            Map<String,Object> originals=JsonUtils.parseMap(raw);
            String source=required(row,"erpSource"),company=required(row,"companyCode"),type=required(row,"orderType");
            if(DppmsCompanyCodeConverter.mappingValues().containsKey(company))company=DppmsCompanyCodeConverter.fromSource(company);
            else if(!DppmsCompanyCodeConverter.mappingValues().containsValue(company))throw new IllegalArgumentException("DPPMS_COMPANY_CODE_UNMAPPED");
            if(!Set.of("SAP","D365","SMS").contains(source) || !Set.of("0","1").contains(type))throw new IllegalArgumentException("未知ERP来源或订单类型");
            var fact=new CommerceSalesOrderFact(target.getSourceKey(),target.getSourceVersion(),target.getSourceVersion(),company,
                    required(row,"orderNo"),type,text(row,"customerCode"),originalText(row,"customerName"),null,null,
                    "1".equals(type)?CommerceSourceLifecycleStatus.RETURNED:CommerceSourceLifecycleStatus.ACTIVE,time(row,"sourceUpdatedAt"),
                    text(row,"salesType"),originalText(row,"sourceProjectName"),originalText(row,"orderComment"),time(row,"orderCreateTime"),
                    time(row,"customerRequiredTime"),originalText(row,"customInfo"),text(row,"contractNo"),text(row,"executionNo"));
            entries.add(new DppmsOrderFieldCarryOwner.Entry(row,fact,proof,required(row,"originalSourceFingerprint"),originals));
        }
        return entries;
    }
    private static Change conflict(Row r, String message) {
        return new Change(r.object(), r.sourceKey(), null, "CONFLICT", Map.of(), r.fields(), message);
    }
    private static String text(Row r, String name) {
        Object value = r.fields().get(name);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }
    private static String required(Row r, String name) {
        String value = text(r, name);
        if (value == null) throw new IllegalArgumentException("必填来源字段缺失: " + name);
        return value;
    }
    private static LocalDateTime time(Row r, String name) {
        Object raw=r.fields().get(name);
        if(raw instanceof Number number)
            return java.time.Instant.ofEpochMilli(number.longValue()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        String value = text(r, name);
        return value == null ? null : LocalDateTime.parse(value.replace(' ', 'T'));
    }
    private static BigDecimal decimal(Row r, String name) {
        String value = text(r, name);
        return value == null ? null : new BigDecimal(value);
    }
    private record Plan(ErpOrderSyncQuery query, List<Change> changes,
                        List<CommerceSalesOrderFact> orders, List<CommerceOrderLineFact> lines,
                        boolean hasOrderRows,boolean hasLineRows) {}
}
