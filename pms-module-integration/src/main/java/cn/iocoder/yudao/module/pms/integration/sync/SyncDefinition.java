package cn.iocoder.yudao.module.pms.integration.sync;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.List;
import java.util.Map;

/** Configuration aggregate passed as a single object, with builder-based evolution. */
@Data @Builder(toBuilder=true) @NoArgsConstructor @AllArgsConstructor(access=AccessLevel.PRIVATE)
@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY, getterVisibility=JsonAutoDetect.Visibility.NONE)
@Accessors(fluent=true)
public class SyncDefinition {
    private String adapter;
    private String sourceSystem;
    private Long connectionId;
    private String mode;
    @Builder.Default private String loadingMode = "UPSERT";
    private boolean clearBeforeLoad;
    private boolean resetMappingsBeforeLoad;
    private String missingPolicy;
    private String cron;
    private String fullCron;
    private int overlapSeconds;
    private int retryCount;
    private int retryIntervalSeconds;
    private int maxRows;
    /** Legacy compatibility flag. New configurations should use readStrategy=KEYSET_PAGING. */
    private boolean autoPaging;
    private long maxBytes;
    @Builder.Default private String readStrategy = "SNAPSHOT";
    @Builder.Default private int fetchSize = 2000;
    @Builder.Default private int chunkSize = 1000;
    @Builder.Default private String restartPolicy = "RESTART_ALL";
    /** 0 means use the JDBC driver's timeout/default and allows expensive analytical SQL to finish. */
    @Builder.Default private int queryTimeoutSeconds = 0;
    private List<Source> sources;

    public String effectiveReadStrategy() {
        if (autoPaging) return "KEYSET_PAGING";
        return readStrategy == null || readStrategy.isBlank() ? "SNAPSHOT" : readStrategy;
    }
    public String effectiveRestartPolicy() {
        return restartPolicy == null || restartPolicy.isBlank() ? "RESTART_ALL" : restartPolicy;
    }
    public int effectiveFetchSize() { return fetchSize > 0 ? fetchSize : 2000; }
    public int effectiveChunkSize() { return chunkSize > 0 ? chunkSize : 1000; }

    @Data @Builder(toBuilder=true) @NoArgsConstructor @AllArgsConstructor(access=AccessLevel.PRIVATE)
    @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY, getterVisibility=JsonAutoDetect.Visibility.NONE)
    @Accessors(fluent=true)
    public static class Source {
        private String object;
        private String sourceObject;
        private String readMode;
        private String table;
        private String sql;
        private Map<String,Object> parameters;
        private String sourceKey;
        private boolean syncPrimaryKey;
        private String updatedAt;
        private List<String> columns;
        private List<Filter> filters;
        private List<Mapping> mappings;
    }
    public record Filter(String column,String operator,Object value) {}
    @Data @Builder(toBuilder=true) @NoArgsConstructor @AllArgsConstructor(access=AccessLevel.PRIVATE)
    @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY, getterVisibility=JsonAutoDetect.Visibility.NONE)
    @Accessors(fluent=true)
    public static class Mapping {
        private String target;
        private String source;
        private String conversion;
        private Object constant;
        private Object defaultValue;
        private Map<String,Object> values;
    }
}
