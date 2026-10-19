package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/** Read the source once in primary-key order; keep each device's complete history in one Owner transaction. */
final class DppmsInstallationStream implements AutoCloseable {
    private final Path directory;
    private final long maxBytes;
    private final SyncDefinition.Source source;
    private final Map<Integer,BufferedWriter> writers=new HashMap<>();
    private LocalDateTime upper;
    private boolean complete;
    DppmsInstallationStream(SyncDefinition definition) throws IOException {
        validate(definition);
        source=definition.sources().getFirst();maxBytes=definition.maxBytes();
        directory=Files.createTempDirectory("npdms-installation-source-");
    }
    static void validate(SyncDefinition definition) {
        if(!DppmsInstallationSyncAdapter.ADAPTER.equals(definition.adapter()) && !DppmsDirectInstallationTask.ADAPTER.equals(definition.adapter()))return;
        if(!"DPPMS".equals(definition.sourceSystem()) || !"ONCE".equals(definition.mode())
                || !"STREAMING_CURSOR".equals(definition.effectiveReadStrategy()) || !"RESTART_ALL".equals(definition.effectiveRestartPolicy())
                || definition.sources()==null || definition.sources().size()!=1)fail("FULL_SOURCE_REQUIRED");
        var source=definition.sources().getFirst();
        if(!"TABLE".equals(source.readMode()) || !"pm_project_shipment".equals(source.table())
                || !"pm_project_shipment".equals(source.sourceObject()) || !"id".equals(source.sourceKey())
                || !"ID".equals(source.publishedProjectMatch()) || source.queryRef()!=null || source.groupKey()!=null
                || source.rowProjection()!=null || source.syncPrimaryKey() || source.updatedAt()!=null
                || source.filters()!=null&&!source.filters().isEmpty()
                || source.sourceKeyColumns()!=null&&!source.sourceKeyColumns().isEmpty())fail("RAW_PRIMARY_KEY_SOURCE_REQUIRED");
        var required=new HashSet<>(cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS);
        if(source.columns()==null || !source.columns().equals(cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS))fail("RAW_COLUMNS_REQUIRED");
        required.add("originalProjectCode");required.add("projectType");
        if(source.mappings()==null || source.mappings().size()!=required.size())fail("RAW_MAPPING_REQUIRED");
        for(var mapping:source.mappings())if(!required.remove(mapping.target()) || !Objects.equals(mapping.source(),mapping.target())
                || !"DIRECT".equals(mapping.conversion()) || mapping.defaultValue()!=null || mapping.constant()!=null
                || mapping.values()!=null&&!mapping.values().isEmpty())fail("RAW_MAPPING_REQUIRED");
    }
    void capture(SpringJdbcStreamingReader.StreamChunk chunk) {
        if(complete || chunk.sourceIndex()!=0)fail("SOURCE_SEQUENCE_INVALID");
        if(upper!=null&&!upper.equals(chunk.upper()))fail("SOURCE_SNAPSHOT_CHANGED");
        upper=chunk.upper();
        if(chunk.sourceComplete()){complete=true;return;}
        try {
            for(var row:chunk.rows()) {
                var serial=serial(row);int partition=Math.floorMod(serial.hashCode(),256);
                var writer=writers.get(partition);
                if(writer==null) {writer=Files.newBufferedWriter(directory.resolve(partition+".jsonl"),StandardCharsets.UTF_8);writers.put(partition,writer);}
                String preserved=row.entrySet().stream().map(entry->{
                    Object value=entry.getValue() instanceof LocalDateTime date?date.toString():entry.getValue();
                    return JsonUtils.toJsonString(entry.getKey())+":"+(value==null?"null":JsonUtils.toJsonString(value));
                }).collect(java.util.stream.Collectors.joining(",","{","}"));
                writer.write(preserved);writer.newLine();
            }
        } catch(IOException error){throw new UncheckedIOException(error);}
    }
    void replay(Consumer<SpringJdbcStreamingReader.StreamChunk> consumer) throws IOException {
        if(!complete)fail("SOURCE_INCOMPLETE");
        closeWriters();int sequence=0;
        // No source SQL sort, business-table staging writes, or process-wide preview state.
        for(int partition=0;partition<256;partition++) {
            var file=directory.resolve(partition+".jsonl");if(!Files.exists(file))continue;
            var groups=new LinkedHashMap<String,List<Map<String,Object>>>();long bytes=0;
            try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
                String line;
                while((line=reader.readLine())!=null) {
                    bytes+=line.getBytes(StandardCharsets.UTF_8).length;
                    if(bytes>maxBytes)fail("LOCAL_PARTITION_BYTE_LIMIT");
                    var row=JsonUtils.parseMap(line);
                    groups.computeIfAbsent(serial(row),ignored->new ArrayList<>()).add(row);
                }
            }
            for(var rows:groups.values()) {
                long size=rows.stream().mapToLong(r->JsonUtils.toJsonString(r).getBytes(StandardCharsets.UTF_8).length).sum();
                consumer.accept(new SpringJdbcStreamingReader.StreamChunk(upper,0,source.object(),source.sourceObject(),
                        List.copyOf(rows),rows.getLast().get("id"),size,false,++sequence));
            }
        }
        consumer.accept(new SpringJdbcStreamingReader.StreamChunk(upper,0,source.object(),source.sourceObject(),List.of(),null,0,true,sequence));
    }
    private static String serial(Map<String,Object> row) {
        var value=row.get("barcode");if(value==null || value.toString().isBlank())fail("DEVICE_SN_MISSING");return value.toString();
    }
    private void closeWriters() throws IOException {
        IOException failure=null;
        for(var writer:writers.values())try{writer.close();}catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        writers.clear();if(failure!=null)throw failure;
    }
    @Override public void close() throws IOException {
        try {closeWriters();} finally {
            try(var files=Files.list(directory)){for(var file:files.toList())Files.deleteIfExists(file);}
            Files.deleteIfExists(directory);
        }
    }
    private static void fail(String code){throw new IllegalArgumentException("DPPMS_INSTALLATION_"+code);}
}
