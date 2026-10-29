package cn.iocoder.yudao.module.pms.integration.api.umc;

import java.util.List;

/** UMC transport capability. The business Owner authorizes and redacts inputs before calling.
 * Completion means DOC and source JSON were obtained; it does not complete a business task. */
public interface UmcInspectionApi {
    Result inspect(Request request);

    record Scope(Long tenantId, String taskKey, String attemptKey) { }
    record OfflineLog(String fileName, byte[] content) {
        public OfflineLog { content = content == null ? null : content.clone(); }
        @Override public byte[] content() { return content == null ? null : content.clone(); }
        public int sizeBytes() { return content == null ? 0 : content.length; }
    }
    /** Persist this checkpoint in the caller's operation before retrying an unknown outcome. */
    record Checkpoint(String inputDigest, String taskId, String reportId, String uploadState) { }
    record Request(Scope scope, String requestId, List<OfflineLog> logs, Checkpoint checkpoint, boolean queryOnly) {
        public Request { logs = logs == null ? null : List.copyOf(logs); }
        public Request(Scope scope, String requestId, List<OfflineLog> logs, Checkpoint checkpoint) {
            this(scope, requestId, logs, checkpoint, false);
        }
    }
    record Result(Checkpoint checkpoint, String fileName, String mediaType, String docSha256,
                  byte[] doc, String detailSha256, byte[] rawDetail, Report detail, List<String> warnings) {
        public Result { doc = doc.clone(); rawDetail = rawDetail.clone(); warnings = warnings == null ? List.of() : List.copyOf(warnings); }
        @Override public byte[] doc() { return doc.clone(); }
        @Override public byte[] rawDetail() { return rawDetail.clone(); }
    }

    // Keep UMC IDs as strings and its independent support/error/result facts unchanged.
    record Report(String id, String taskid, String name, Long day, Long timeStart, Long timeEnd,
                  String info, Boolean doc, Boolean pdf, Integer level1, Integer level2, Integer level3,
                  List<Device> devlist) { }
    record Device(String id, String reportid, String deviceid, String deviceip, String devicelabel,
                  String devtype, String devmodel, String version, String sn, Long uptime,
                  Long timeStart, Long timeEnd, String info, Integer level1, Integer level2,
                  Integer level3, List<Rule> rulelist) { }
    record Rule(String id, String reportid, String deviceid, String ruleid, String type, String name,
                Integer level, String cmd, Integer cmdtype, String output, String error, String info,
                String suggest, Boolean result, Boolean support, List<Target> targetlist) { }
    record Target(String id, String reportid, String deviceid, String ruleid, String name, Integer type,
                  String info, String error, Boolean result, List<Value> valuelist) { }
    record Value(String id, String targetid, String object, String value, Boolean result) { }

    final class Failure extends RuntimeException {
        private final String code;
        private final Checkpoint checkpoint;
        public Failure(String code, Checkpoint checkpoint) {
            super("UMC inspection failed: " + code);
            this.code = code; this.checkpoint = checkpoint;
        }
        public String code() { return code; }
        public Checkpoint checkpoint() { return checkpoint; }
    }
}
