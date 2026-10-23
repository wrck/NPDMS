package cn.iocoder.yudao.module.pms.cutover.api.result;

import java.time.LocalDateTime;
import java.util.List;

/** CUT Owner 的已归档成功事实；内部消费者必须以自身当前范围判断覆盖，不得以条数代替覆盖。 */
public interface CutoverResultApi {
    List<Result> successes(ProjectQuery query);
    Result lockAndInspect(ResultQuery query);
    record ProjectQuery(Long tenantId, Long projectId) { }
    record ResultQuery(Long tenantId, Long projectId, Long taskId, Long closureId, Long closureVersion) { }
    record Device(Long deviceId, String serialNumber, Long assignmentVersion) { }
    record CollectionFile(Long deviceId, String stageCode, Long actorId, String platformTaskId,
                          Long resultVersion, Long fileVersionId) { }
    record ManualFile(Long deviceId, String stageCode, Long artifactId, Integer versionNo,
                      String referenceKey, String sha256) { }
    record Result(Long tenantId, Long projectId, Long taskId, String taskNo, String taskName,
                  Long closureId, Long closureVersion, String resultRef, Long submittedBy,
                  LocalDateTime archivedAt, List<Device> devices, List<CollectionFile> collectionFiles,
                  List<ManualFile> manualFiles) {
        public Result { devices = List.copyOf(devices); collectionFiles = List.copyOf(collectionFiles); manualFiles = List.copyOf(manualFiles); }
    }
}
