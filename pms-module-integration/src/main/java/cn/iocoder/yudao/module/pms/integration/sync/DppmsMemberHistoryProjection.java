package cn.iocoder.yudao.module.pms.integration.sync;
import java.util.*;
final class DppmsMemberHistoryProjection {
    static boolean supports(SyncSourceProjection.Input input) {
        return (DppmsMemberHistorySyncTemplate.ADAPTER.equals(input.definition().adapter()) || DppmsDirectMemberHistoryTemplate.ADAPTER.equals(input.definition().adapter())) && DppmsMemberHistorySyncTemplate.PROJECTION.equals(input.source().rowProjection());
    }
    static List<MysqlSyncReader.SourceRows> expand(SyncSourceProjection.Input input) {
        var ordinary=new ArrayList<Map<String,Object>>();var contacts=new ArrayList<Map<String,Object>>();var keys=new HashSet<String>();
        for(var row:input.rows()) {
            if(row.get("id")==null || !keys.add(row.get("id").toString()) || !row.containsKey("originalProjectType"))throw new IllegalArgumentException("成员来源原身份缺失或重复");
            ("60".equals(Objects.toString(row.get("memberRole"),null))?contacts:ordinary).add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
        }
        return List.of(new MysqlSyncReader.SourceRows(DppmsMemberHistorySyncTemplate.MEMBERS,"pm_project_member",List.copyOf(ordinary)),
            new MysqlSyncReader.SourceRows(DppmsMemberHistorySyncTemplate.CONTACTS,"pm_project_member",List.copyOf(contacts)));
    }
    private DppmsMemberHistoryProjection() {}
}
