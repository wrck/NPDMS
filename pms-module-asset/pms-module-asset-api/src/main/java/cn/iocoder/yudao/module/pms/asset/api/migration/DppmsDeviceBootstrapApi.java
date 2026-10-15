package cn.iocoder.yudao.module.pms.asset.api.migration;

import java.time.LocalDateTime;
import java.util.List;

/** Separate base archive creation from explicitly guarded project assignment. */
public interface DppmsDeviceBootstrapApi {
    List<Seed> snapshot(SnapshotQuery query);
    List<BaseResult> previewBase(BaseCommand command);
    List<BaseResult> applyBase(BaseCommand command);
    AssignmentResult previewAssignment(Assignment command);
    AssignmentResult applyAssignment(Assignment command);
    record SnapshotQuery(Long tenantId,Long actorId,List<String> deviceSns,long maxBytes) {}
    record Seed(String deviceSn,Long shipmentId,String shipmentSourceKey,String contractNo,String packageNo,
                LocalDateTime shipmentTime,String productCode,String productModel,String productName) {}
    record BaseCommand(Long tenantId,Long actorId,String sourceVersion,LocalDateTime sourceReadAt,List<Seed> seeds) {}
    record BaseResult(String deviceSn,Long deviceId,String action,Long shipmentId,Long assignmentVersion) {}
    record Assignment(Long tenantId,Long actorId,Long projectId,Long deviceId,String contractNo,
                      Long expectedShipmentId,Long expectedAssignmentVersion,String sourceKey,LocalDateTime effectiveAt) {}
    record AssignmentResult(Long deviceId,Long projectId,String action,Long assignmentVersion) {}
}
