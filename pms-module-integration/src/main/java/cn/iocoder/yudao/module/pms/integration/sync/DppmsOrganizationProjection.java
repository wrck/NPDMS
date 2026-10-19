package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The reviewed SQL chooses one whole definition; original aliases remain separately traceable. */
final class DppmsOrganizationProjection {
    static final String PROJECTION="organizationCanonical";
    static final List<String> METADATA=List.of("_organizationWinnerObject","_organizationWinnerKey","_organizationBusinessCode","_organizationCompanyKey","_organizationParentKey");
    private DppmsOrganizationProjection() {}
    static boolean supports(SyncSourceProjection.Input input) {
        return "DPPMS_ORGANIZATION".equals(input.definition().adapter()) && PROJECTION.equals(input.source().rowProjection());
    }
    static List<MysqlSyncReader.SourceRows> expand(SyncSourceProjection.Input input) {
        Map<String,SyncDefinition.Source> sources=new LinkedHashMap<>();
        Map<String,List<Map<String,Object>>> rows=new LinkedHashMap<>();
        Map<String,Set<String>> keys=new HashMap<>();
        for(var source:input.definition().sources())if(Objects.equals(source.object(),input.source().object())
                || Objects.equals(source.queryRef(),input.source().object())) {
            sources.put(source.object(),source);rows.put(source.object(),new ArrayList<>());keys.put(source.object(),new HashSet<>());
        }
        Set<String> businessKeys=new HashSet<>();
        Map<String,String> companyAliases=new HashMap<>(),departmentAliases=new HashMap<>();
        for(var group:input.rows()) {
            String type=Objects.toString(group.get("objectName"),null);
            var canonical=tree(group.get("canonicalFields"));var aliases=tree(group.get("sourceAliases"));
            String code=text(canonical,"code");
            if(!Set.of("COMPANY","DEPARTMENT").contains(Objects.toString(type,"")) || !canonical.isObject() || !aliases.isArray()
                    || code==null || !Objects.equals(hex(code),group.get("businessKey")) || !businessKeys.add(type+":"+code))
                throw new IllegalArgumentException("组织归并身份或完整主定义缺失/重复");
            String winnerId=text(canonical,"sourceId"),table=text(canonical,"sourceTable");
            List<Decoded> decoded=new ArrayList<>();int rank=Integer.MAX_VALUE;Decoded winner=null;
            for(var alias:aliases) {
                if(!alias.path("rawFields").isObject())throw new IllegalArgumentException("组织别名没有完整原字段");
                String original=text(alias,"table"),id=text(alias,"id"),object;int sourceRank;
                if("COMPANY".equals(type) && "ehr_company".equals(original)) {object="COMPANY";sourceRank=0;}
                else if("DEPARTMENT".equals(type) && "ehr_department".equals(original)) {
                    object=switch(Objects.toString(text(alias,"component"),"")) {
                        case "EHR_BASE" -> "DEPARTMENT";case "EHR_COMPLETION" -> "ehr_department";
                        default -> throw new IllegalArgumentException("组织EHR别名分支未知");};
                    sourceRank="DEPARTMENT".equals(object)?0:1;
                } else if("DEPARTMENT".equals(type) && "fnd_department".equals(original)) {object="fnd_department";sourceRank=2;}
                else throw new IllegalArgumentException("组织别名来源未获准");
                Map<String,Object> fields=fields(alias.path("rawFields"));
                String primary="COMPANY".equals(object)?"compID":"fnd_department".equals(object)?"id":"depID";
                String rawCode=Objects.toString(fields.get("COMPANY".equals(object)?"compCode":"fnd_department".equals(object)?"departmentNum":"depCode"),null);
                if(!Objects.equals(id,Objects.toString(fields.get(primary),null)) || rawCode==null
                        || !Objects.equals(code,"COMPANY".equals(object)?rawCode.trim():rawCode))
                    throw new IllegalArgumentException("组织原身份、原业务编码与归并身份不同");
                if("fnd_department".equals(object)) {
                    if(!"163130".equals(rawCode))throw new IllegalArgumentException("未批准的FND补建部门");
                    DppmsDepartmentSyncTemplate.REFERENCES.forEach(field->fields.put(field,null));
                }
                if("ehr_department".equals(object)) {
                    if(!DppmsEhrDepartmentCompletionTemplate.CODES.contains(rawCode))throw new IllegalArgumentException("未批准的EHR补建部门");
                    var fnd=alias.path("fndRawFields");
                    if(!Objects.equals(text(alias,"fndId"),text(fnd,"id")) || text(fnd,"id")!=null && !Objects.equals(rawCode,text(fnd,"departmentNum")))
                        throw new IllegalArgumentException("组织补齐FND参考不是原部门的精确业务编码来源");
                    for(String column:DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS)fields.put("migrationFnd_"+column,value(fnd.path(column)));
                    DppmsEhrDepartmentCompletionTemplate.REFERENCES.forEach(field->fields.put(field,null));
                    // Parent is resolved by code, never guessed from the numeric source ID.
                    fields.put("migrationParentCode",value(canonical.path("parentCode")));
                }
                var item=new Decoded(object,id,fields,sourceRank);decoded.add(item);
                if(sourceRank<rank){rank=sourceRank;winner=item;}
            }
            if(winner==null || !Objects.equals(table,"COMPANY".equals(winner.object())?"ehr_company":
                    "fnd_department".equals(winner.object())?"fnd_department":"ehr_department"))
                throw new IllegalArgumentException("组织主定义未遵循批准的完整来源优先级");
            // Equal-ranked aliases may be present; SQL's whole-field MIN chooses their precise source ID.
            Decoded chosen=null;
            for(var alias:decoded)if(alias.rank()==rank && Objects.equals(alias.id(),winnerId)) {
                if(chosen!=null)throw new IllegalArgumentException("组织主来源身份重复");chosen=alias;
            }
            if(chosen==null)throw new IllegalArgumentException("组织主定义没有对应原行");
            for(var alias:decoded) {
                var source=sources.get(alias.object());
                if(source==null || alias.id()==null || alias.id().isBlank() || alias.id().length()>128 || !keys.get(alias.object()).add(alias.id()))
                    throw new IllegalArgumentException("组织来源对象未声明或原来源身份重复");
                alias.fields().put(METADATA.get(0),chosen.object());alias.fields().put(METADATA.get(1),chosen.id());alias.fields().put(METADATA.get(2),code);
                alias.fields().put("_organizationCompanyKey",null);alias.fields().put("_organizationParentKey",null);
                rows.get(alias.object()).add(alias.fields());
                if("COMPANY".equals(alias.object()))companyAliases.put(alias.id(),chosen.id());
                if("DEPARTMENT".equals(alias.object()))departmentAliases.put(alias.id(),chosen.id());
            }
            if(input.definition().maxRows()>0 && rows.values().stream().mapToInt(List::size).sum()>input.definition().maxRows())
                throw new IllegalArgumentException("组织归并原行超过整批容量限制");
        }
        // Only equivalent original aliases are rewritten; absent parent/company references still fail in the original Owner.
        for(var row:rows.getOrDefault("DEPARTMENT",List.of())) {
            Object company=row.get("compID"),parent=row.get("adminID");
            row.put("_organizationCompanyKey",company==null?null:companyAliases.getOrDefault(company.toString(),company.toString()));
            row.put("_organizationParentKey",parent==null?null:departmentAliases.getOrDefault(parent.toString(),parent.toString()));
        }
        return sources.values().stream().map(source->new MysqlSyncReader.SourceRows(source.object(),source.sourceObject(),
                rows.get(source.object()).stream().map(Collections::unmodifiableMap).toList())).toList();
    }
    private record Decoded(String object,String id,Map<String,Object> fields,int rank) {}
    private static String hex(String value){return HexFormat.of().withUpperCase().formatHex(value.getBytes(StandardCharsets.UTF_8));}
    private static JsonNode tree(Object raw){return raw==null?tools.jackson.databind.node.NullNode.getInstance():
            JsonUtils.parseTree(raw instanceof String text?text:JsonUtils.toJsonString(raw));}
    private static String text(JsonNode node,String name){return node.path(name).isNull()||node.path(name).isMissingNode()?null:node.path(name).asText();}
    private static Object value(JsonNode node){return node.isNull()||node.isMissingNode()?null:node.isObject()||node.isArray()?node.toString():node.asText();}
    private static Map<String,Object> fields(JsonNode node){var result=new LinkedHashMap<String,Object>();node.properties().forEach(entry->result.put(entry.getKey(),value(entry.getValue())));return result;}
}
