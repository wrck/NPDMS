package cn.iocoder.yudao.module.pms.asset.service.cutover;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.AssetCutoverResultApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.cutover.DeviceCutoverResultDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.cutover.DeviceCutoverResultMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.cutover.query.DeviceCutoverScopeQuery;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.storage.DeviceCutoverResultFileDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.storage.DeviceCutoverResultFileMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.storage.query.DeviceCutoverResultFileListQuery;
import cn.iocoder.yudao.module.pms.cutover.api.result.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 向资产服务记录追加 CUT 的真实成功和文件版本引用；不复制文件、不猜测设备软件版本。 */
@Service @RequiredArgsConstructor
public class AssetCutoverResultService implements AssetCutoverResultApi, CutoverCompletedConsumer {
    private final DeviceCutoverResultMapper records;
    private final CutoverResultApi results;
    private final DeviceCutoverResultFileMapper files;
    public String ownerContext() { return "AST"; }

    @Override @Transactional(rollbackFor = Exception.class)
    public void accept(CutoverCompleted event) {
        if (event == null || !Objects.equals(event.tenantId(), TenantContextHolder.getTenantId())
                || !"SUCCESS".equals(event.finalResult())) throw new IllegalArgumentException("割接资产事件身份无效");
        var result = results.lockAndInspect(new CutoverResultApi.ResultQuery(event.tenantId(), event.projectId(),
                event.taskId(), event.closureId(), event.closureRevision()));
        if (!Objects.equals(result.resultRef(), event.resultRef()) || !Objects.equals(result.tenantId(), event.tenantId())
                || !Objects.equals(result.projectId(), event.projectId()) || !Objects.equals(result.taskId(), event.taskId())
                || !Objects.equals(result.closureId(), event.closureId()) || !Objects.equals(result.closureVersion(), event.closureRevision()))
            throw new IllegalArgumentException("割接资产事件来源不一致");
        var expectedDevices = new HashSet<Long>();
        for (var device : result.devices()) if (!expectedDevices.add(device.deviceId())) throw conflict();
        if (expectedDevices.isEmpty() || result.collectionFiles().stream().anyMatch(file -> !expectedDevices.contains(file.deviceId()))
                || result.manualFiles().stream().anyMatch(file -> !expectedDevices.contains(file.deviceId()))) throw conflict();
        // 锁定设备后再读消费记录，串行化重复投递，避免部分设备重复消费。
        var current = new HashMap<Long, cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO>();
        for (var device : result.devices().stream().sorted(Comparator.comparing(CutoverResultApi.Device::deviceId)).toList())
            current.put(device.deviceId(), records.selectCurrentDeviceForUpdate(new DeviceCutoverScopeQuery(event.tenantId(), device.deviceId())));
        var previous = records.byTask(new Query(event.tenantId(), event.projectId(), event.taskId()));
        if (!previous.isEmpty()) {
            var actualDevices = new HashSet<Long>();
            for (var old : previous) if (!actualDevices.add(old.getDeviceId())) throw conflict();
            if (!actualDevices.equals(expectedDevices)) throw conflict();
            for (var old : previous) requireReplay(new DeviceResult(old, result));
            return;
        }
        for (var device : result.devices()) {
            var asset = current.get(device.deviceId());
            boolean unchanged = asset != null && Objects.equals(asset.getProjectId(), result.projectId())
                    && Objects.equals(asset.getProjectAssignmentVersion(), device.assignmentVersion())
                    && Objects.equals(asset.getSn(), device.serialNumber());
            var row = new DeviceCutoverResultDO(); row.setTenantId(event.tenantId()); row.setProjectId(result.projectId());
            row.setDeviceId(device.deviceId()); row.setSerialNumber(device.serialNumber()); row.setAssignmentVersion(device.assignmentVersion());
            row.setTaskId(result.taskId()); row.setClosureId(result.closureId()); row.setClosureVersion(result.closureVersion());
            row.setEventId(event.eventId()); row.setResultRef(result.resultRef()); row.setCutoverAt(result.archivedAt());
            row.setRefreshStatus(unchanged ? "REFRESHED" : "HISTORICAL_SCOPE_CHANGED");
            row.setTaskNo(result.taskNo()); row.setTaskName(result.taskName()); row.setSubmitUserId(result.submittedBy());
            row.setCreator("cutover-result"); row.setUpdater("cutover-result");
            if (records.insert(row) != 1) throw new IllegalStateException("割接资产服务记录保存失败");
            insertFiles(new DeviceResult(row, result));
        }
    }

    private record DeviceResult(DeviceCutoverResultDO row, CutoverResultApi.Result result) {}

    private void requireReplay(DeviceResult stored) {
        var row = stored.row(); var result = stored.result();
        var device = result.devices().stream().filter(value -> Objects.equals(value.deviceId(), row.getDeviceId())).findFirst().orElseThrow(AssetCutoverResultService::conflict);
        if (!Objects.equals(row.getTenantId(), result.tenantId()) || !Objects.equals(row.getProjectId(), result.projectId())
                || !Objects.equals(row.getTaskId(), result.taskId()) || !Objects.equals(row.getTaskNo(), result.taskNo())
                || !Objects.equals(row.getTaskName(), result.taskName()) || !Objects.equals(row.getClosureId(), result.closureId())
                || !Objects.equals(row.getClosureVersion(), result.closureVersion()) || !Objects.equals(row.getResultRef(), result.resultRef())
                || !Objects.equals(row.getSubmitUserId(), result.submittedBy()) || !Objects.equals(row.getCutoverAt(), result.archivedAt())
                || !Objects.equals(row.getSerialNumber(), device.serialNumber()) || !Objects.equals(row.getAssignmentVersion(), device.assignmentVersion())) throw conflict();
        var children = files.selectListByParents(DeviceCutoverResultFileListQuery.builder().tenantId(row.getTenantId())
                .deviceCutoverResultIds(Set.of(row.getId())).build());
        var collection = new ArrayList<CutoverResultApi.CollectionFile>();
        var manual = new ArrayList<CutoverResultApi.ManualFile>();
        for (var file : children) {
            if (!Objects.equals(file.getTenantId(), row.getTenantId()) || !Objects.equals(file.getDeviceCutoverResultId(), row.getId())) throw conflict();
            switch (file.getFileSourceCode()) {
                case "COLLECTION" -> collection.add(new CutoverResultApi.CollectionFile(row.getDeviceId(), file.getCollectionStageCode(),
                        file.getActorUserId(), file.getPlatformTaskId(), file.getResultVersion(), file.getFileVersionId()));
                case "MANUAL" -> manual.add(new CutoverResultApi.ManualFile(row.getDeviceId(), file.getCollectionStageCode(),
                        file.getFileArtifactId(), file.getFileVersionNo(), file.getFileReferenceKey(), file.getFileSha256()));
                default -> throw conflict();
            }
        }
        if (!collection.equals(result.collectionFiles().stream().filter(file -> Objects.equals(file.deviceId(), row.getDeviceId())).toList())
                || !manual.equals(result.manualFiles().stream().filter(file -> Objects.equals(file.deviceId(), row.getDeviceId())).toList())) throw conflict();
    }

    private void insertFiles(DeviceResult stored) {
        var row = stored.row(); var result = stored.result(); var time = LocalDateTime.now(); int order = 0;
        for (var source : result.collectionFiles()) {
            if (!Objects.equals(source.deviceId(), row.getDeviceId())) continue;
            var file = DeviceCutoverResultFileDO.builder().tenantId(row.getTenantId()).deviceCutoverResultId(row.getId())
                    .fileSourceCode("COLLECTION").collectionStageCode(source.stageCode()).actorUserId(source.actorId())
                    .platformTaskId(source.platformTaskId()).resultVersion(source.resultVersion()).fileVersionId(source.fileVersionId())
                    .sortOrder(order++).createUserId(result.submittedBy()).createTime(time).build();
            if (files.insert(file) != 1) throw new IllegalStateException("割接资产采集文件引用保存失败");
        }
        for (var source : result.manualFiles()) {
            if (!Objects.equals(source.deviceId(), row.getDeviceId())) continue;
            var file = DeviceCutoverResultFileDO.builder().tenantId(row.getTenantId()).deviceCutoverResultId(row.getId())
                    .fileSourceCode("MANUAL").collectionStageCode(source.stageCode()).fileArtifactId(source.artifactId())
                    .fileVersionNo(source.versionNo()).fileReferenceKey(source.referenceKey()).fileSha256(source.sha256())
                    .sortOrder(order++).createUserId(result.submittedBy()).createTime(time).build();
            if (files.insert(file) != 1) throw new IllegalStateException("割接资产人工文件引用保存失败");
        }
    }

    private static IllegalArgumentException conflict() {
        return new IllegalArgumentException("重复割接资产事件不能替换原成功事实");
    }
    @Override
    public List<Receipt> byTask(Query query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.projectId() == null || query.taskId() == null) throw new SecurityException("资产割接记录范围无效");
        return records.byTask(query).stream().map(row -> new Receipt(row.getDeviceId(), row.getSerialNumber(), row.getTaskId(),
                row.getClosureId(), row.getClosureVersion(), row.getRefreshStatus(), row.getCutoverAt(), row.getCreateTime())).toList();
    }
}
