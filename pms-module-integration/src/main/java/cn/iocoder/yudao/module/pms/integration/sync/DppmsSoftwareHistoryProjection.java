package cn.iocoder.yudao.module.pms.integration.sync;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSoftwareHistorySyncTemplate.*;
final class DppmsSoftwareHistoryProjection {
    record Input(List<Map<String,Object>> groups,List<MysqlSyncReader.SourceRows> objects) {}
    static List<MysqlSyncReader.SourceRows> enrich(Input input) {
        var scopes=new HashMap<String,Map<String,Object>>();
        for(var group:input.groups())scopes.put(Objects.toString(group.get("_groupKey"),null),group);
        var parents=new HashMap<String,String>();
        for(var object:input.objects())if(BATCH.equals(object.object()))for(var row:object.rows())parents.put(Objects.toString(row.get("id"),null),Objects.toString(row.get("projectId"),null));
        return input.objects().stream().map(object->new MysqlSyncReader.SourceRows(object.object(),object.sourceObject(),object.rows().stream().map(row->{
            if(VERSION.equals(object.object()) && !Objects.equals(parents.get(Objects.toString(row.get("logId"),null)),Objects.toString(row.get("projectId"),null)))
                throw new IllegalArgumentException("DPPMS_SOFTWARE_PARENT_PROJECT_CONFLICT");
            var group=scopes.get(Objects.toString(row.get("_groupKey"),null));
            if(group==null || group.get("projectType")==null || group.get("originalProjectCode")==null
                    || !Objects.equals(Objects.toString(group.get("projectId"),null),Objects.toString(row.get("projectId"),null)))
                throw new IllegalArgumentException("DPPMS_SOFTWARE_SCOPE_CHANGED");
            var enriched=new LinkedHashMap<>(row);enriched.put("projectType",group.get("projectType"));enriched.put("originalProjectCode",group.get("originalProjectCode"));
            return Collections.unmodifiableMap(enriched);
        }).toList())).toList();
    }
    private DppmsSoftwareHistoryProjection() {}
}
