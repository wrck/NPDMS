package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.Node;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.LongSupplier;

/** Pure P01 plan. Reviewed source SQL supplies whole definitions; existing formal rows remain authoritative. */
final class DirectOrganizationPlan {
    record Input(Batch batch, List<Node> catalogue, Map<String,Long> leaders, String actor,
                 LocalDateTime time, LongSupplier ids) {}
    record Mutation(Node before, Node after) {}
    record Result(List<Change> changes, List<Mutation> writes) {}
    private record Candidate(Row row, Map<String,Object> fields, Map<String,Object> alias, int rank) {
        String type() { return text(row.fields(), "objectName"); }
        String code() { return text(fields, "code"); }
        String key() { return type() + ":" + code(); }
    }
    private final Input input;
    private final Map<String,Node> catalogue = new LinkedHashMap<>();
    private final Map<String,Candidate> winners = new LinkedHashMap<>();
    private final Map<String,Binding> bindings = new HashMap<>();
    private final Map<String,Mutation> mutations = new LinkedHashMap<>();
    private final Set<String> visiting = new HashSet<>();
    private DirectOrganizationPlan(Input input) {
        this.input = input;
        for (Node node : input.catalogue()) {
            if (node.code() != null && catalogue.put(node.type() + ":" + node.code(), node) != null)
                throw new IllegalArgumentException("正式目录编码重复，不能任选关联");
        }
        for (Binding binding : input.batch().bindings()) {
            if (bindings.put(binding.object() + ":" + binding.sourceKey(), binding) != null)
                throw new IllegalArgumentException("来源绑定重复");
        }
    }
    static Result build(Input input) { return new DirectOrganizationPlan(input).build(); }
    private Result build() {
        List<Candidate> candidates = new ArrayList<>();
        Map<String,Map<String,Object>> groups = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>(), completion = new HashSet<>();
        for (Row row : input.batch().rows()) {
            if (!seen.add(identity(row))) throw new IllegalArgumentException("来源身份重复");
            if ("COMPANY_REFERENCE".equals(row.object())) continue;
            if (!DppmsDirectOrganizationTemplate.OBJECTS.contains(row.object())) throw new IllegalArgumentException("未知来源对象");
            var fields = json(row.fields().get("canonicalFields"));
            var alias = json(row.fields().get("sourceAlias"));
            int rank = switch (row.object()) { case "COMPANY", "DEPARTMENT" -> 0; case "ehr_department" -> 1; default -> 2; };
            Candidate candidate = new Candidate(row, fields, alias, rank);
            if (!Objects.equals(row.sourceKey(), text(alias,"id")) || !Integer.toString(rank).equals(text(row.fields(),"sortRank")))
                throw new IllegalArgumentException("来源原主键或裁决优先级变化");
            validate(candidate, completion);
            candidates.add(candidate);
            Candidate previous = winners.get(candidate.key());
            if (previous == null || rank < previous.rank() || rank == previous.rank()
                    && row.fields().get("canonicalFields").toString().compareTo(previous.row().fields().get("canonicalFields").toString()) < 0)
                winners.put(candidate.key(), candidate);
            var group = groups.computeIfAbsent(candidate.key(), ignored -> {
                var value = new LinkedHashMap<String,Object>();
                value.put("objectName", candidate.type()); value.put("businessKey", row.fields().get("businessKey"));
                value.put("sourceAliases", new ArrayList<Map<String,Object>>()); return value;
            });
            @SuppressWarnings("unchecked") var aliases = (List<Map<String,Object>>) group.get("sourceAliases");
            aliases.add(alias);
        }
        if (!completion.equals(new HashSet<>(DppmsEhrDepartmentCompletionTemplate.CODES)))
            throw new IllegalArgumentException("已批准八项补齐部门缺项，未写入目录");
        // Reuse the existing adjudicated projection to validate raw identities, exclusions, alias equivalence and winner lineage.
        winners.forEach((key, candidate) -> groups.get(key).put("canonicalFields", candidate.fields()));
        var original = DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P01", 1L));
        DppmsOrganizationProjection.expand(new SyncSourceProjection.Input(original, original.sources().getFirst(), new ArrayList<>(groups.values())));
        Map<String,String> sourceCompanies = new HashMap<>(), sourceDepartments = new HashMap<>();
        for (Candidate candidate : candidates) {
            if ("COMPANY".equals(candidate.row().object())) sourceCompanies.put(candidate.row().sourceKey(),candidate.code());
            if ("DEPARTMENT".equals(candidate.row().object())) sourceDepartments.put(candidate.row().sourceKey(),candidate.code());
        }
        for (Candidate candidate : candidates) if ("DEPARTMENT".equals(candidate.row().object())) {
            Map<String,Object> raw = json(candidate.alias().get("rawFields"));
            String companyId = text(raw,"compID"), parentId = text(raw,"adminID");
            if (companyId != null && !"0".equals(companyId)
                    && (!sourceCompanies.containsKey(companyId) || !Objects.equals(sourceCompanies.get(companyId),text(candidate.fields(),"companyCode"))))
                throw new IllegalArgumentException("EHR基础部门公司原主键与业务编码未精确匹配");
            if (parentId != null && !"0".equals(parentId)
                    && (!sourceDepartments.containsKey(parentId) || !Objects.equals(sourceDepartments.get(parentId),text(candidate.fields(),"parentCode"))))
                throw new IllegalArgumentException("EHR基础部门父级原主键与业务编码未精确匹配");
        }
        for (Candidate candidate : winners.values()) resolve(candidate);
        List<Change> changes = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Row row = candidate.row(); Node node = catalogue.get(candidate.key()); Binding prior = bindings.get(identity(row));
            checkBinding(row, node, prior);
            Mutation mutation = mutations.get(candidate.key());
            boolean main = winners.get(candidate.key()) == candidate;
            if (!main && prior != null && !Boolean.TRUE.equals(prior.lastFields().get("_referenceOnly"))) {
                Node before = mutation != null && mutation.before() != null ? mutation.before() : node;
                Object last = prior.lastFields().get("_directSnapshot");
                if (last == null || !before.equals(JsonUtils.getObjectMapper().convertValue(last,Node.class)))
                    throw new IllegalArgumentException("不能通过改为别名隐藏原承接记录的维护冲突");
            }
            String action = main && mutation != null ? mutation.before() == null ? "CREATED" : "UPDATED"
                    : prior == null ? "REFERENCED" : "UNCHANGED";
            Map<String,Object> after = new LinkedHashMap<>(row.fields());
            after.put("_directSnapshot", node);
            after.put("_referenceOnly", !main || mutation == null && (prior == null || Boolean.TRUE.equals(prior.lastFields().get("_referenceOnly"))));
            after.put("code", node.code());
            after.put("_parentTargetId", node.parentId());
            var company = catalogue.get("COMPANY:" + text(candidate.fields(),"companyCode"));
            after.put("_companyTargetId", company == null ? null : company.id());
            if (!"COMPANY".equals(candidate.type())) after.put("department", node);
            String director = text(candidate.fields(),"rawDirector");
            after.put("_directorSourceEmployeeId", director);
            changes.add(new Change(row.object(), row.sourceKey(), node.id(), action, prior == null ? Map.of() : prior.lastFields(), after,
                    director != null && !input.leaders().containsKey(director) ? "主管未有唯一已核实账号绑定，保留待关联" : "按裁决直写；保留既有正式定义及原来源别名"));
        }
        for (Row row : input.batch().rows()) if ("COMPANY_REFERENCE".equals(row.object())) {
            if (!Objects.equals(row.sourceKey(), text(row.fields(),"sourceCompanyId"))) throw new IllegalArgumentException("公司别名原身份不一致");
            String raw = text(row.fields(),"sourceCompanyCode"), code = DppmsCompanyCodeConverter.fromSource(raw);
            Node node = catalogue.get("COMPANY:" + code);
            if (node == null) throw new IllegalArgumentException("公司别名未唯一匹配正式公司，不创建临时公司");
            Binding prior = bindings.get(identity(row)); checkBinding(row,node,prior);
            if (prior != null && !Objects.equals(prior.lastFields().get("sourceCompanyCode"),raw)) throw new IllegalArgumentException("公司别名原编码变化，禁止改绑");
            Map<String,Object> after = new LinkedHashMap<>(row.fields()); after.put("companyCode",code); after.put("companyName",node.name());
            changes.add(new Change(row.object(),row.sourceKey(),node.id(),prior == null ? "REFERENCED" : "UNCHANGED",prior == null ? Map.of() : prior.lastFields(),after,"原公司码转换复用既有规则"));
        }
        return new Result(List.copyOf(changes), List.copyOf(mutations.values()));
    }
    private Node resolve(Candidate candidate) {
        String key = candidate.key();
        if (!visiting.add(key)) throw new IllegalArgumentException("部门父级形成环路");
        try {
            Node current = catalogue.get(key);
            if (mutations.containsKey(key)) return current;
            Binding prior = bindings.get(identity(candidate.row()));
            if (current != null) {
                checkBinding(candidate.row(),current,prior);
                Long requestedLeader = leader(candidate);
                if (requestedLeader != null && !Objects.equals(requestedLeader,current.leaderUserId())
                        && (prior == null || Boolean.TRUE.equals(prior.lastFields().get("_referenceOnly")) || current.leaderUserId() != null))
                    throw new IllegalArgumentException("主管只能补齐本任务承接且仍为空的记录");
                if (prior != null && !Boolean.TRUE.equals(prior.lastFields().get("_referenceOnly"))) {
                    Object last = prior.lastFields().get("_directSnapshot");
                    if (last == null || !current.equals(JsonUtils.getObjectMapper().convertValue(last,Node.class)))
                        throw new IllegalArgumentException("已迁入记录被维护，不能覆盖结构、历史或负责人");
                    Long leader = leader(candidate);
                    if ("DEPARTMENT".equals(current.type()) && leader != null && !Objects.equals(leader,current.leaderUserId())) {
                        if (current.leaderUserId() != null) throw new IllegalArgumentException("禁止覆盖既有部门主管");
                        Node updated = new Node(current.id(),current.type(),current.code(),current.name(),current.parentId(),current.sort(),leader,
                                current.phone(),current.email(),current.status(),current.version()+1,current.creator(),current.createTime(),input.actor(),input.time());
                        mutations.put(key,new Mutation(current,updated)); catalogue.put(key,updated); return updated;
                    }
                }
                return current;
            }
            if (prior != null || candidate.row().targetId() != null) throw new IllegalArgumentException("原绑定目标已删除或编码变化，不能重建");
            boolean company = "COMPANY".equals(candidate.type());
            Node parent = null;
            if (!company) {
                String companyCode = text(candidate.fields(),"companyCode");
                if (companyCode != null) required("COMPANY:" + companyCode);
                String parentCode = text(candidate.fields(),"parentCode");
                if (parentCode != null) {
                    parent = required("DEPARTMENT:" + parentCode);
                    if (parent.status() != 0) throw new IllegalArgumentException("父部门停用，不能新增下级");
                }
            }
            boolean history = "fnd_department".equals(candidate.row().object()) || "FND_HISTORY".equals(text(candidate.fields(),"auditRule"));
            LocalDateTime created = history ? SyncFieldMapper.sourceTime(candidate.fields().get("createTime")) : input.time();
            LocalDateTime updated = history && candidate.fields().get("updateTime") != null ? SyncFieldMapper.sourceTime(candidate.fields().get("updateTime")) : created;
            if (created == null || updated == null || created.getNano() != 0 || updated.getNano() != 0) throw new IllegalArgumentException("历史审计时间缺失或精度变化");
            Long leader = company ? null : leader(candidate);
            Node node = new Node(input.ids() == null ? null : input.ids().getAsLong(),candidate.type(),candidate.code(),text(candidate.fields(),"name"),
                    company ? null : parent == null ? Long.valueOf(0) : parent.id(),company ? null : 0,leader,null,null,
                    Integer.valueOf(text(candidate.fields(),"status")),0,history ? null : input.actor(),created,history ? null : input.actor(),updated);
            catalogue.put(key,node); mutations.put(key,new Mutation(null,node)); return node;
        } finally { visiting.remove(key); }
    }
    private Long leader(Candidate candidate) {
        String employee = text(candidate.fields(),"rawDirector");
        return employee != null && "ehr_department".equals(candidate.row().object()) ? input.leaders().get(employee) : null;
    }
    private Node required(String key) {
        Candidate candidate = winners.get(key);
        Node node = candidate == null ? catalogue.get(key) : resolve(candidate);
        if (node == null) throw new IllegalArgumentException("公司或父部门未精确匹配: " + key);
        return node;
    }
    private static void checkBinding(Row row,Node node,Binding prior) {
        if (row.targetId() != null && !Objects.equals(row.targetId(),node.id()) || prior != null && !Objects.equals(prior.targetId(),node.id()))
            throw new IllegalArgumentException("原来源绑定不能改绑");
    }
    private static void validate(Candidate c,Set<String> completion) {
        boolean company = "COMPANY".equals(c.row().object());
        if (!Objects.equals(c.type(),company ? "COMPANY" : "DEPARTMENT")) throw new IllegalArgumentException("组织类型变化");
        requiredText(c.code(),64); requiredText(text(c.fields(),"name"),company ? 128 : 30);
        if (!Set.of("0","1").contains(Objects.toString(c.fields().get("status"),""))) throw new IllegalArgumentException("组织状态未知");
        Map<String,Object> raw = json(c.alias().get("rawFields"));
        boolean fndRow = "fnd_department".equals(c.row().object());
        List<String> columns = company ? List.of("compID","compCode","compName","isDisabled")
                : fndRow ? DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS : DppmsEhrDepartmentCompletionTemplate.ORIGINAL_COLUMNS;
        if (!raw.keySet().containsAll(columns)) throw new IllegalArgumentException("来源原字段缺失，不能以空值代替缺列");
        String table = company ? "ehr_company" : fndRow ? "fnd_department" : "ehr_department";
        String binary = HexFormat.of().withUpperCase().formatHex(c.code().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!Objects.equals(table,text(c.alias(),"table")) || !Objects.equals(table,text(c.fields(),"sourceTable"))
                || !Objects.equals(c.row().sourceKey(),text(c.fields(),"sourceId")) || !Objects.equals(binary,text(c.row().fields(),"businessKey")))
            throw new IllegalArgumentException("原来源与主定义身份不一致");
        boolean history = fndRow || "ehr_department".equals(c.row().object()) && Set.of("167010","167020","167050").contains(c.code());
        Map<String,Object> authoritative = history && !fndRow ? json(c.alias().get("fndRawFields")) : raw;
        String expectedName = text(authoritative,company ? "compName" : history ? "departmentName" : "depName");
        if (company && expectedName != null) expectedName = expectedName.trim();
        String rawStatus = text(authoritative,history ? "status" : "isDisabled");
        String expectedStatus = history ? switch (Objects.toString(rawStatus,"")) {
            case "1" -> "0"; case "0" -> "1"; default -> throw new IllegalArgumentException("FND原状态未知");
        } : rawStatus == null ? "0" : rawStatus;
        if(fndRow) {
            if(!DppmsDirectOrganizationTemplate.FND_CODES.contains(c.code())
                    || !Objects.equals(c.code(),text(raw,"departmentNum")))throw new IllegalArgumentException("FND原编码不在已审核29码内");
            String exception=Map.of("160700002","541","310500001","391").get(c.code());
            if(exception!=null) {
                if(!exception.equals(text(c.fields(),"ehrDepartmentId")) || !"1".equals(text(c.fields(),"ehrDisabled")))
                    throw new IllegalArgumentException("FND已裁决EHR停用例外来源事实变化");
                expectedStatus="1";
            }
        }
        if (!Objects.equals(expectedName,text(c.fields(),"name")) || !Objects.equals(expectedStatus,text(c.fields(),"status")))
            throw new IllegalArgumentException("主定义偏离批准的名称/状态映射");
        if (history) {
            if (!authoritative.keySet().containsAll(DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS)
                    || !Objects.equals(fndRow?DppmsDepartmentCodeRules.parentForNew(c.code()):"160000",text(c.fields(),"parentCode")))
                throw new IllegalArgumentException("FND原字段或裁决父级缺失");
            var created = SyncFieldMapper.sourceTime(authoritative.get("createTime"));
            var updated = authoritative.get("updateTime") == null ? created : SyncFieldMapper.sourceTime(authoritative.get("updateTime"));
            var canonicalCreated = SyncFieldMapper.sourceTime(c.fields().get("createTime"));
            var canonicalUpdated = c.fields().get("updateTime") == null ? canonicalCreated : SyncFieldMapper.sourceTime(c.fields().get("updateTime"));
            if (created == null || updated == null || created.getNano() != 0 || updated.getNano() != 0
                    || !Objects.equals(created,canonicalCreated) || !Objects.equals(updated,canonicalUpdated)) throw new IllegalArgumentException("主定义历史时间偏离原审计证据");
        }
        if ("ehr_department".equals(c.row().object())) {
            if (!completion.add(c.code()) || !DppmsEhrDepartmentCompletionTemplate.CODES.contains(c.code())) throw new IllegalArgumentException("补齐部门范围变化");
            for (String field : List.of("depProperty","depNum","xOrder","remark","depCustom1","depCustom3","depCustom5"))
                if (raw.get(field) != null) throw new IllegalArgumentException("已裁决全空排除字段出现值: " + field);
            Map<String,Object> fnd = json(c.alias().get("fndRawFields"));
            if (history != (fnd.get("id") != null)) throw new IllegalArgumentException("FND/EHR主来源变化");
            if (history && (!"160000".equals(text(c.fields(),"parentCode")) || !"FND_HISTORY".equals(text(c.fields(),"auditRule"))))
                throw new IllegalArgumentException("FND补齐父部门或历史审计规则变化");
            if (text(c.fields(),"parentCode") == null) throw new IllegalArgumentException("补齐部门原父级缺失");
        }
        if ("DEPARTMENT".equals(c.row().object())) {
            if (text(raw,"depGrade") == null || text(raw,"depGrade").compareTo("2") > 0 || c.code().length() != 6)
                throw new IllegalArgumentException("EHR基础部门范围变化");
            for (var pair : Map.of("compID","companyCode","adminID","parentCode").entrySet()) {
                String id = text(raw,pair.getKey());
                if (id != null && !"0".equals(id) && text(c.fields(),pair.getValue()) == null) throw new IllegalArgumentException("EHR原公司/父部门引用缺失");
            }
        }
    }
    private static void requiredText(String value,int size) { if (value == null || value.isBlank() || value.length() > size) throw new IllegalArgumentException("组织字段缺失或超长，禁止截断"); }
    private static String identity(Row row) { return row.object() + ":" + row.sourceKey(); }
    static String text(Map<String,Object> fields,String key) { return Objects.toString(fields.get(key),null); }
    @SuppressWarnings("unchecked") static Map<String,Object> json(Object raw) {
        if (raw instanceof Map<?,?> map) return (Map<String,Object>) map;
        if (raw == null) throw new IllegalArgumentException("完整原字段或主定义缺失");
        return JsonUtils.parseMap(raw.toString());
    }
}
