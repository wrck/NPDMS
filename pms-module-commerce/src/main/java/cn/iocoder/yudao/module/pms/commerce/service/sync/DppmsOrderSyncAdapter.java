package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ErpOrderSyncQuery;
import cn.iocoder.yudao.module.pms.commerce.service.authority.AuthorityPayloadCanonicalizer;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** DPPMS source pages enter the existing COM authority transaction, never a second table writer. */
@Component
@RequiredArgsConstructor
public class DppmsOrderSyncAdapter implements DataSyncAdapter {
    public static final String KEY = "DPPMS_ERP_ORDER";
    private final ErpOrderSyncMapper mapper;
    private final CommerceAuthorityIngestApi authority;
    private final AuthorityPayloadCanonicalizer canonicalizer;

    @Override
    public Descriptor descriptor() {
        List<Field> common = List.of(field("erpSource", "ERP来源", true), field("companyCode", "公司编码", true),
                field("orderNo", "订单号", true), field("orderType", "订单类型（0销售/1退货）", true),
                field("sourceUpdatedAt", "来源版本时间", true), field("migrationIssue", "来源预检问题", false));
        List<Field> head = new ArrayList<>(common);
        for (String name : List.of("customerCode", "customerName", "salesType", "sourceProjectName", "orderComment",
                "orderCreateTime", "customerRequiredTime")) head.add(field(name, name, false));
        List<Field> line = new ArrayList<>(common);
        line.add(field("migrationParentExists", "来源父订单预检通过", false));
        line.add(field("lineNo", "行号", true));
        for (String name : List.of("itemCode", "itemDescription", "orderQuantity", "openQuantity", "bundleCode",
                "warrantyMonth", "profitCenter", "realExecutionNo", "unitCode")) line.add(field(name, name, false));
        return new Descriptor(KEY, "DPPMS ERP销售订单与订单行",
                List.of(new ObjectDescriptor("ORDER", "销售订单", head, "COM", "SalesOrder", "com_sales_order", false),
                        new ObjectDescriptor("LINE", "订单行", line, "COM", "OrderLine", "com_sales_order_line", false)),
                List.of("RETAIN"), List.of("UPSERT"), false);
    }

    private static Field field(String name, String label, boolean required) {
        return new Field(name, label, "STRING", required);
    }

    @Override public List<Change> preview(Batch batch) { return plan(batch,true).changes(); }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public boolean supportsStreaming() { return true; }
    @Override public void refreshCaches() { /* COM order queries do not cache these records. */ }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Change> apply(Batch batch) {
        Plan plan = plan(batch,false);
        if (plan.changes().stream().anyMatch(c -> "CONFLICT".equals(c.action()))) return plan.changes();
        if (!plan.orders().isEmpty() || !plan.lines().isEmpty()) {
            String event = "dppms-order-sync:" + UUID.randomUUID();
            authority.ingestBatch(new CommerceAuthorityBatchCommand(TenantContextHolder.getRequiredTenantId(),
                    event, event, "ERP", event, List.of(), plan.orders(), plan.lines(), List.of(), LocalDateTime.now(), event));
        }
        Map<String, Long> ids = new HashMap<>();
        mapper.selectOrders(plan.query()).forEach(r -> ids.put("ORDER:" + r.getSourceKey(), r.getId()));
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
        var query = new ErpOrderSyncQuery(TenantContextHolder.getRequiredTenantId(), batch.rows().stream()
                .map(r -> text(r, "orderNo")).filter(Objects::nonNull).distinct().toList());
        Map<String, SalesOrderDO> orders = new HashMap<>();
        Map<String, String> orderBusiness = new HashMap<>();
        for (var r : mapper.selectOrders(query)) {
            orders.put(r.getSourceKey(), r);
            orderBusiness.put(r.getCompanyCode() + "|" + r.getOrderType() + "|" + r.getOrderNo(), r.getSourceKey());
        }
        Map<String, SalesOrderLineDO> lines = new HashMap<>();
        for (var r : mapper.selectLines(query)) lines.put(r.getSourceKey(), r);
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
                String type = required(row, "orderType"), number = required(row, "orderNo");
                if (!Set.of("SAP", "D365", "SMS").contains(source) || !Set.of("0", "1").contains(type))
                    throw new IllegalArgumentException("未知ERP来源或订单类型");
                String parentKey = "DPPMS|" + source + "|" + company + "|" + type + "|" + number;
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
                    String occupied = orderBusiness.get(company + "|" + type + "|" + number);
                    if (occupied != null && !occupied.equals(targetKey)) {
                        changes.add(conflict(row, "订单业务键已由其他来源身份占用")); continue;
                    }
                    if (old != null && Boolean.TRUE.equals(old.getDeleted())) {
                        changes.add(conflict(row, "目标订单已删除，不能重新创建")); continue;
                    }
                    var fact = new CommerceSalesOrderFact(targetKey, old == null ? null : old.getSourceVersion(),
                            version, company, number, type, text(row, "customerCode"), text(row, "customerName"),
                            null, null, lifecycle, time, text(row, "salesType"), text(row, "sourceProjectName"),
                            text(row, "orderComment"), time(row, "orderCreateTime"), time(row, "customerRequiredTime"));
                    var previous = incomingOrders.get(targetKey);
                    if (previous != null && !canonicalizer.orderPayload(previous).equals(canonicalizer.orderPayload(fact))) {
                        throw new IllegalArgumentException("同一批次订单主档字段冲突");
                    }
                    action = decision(old == null ? null : old.getSourceVersion(), version,
                            old == null ? null : canonicalizer.orderPayload(old), canonicalizer.orderPayload(fact));
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
                    if (old != null && Boolean.TRUE.equals(old.getDeleted())) {
                        changes.add(conflict(row, "目标订单行已删除，不能重新创建")); continue;
                    }
                    BigDecimal qty = decimal(row, "orderQuantity"), open = decimal(row, "openQuantity");
                    String unit = text(row, "unitCode");
                    var fact = new CommerceOrderLineFact(targetKey, old == null ? null : old.getSourceVersion(),
                            version, parentKey, lineNo, text(row, "itemCode"), text(row, "itemDescription"), null, null,
                            qty, open, qty == null || open == null ? null : qty.subtract(open), unit, 0,
                            unit == null || qty == null ? "PENDING_AUTHORITY" : "CONFIRMED", lifecycle, time,
                            type, text(row, "bundleCode"), text(row, "profitCenter"), text(row, "realExecutionNo"),
                            decimal(row, "warrantyMonth") == null ? null : decimal(row, "warrantyMonth").intValueExact());
                    var previous = incomingLines.get(targetKey);
                    if (previous != null && !canonicalizer.linePayload(previous).equals(canonicalizer.linePayload(fact)))
                        throw new IllegalArgumentException("同一批次订单行字段冲突");
                    action = decision(old == null ? null : old.getSourceVersion(), version,
                            old == null ? null : canonicalizer.linePayload(old, parentKey), canonicalizer.linePayload(fact));
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
        return new Plan(query, changes, List.copyOf(incomingOrders.values()), List.copyOf(incomingLines.values()));
    }

    private static String decision(String oldVersion, String version, String oldPayload, String payload) {
        if (oldVersion == null) return "CREATED";
        if (oldVersion.equals(version)) {
            if (!Objects.equals(oldPayload, payload)) throw new IllegalArgumentException("SOURCE_VERSION_PAYLOAD_CONFLICT: 同版本内容不同");
            return "UNCHANGED";
        }
        if (LocalDateTime.parse(version).isBefore(LocalDateTime.parse(oldVersion)))
            throw new IllegalArgumentException("STALE_SOURCE_VERSION: 来源版本早于目标版本");
        return "UPDATED";
    }
    private static Change issue(Row r, String message) {
        return new Change(r.object(), r.sourceKey(), null, "ISSUE", Map.of(), r.fields(), message);
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
                        List<CommerceSalesOrderFact> orders, List<CommerceOrderLineFact> lines) {}
}
