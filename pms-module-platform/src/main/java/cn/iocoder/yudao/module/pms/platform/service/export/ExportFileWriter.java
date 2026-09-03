package cn.iocoder.yudao.module.pms.platform.service.export;

public interface ExportFileWriter {

    WrittenExportFile write(Command command);

    void expire(Command command, WrittenExportFile file);

    record Command(Long tenantId, Long actorUserId, Long taskId, String operationId,
                   Long scopeVersion, byte[] content) {}

    record WrittenExportFile(Long artifactId, Integer versionNo, String referenceKey,
                             Integer artifactVersion, Integer referenceVersion, Integer availabilityVersion,
                             String sha256) {}
}
