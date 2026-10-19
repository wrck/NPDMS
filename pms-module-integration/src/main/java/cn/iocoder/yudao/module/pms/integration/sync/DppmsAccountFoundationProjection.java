package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Decodes the approved SQL business groups into the existing Owners' source contracts. */
final class DppmsAccountFoundationProjection {
    static final String ACCOUNTS = "accountFoundationAliases", ROLES = "accountFoundationRoles";
    private DppmsAccountFoundationProjection() {}
    static boolean supports(SyncSourceProjection.Input input) {
        return "DPPMS_ACCOUNT_FOUNDATION".equals(input.definition().adapter())
                && Set.of(ACCOUNTS,ROLES).contains(input.source().rowProjection());
    }
    static List<MysqlSyncReader.SourceRows> expand(SyncSourceProjection.Input input) {
        Map<String,SyncDefinition.Source> sources = new LinkedHashMap<>();
        Map<String,List<Map<String,Object>>> rows = new LinkedHashMap<>();
        Map<String,Set<String>> keys = new HashMap<>();
        for (var source : input.definition().sources()) if (Objects.equals(source.object(),input.source().object())
                || Objects.equals(source.queryRef(),input.source().object())) {
            sources.put(source.object(),source); rows.put(source.object(),new ArrayList<>()); keys.put(source.object(),new HashSet<>());
        }
        for (var group : input.rows()) {
            List<Alias> aliases = ACCOUNTS.equals(input.source().rowProjection()) ? accounts(group) : roles(group);
            for (var alias : aliases) {
                var source = sources.get(alias.object());
                if (source == null) throw new IllegalArgumentException("账号基础归并返回未声明的来源别名");
                var fields = new LinkedHashMap<>(alias.fields());
                Object key = fields.get(source.sourceKey());
                if (key == null || key.toString().isBlank() || key.toString().length()>128 || !keys.get(alias.object()).add(key.toString()))
                    throw new IllegalArgumentException("账号基础原来源身份缺失或重复");
                rows.get(alias.object()).add(Collections.unmodifiableMap(fields));
            }
            if (input.definition().maxRows()>0 && rows.values().stream().mapToInt(List::size).sum()>input.definition().maxRows())
                throw new IllegalArgumentException("账号基础归并原行超过整批容量限制");
        }
        return sources.values().stream().map(source -> new MysqlSyncReader.SourceRows(source.object(),source.sourceObject(),
                List.copyOf(rows.get(source.object())))).toList();
    }
    private record Alias(String object, Map<String,Object> fields) {}
    private static List<Alias> accounts(Map<String,Object> group) {
        String username = Objects.toString(group.get("username"),null);
        if (username != null && !Objects.equals(hex(username),group.get("businessKey")))
            throw new IllegalArgumentException("账号分组身份未按原登录名精确归并");
        JsonNode verification = tree(group.get("ehrVerification"));
        if (!verification.isObject()) throw new IllegalArgumentException("账号原EHR核验字段缺失");
        Map<String,String> references = Map.of("migrationLoginCount","loginCount","migrationLoginEmployeeId","employeeId",
                "migrationLoginWorkNo","workNo","migrationLoginName","name","migrationEmployeeId","verifiedEmployeeId",
                "migrationEmployeeWorkNo","verifiedWorkNo","migrationEmployeeName","verifiedName");
        JsonNode aliases = tree(group.get("sourceAliases"));
        if (!aliases.isArray()) throw new IllegalArgumentException("账号原来源别名缺失");
        List<Alias> result = new ArrayList<>();
        for (var alias : aliases) {
            String object = alias.path("sourceTable").asText();
            if (!DppmsUserSyncTemplate.COLUMNS.containsKey(object) || !alias.path("fields").isObject())
                throw new IllegalArgumentException("账号别名没有完整原字段");
            Map<String,Object> fields = fields(alias.path("fields"));
            String primary = "t_user".equals(object)?"user_id":"id";
            if (!Objects.equals(alias.path("sourceId").asText(),Objects.toString(fields.get(primary),null)))
                throw new IllegalArgumentException("账号别名与原主键不同");
            if (!"t_user_info".equals(object)) {
                String actual = Objects.toString(fields.get("t_user".equals(object)?"user_name":"username"),null);
                if (!Objects.equals(username,actual)) throw new IllegalArgumentException("账号别名混入其他登录名");
                references.forEach((target,field) -> fields.put(target,value(verification.path(field))));
            }
            result.add(new Alias(object,fields));
            if ("fnd_user_info".equals(object)) result.add(new Alias("FND_USER_ROLE",fields));
        }
        return result;
    }
    private static List<Alias> roles(Map<String,Object> group) {
        String code = Objects.toString(group.get("code"),null);
        if (code==null || !Objects.equals(hex(code),group.get("businessKey")))
            throw new IllegalArgumentException("角色分组编码缺失或未精确归并");
        int variants = Integer.parseInt(Objects.toString(group.get("definitionVariants"),"-1"));
        JsonNode canonical = tree(group.get("canonicalFields")), aliases = tree(group.get("sourceAliases"));
        if (variants>1 || variants<0 || variants==1 && !canonical.isObject() || !aliases.isArray())
            throw new IllegalArgumentException("同一角色存在不同原定义或缺失来源，不能任选覆盖");
        List<Alias> result = new ArrayList<>();
        for (var alias : aliases) {
            String table = alias.path("table").asText(), id=alias.path("id").asText(), sourceCode=alias.path("sourceCode").asText();
            boolean fnd = "fnd_roles".equals(table);
            if (!fnd && !"t_role".equals(table)) throw new IllegalArgumentException("角色来源表未获批准");
            if (fnd && "15".equals(id)) continue;
            String expectedSource=fnd?Objects.toString(DppmsRoleCodes.FND_CODES.get(id),null):DppmsRoleCodes.T_SOURCE_CODES.get(id);
            String expectedCode=expectedSource==null?null:fnd?DppmsRoleCodes.targetFnd(expectedSource):Objects.toString(DppmsRoleCodes.T_CODES.get(expectedSource),null);
            if (!Objects.equals(sourceCode,expectedSource) || !Objects.equals(code,expectedCode))
                throw new IllegalArgumentException("角色原身份与批准编码映射不同");
            if(!fnd && !Objects.equals(sourceCode,alias.path("originalRoleName").asText()))
                throw new IllegalArgumentException("t_role原业务编码与批准身份不同，不能仅凭ID覆盖");
            String object=fnd?"FND_ROLE":"T_ROLE";
            boolean reference=DppmsRoleCodes.reference(object,sourceCode);
            if (!reference && !canonical.isObject()) throw new IllegalArgumentException("角色主定义缺失");
            Map<String,Object> fields=new LinkedHashMap<>();
            fields.put("sourceRoleId",id);fields.put("sourceCode",sourceCode);
            fields.put("name",reference?null:value(canonical.path("name")));
            fields.put("sort",reference?null:value(canonical.path("sort")));fields.put("remark",reference?null:value(canonical.path("remark")));
            // Every retained original role must be active. Do not enable a NULL/unknown status.
            Object rawStatus=value(alias.path("rawStatus"));
            if(!Objects.equals("1",Objects.toString(rawStatus,null)))throw new IllegalArgumentException("原角色状态为空或未启用，不能由归并默认启用");
            fields.put("status",0);
            for (String kind:List.of("creator","updater")) {
                fields.put(kind+"Name",reference?null:value(canonical.path(kind+"Account")));
                fields.put(kind+"SourceKey",null);
            }
            fields.put("createTime",reference?null:value(canonical.path("createTime")));
            fields.put("updateTime",reference?null:value(canonical.path("updateTime")));
            fields.put("_foundationSourceAlias",alias.toString());fields.put("_foundationCanonicalFields",canonical.toString());
            result.add(new Alias(object,fields));
        }
        return result;
    }
    private static String hex(String value) {return HexFormat.of().withUpperCase().formatHex(value.getBytes(StandardCharsets.UTF_8));}
    private static JsonNode tree(Object raw) {return raw==null?tools.jackson.databind.node.NullNode.getInstance():raw instanceof JsonNode node?node:
            JsonUtils.parseTree(raw instanceof String text?text:JsonUtils.toJsonString(raw));}
    private static Object value(JsonNode node) {return node.isMissingNode()||node.isNull()?null:node.isObject()||node.isArray()?node.toString():node.asText();}
    private static Map<String,Object> fields(JsonNode node) {
        Map<String,Object> result=new LinkedHashMap<>();node.properties().forEach(entry->result.put(entry.getKey(),value(entry.getValue())));return result;
    }
}
