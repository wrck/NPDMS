package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsInstallationStreamTest {
    final LocalDateTime upper=LocalDateTime.of(2026,10,15,12,0);
    SpringJdbcStreamingReader.StreamChunk chunk(int sequence,Map<String,Object> row) {
        return new SpringJdbcStreamingReader.StreamChunk(upper,0,DppmsInstallationSyncAdapter.OBJECT,"pm_project_shipment",List.of(row),row.get("id"),100,false,sequence);
    }
    SpringJdbcStreamingReader.StreamChunk complete() {
        return new SpringJdbcStreamingReader.StreamChunk(upper,0,DppmsInstallationSyncAdapter.OBJECT,"pm_project_shipment",List.of(),null,0,true,4);
    }
    @Test void sameDeviceAcrossSourceChunksIsDeliveredTogetherWithEveryOriginalRow() throws Exception {
        var output=new ArrayList<SpringJdbcStreamingReader.StreamChunk>();
        try(var stream=new DppmsInstallationStream(DppmsInstallationSyncTemplate.create(1L))) {
            stream.capture(chunk(1,Map.of("id",1,"barcode","SN","createTime","2020-01-01")));
            stream.capture(chunk(2,Map.of("id",2,"barcode","OTHER","createTime","2020-01-03")));
            stream.capture(chunk(3,Map.of("id",3,"barcode","SN","createTime","2020-01-05")));
            stream.capture(chunk(4,Map.of("id",4,"barcode","SN","createTime","2020-01-02")));
            assertThrows(IllegalArgumentException.class,()->stream.replay(output::add));assertTrue(output.isEmpty());
            stream.capture(complete());stream.replay(output::add);
        }
        assertTrue(output.getLast().sourceComplete());assertEquals(3,output.size());
        var same=output.stream().filter(c->c.rows().size()==3).findFirst().orElseThrow();
        assertEquals(List.of(1,3,4),same.rows().stream().map(r->r.get("id")).toList());
        assertEquals(4,output.stream().mapToInt(c->c.rows().size()).sum());
    }
    @Test void localStagingRetainsExplicitNullsAndHistoricalTimestampPrecision() throws Exception {
        var row=new LinkedHashMap<String,Object>();row.put("id",1);row.put("barcode","SN");row.put("updateBy",null);
        var created=LocalDateTime.of(2020,1,2,3,4,5,123000000);row.put("createTime",created);
        var output=new ArrayList<SpringJdbcStreamingReader.StreamChunk>();
        try(var stream=new DppmsInstallationStream(DppmsInstallationSyncTemplate.create(1L))) {
            stream.capture(chunk(1,row));stream.capture(complete());stream.replay(output::add);
        }
        var restored=output.getFirst().rows().getFirst();assertTrue(restored.containsKey("updateBy"));assertNull(restored.get("updateBy"));
        assertEquals(created.toString(),restored.get("createTime"));
    }
    @Test void byteBudgetIncompleteSourceAndCheckpointModeCannotYieldPartialDeviceHistory() throws Exception {
        var definition=DppmsInstallationSyncTemplate.create(1L).toBuilder().maxBytes(10).build();
        var output=new ArrayList<SpringJdbcStreamingReader.StreamChunk>();
        try(var stream=new DppmsInstallationStream(definition)) {
            stream.capture(chunk(1,Map.of("id",1,"barcode","SN")));stream.capture(complete());
            assertThrows(IllegalArgumentException.class,()->stream.replay(output::add));assertTrue(output.isEmpty());
        }
        assertThrows(IllegalArgumentException.class,()->new DppmsInstallationStream(definition.toBuilder().restartPolicy("CHECKPOINT_KEY").build()));
        var source=definition.sources().getFirst();source.updatedAt("createTime");
        assertThrows(IllegalArgumentException.class,()->new DppmsInstallationStream(definition));
    }
}
