package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsDirectAccountTemplateTest {
    @Test void recreatedTaskMapsAdjudicatedDepartmentWithoutChangingSourceEvidence() {
        var source=DppmsDirectAccountTemplate.create(1L).sources().stream()
                .filter(s->"t_user_info".equals(s.object())).findFirst().orElseThrow();
        var raw=raw(source,391);
        var row=map(source,raw);
        assertEquals(88,row.fields().get("depID"));
        assertEquals(391,raw.get("depID"));
    }
    @Test void otherApprovedDepartmentRetainsItsOriginalIdentity() {
        var source=DppmsDirectAccountTemplate.create(1L).sources().stream()
                .filter(s->"t_user_info".equals(s.object())).findFirst().orElseThrow();
        assertEquals(392,map(source,raw(source,392)).fields().get("depID"));
    }
    @Test void unknownDepartmentIsNotSilentlyAssignedToAParent() {
        var source=DppmsDirectAccountTemplate.create(1L).sources().stream()
                .filter(s->"t_user_info".equals(s.object())).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class,()->map(source,raw(source,999999)));
    }
    private static Map<String,Object> raw(SyncDefinition.Source source,int department) {
        var raw=new LinkedHashMap<String,Object>();
        source.mappings().forEach(mapping->raw.put(mapping.source(),null));
        raw.put(source.sourceKey(),"11");raw.put("depID",department);return raw;
    }
    private static cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row map(
            SyncDefinition.Source source,Map<String,Object> raw) {
        var definition=SyncDefinition.builder().adapter(DppmsDirectAccountTemplate.ADAPTER).sources(List.of(source)).build();
        var snapshot=new MysqlSyncReader.Snapshot(LocalDateTime.now(),List.of(
                new MysqlSyncReader.SourceRows(source.object(),source.sourceObject(),List.of(raw))),0);
        return new SyncFieldMapper().transform(definition,snapshot,List.of()).getFirst();
    }
}
