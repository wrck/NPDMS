package cn.iocoder.yudao.module.pms.asset.api.device;

import java.util.List;

/** Manual log import owned by AST; importing files never completes configuration business. */
public interface DeviceConfigurationLogImportApi {
    Result importLog(Command command);

    void validateFile(FileQuery query);

    record Command(Long tenantId, Long actorId, Long projectId, String operationId,
                   String fileName, byte[] content) {}

    record Result(Long importId, String outcome, String rejectionReason, List<File> files) {}

    record File(Long fileId, Long deviceId, String serialNumber, boolean available) {}

    record FileQuery(Long tenantId, Long actorId, Long projectId, Long deviceId, Long fileId) {}
}
