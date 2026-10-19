package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.util.*;
public final class DppmsSecureLicenseSyncTemplate {
    static final String ADAPTER="DPPMS_SECURE_LICENSE",OBJECT="LICENSE_HISTORY",TABLE="pm_project_license_info_from_license";
    static final List<String> ORIGINAL=List.of("id","sn","specModel","contract","contractType","item","status");
    static final List<String> COLUMNS=List.of("id","sn","specModel","contract","contractType","item","status","ciphertext","keyId","codeMac");
    /** Frozen non-secret values of explicitly approved cache IDs; never accepts a plaintext license code. */
    public record Approved(String id,String sn,String specModel,String contract,String contractType,String item,String status) {}
    public record Request(Long connectionId,Long ownerProjectId,List<Approved> approvedLicenses) {}
    public static SyncDefinition create(Request request) {
        if(request==null || request.connectionId()==null || request.connectionId()<=0 || request.ownerProjectId()!=null && request.ownerProjectId()<=0
                || request.approvedLicenses()==null || request.approvedLicenses().size()>200)throw new IllegalArgumentException("许可受控范围无效");
        var keys=new HashSet<String>();
        for(var source:request.approvedLicenses())if(source==null || source.id()==null || !source.id().matches("[1-9][0-9]*") || !keys.add(source.id()))throw new IllegalArgumentException("许可批准原身份无效或重复");
        var selected=request.approvedLicenses().stream().sorted(Comparator.comparing(a->new java.math.BigInteger(a.id()))).toList();
        String originals=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(selected);
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(TABLE).readMode("SECURE_LICENSE").sourceKey("id")
            .columns(List.of()).filters(List.of()).parameters(Map.of("approvedLicenseIds",String.join(",",selected.stream().map(Approved::id).toList()),
                "approvedLicenseFacts",originals,"ownerProjectId",Objects.toString(request.ownerProjectId(),"")))
            .mappings(COLUMNS.stream().map(n->SyncDefinition.Mapping.builder().source(n).target(n).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(request.connectionId()).mode("ONCE")
            .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
            .retryCount(0).retryIntervalSeconds(60).readStrategy("SNAPSHOT").restartPolicy("RESTART_ALL")
            .maxRows(200).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
    static List<Approved> approved(SyncDefinition.Source source) {
        String facts=Objects.toString(source.parameters()==null?null:source.parameters().get("approvedLicenseFacts"),"");
        try {
            var tree=JsonUtils.getObjectMapper().readTree(facts);
            if(tree==null || !tree.isArray() || tree.size()>200)throw new IllegalArgumentException();
            var result=new ArrayList<Approved>();
            for(var row:tree) {
                if(!row.isObject() || row.size()!=ORIGINAL.size() || ORIGINAL.stream().anyMatch(n->!row.has(n)))throw new IllegalArgumentException();
                for(String name:ORIGINAL)if(!row.path(name).isNull() && !row.path(name).isString())throw new IllegalArgumentException();
                result.add(new Approved(value(row,"id"),value(row,"sn"),value(row,"specModel"),value(row,"contract"),value(row,"contractType"),value(row,"item"),value(row,"status")));
            }
            return List.copyOf(result);
        } catch(RuntimeException failure){throw new IllegalArgumentException("LICENSE_APPROVAL_INVALID");}
    }
    private static String value(tools.jackson.databind.JsonNode row,String name){return row.path(name).isNull()?null:row.path(name).asText();}
    private DppmsSecureLicenseSyncTemplate() {}
}
