package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncRunDO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StreamingResultSampleTest {
    private DataSyncAdapter.Change change(String key,String action) {
        return new DataSyncAdapter.Change("ITEM",key,null,action,Map.of(),Map.of(),key);
    }
    private SyncRunService.StreamingResultSample sample(boolean preview,List<DataSyncAdapter.Change> previous) {
        return new SyncRunService.StreamingResultSample(new SyncRunDO().setPreview(preview).setResultJson(JsonUtils.toJsonString(previous)),3);
    }
    @Test void cappedOrdinaryAndAllProblemSamplesNeedNoJsonRewrite() {
        var ordinary=List.of(change("1","INSERT"),change("2","INSERT"),change("3","INSERT"));
        assertNull(sample(true,ordinary).prepare(List.of(change("4","INSERT"))));
        assertNull(sample(false,ordinary).prepare(List.of(change("4","ISSUE"))));
        var problems=List.of(change("1","ISSUE"),change("2","CONFLICT"),change("3","FAILED"));
        assertNull(sample(true,problems).prepare(List.of(change("4","ISSUE"))));
    }
    @Test void problemsReplaceFromTheEndPreservingExistingProblemPositionsAndOrder() {
        var sample=sample(true,List.of(change("1","INSERT"),change("2","ISSUE"),change("3","INSERT")));
        var update=sample.prepare(List.of(change("4","CONFLICT"),change("5","FAILED"),change("6","ISSUE")));
        assertEquals(List.of("5","2","4"),update.rows().stream().map(DataSyncAdapter.Change::sourceKey).toList());
        sample.committed(update);assertNull(sample.prepare(List.of(change("7","ISSUE"))));
    }
    @Test void uncommittedPreparationDoesNotAlterRetryAndExistingJsonResumesSampling() {
        var sample=sample(true,List.of(change("1","INSERT")));
        var failed=sample.prepare(List.of(change("2","INSERT")));
        assertEquals(failed,sample.prepare(List.of(change("2","INSERT"))));
        var retry=sample.prepare(List.of(change("3","INSERT")));
        assertEquals(List.of("1","3"),retry.rows().stream().map(DataSyncAdapter.Change::sourceKey).toList());
        sample.committed(retry);
        var restored=new SyncRunService.StreamingResultSample(new SyncRunDO().setPreview(true).setResultJson(retry.json()),3);
        assertEquals(List.of("1","3","4"),restored.prepare(List.of(change("4","INSERT"))).rows().stream().map(DataSyncAdapter.Change::sourceKey).toList());
    }
    @Test void persistedSampleIsDetachedFromCallerOwnedNestedMaps() {
        var nested=new LinkedHashMap<String,Object>();nested.put("value","original");
        var after=new LinkedHashMap<String,Object>();after.put("nested",nested);
        var row=new DataSyncAdapter.Change("ITEM","1",null,"INSERT",Map.of(),after,"sample");
        var sample=sample(true,List.of());var update=sample.prepare(List.of(row));
        nested.put("value","changed-before-commit");sample.committed(update);nested.put("value","changed-after-commit");
        var appended=sample.prepare(List.of(change("2","INSERT")));
        assertEquals("original",((Map<?,?>)appended.rows().getFirst().after().get("nested")).get("value"));
        assertEquals(JsonUtils.parseTree(appended.json()),JsonUtils.parseTree(JsonUtils.toJsonString(appended.rows())));
    }
}
