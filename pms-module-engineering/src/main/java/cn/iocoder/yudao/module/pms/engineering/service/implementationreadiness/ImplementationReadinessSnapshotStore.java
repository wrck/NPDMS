package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.*;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessContextFact.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.implementationreadiness.ImplementationReadinessSnapshotDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** IMP 拥有完整的就绪来源，CUT 仅引用其不可变身份。 */
@Service
@RequiredArgsConstructor
public class ImplementationReadinessSnapshotStore {
    private final CutoverReadinessDeviceMapper devices;
    private final CutoverReadinessSourceMapper sources;
    private final CutoverReadinessSourceObjectMapper objects;
    private final CutoverReadinessSourceWatermarkMapper watermarks;
    private final CutoverReadinessUnmetMapper unmet;

    public record Write(ImplementationReadinessSnapshotDO snapshot, ImplementationReadinessContextFact context,
                        List<String> unmetCodes, Long actorId, LocalDateTime createTime) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Write command) {
        var root = command.snapshot();
        for (int i = 0; i < command.context().devices().size(); i++) {
            var value = command.context().devices().get(i);
            devices.insert(CutoverReadinessDeviceDO.builder().tenantId(root.getTenantId()).snapshotId(root.getId())
                    .deviceId(value.deviceId()).sn(value.serialNumber()).projectAssignmentVersion(value.projectAssignmentVersion())
                    .sortOrder(i).createUserId(command.actorId()).createTime(command.createTime()).build());
        }
        for (var value : command.context().sourceFacts()) {
            var source = CutoverReadinessSourceDO.builder().tenantId(root.getTenantId()).snapshotId(root.getId())
                    .sourceCode(value.sourceCode().name()).completionStatusCode(value.completionStatus().name())
                    .factVersion(value.factVersion()).reopenedFlag(value.reopened())
                    .createUserId(command.actorId()).createTime(command.createTime()).build();
            sources.insert(source);
            for (int i = 0; i < value.sourceObjectIds().size(); i++) {
                objects.insert(CutoverReadinessSourceObjectDO.builder().tenantId(root.getTenantId()).sourceFactId(source.getId())
                        .sourceObjectId(value.sourceObjectIds().get(i)).sortOrder(i)
                        .createUserId(command.actorId()).createTime(command.createTime()).build());
            }
            for (var mark : value.watermarkEntries()) {
                watermarks.insert(CutoverReadinessSourceWatermarkDO.builder().tenantId(root.getTenantId()).sourceFactId(source.getId())
                        .axisCode(mark.axisCode()).objectId(mark.objectId()).factVersion(mark.version())
                        .createUserId(command.actorId()).createTime(command.createTime()).build());
            }
        }
        for (int i = 0; i < command.unmetCodes().size(); i++) {
            unmet.insert(CutoverReadinessUnmetDO.builder().tenantId(root.getTenantId()).snapshotId(root.getId())
                    .unmetCode(command.unmetCodes().get(i)).sortOrder(i)
                    .createUserId(command.actorId()).createTime(command.createTime()).build());
        }
    }

    public ImplementationReadinessSnapshotFact read(ImplementationReadinessSnapshotDO root) {
        var deviceRows = devices.selectListByParents(CutoverReadinessDeviceListQuery.builder()
                .tenantId(root.getTenantId()).snapshotIds(Set.of(root.getId())).build());
        var sourceRows = sources.selectListByParents(CutoverReadinessSourceListQuery.builder()
                .tenantId(root.getTenantId()).snapshotIds(Set.of(root.getId())).build());
        var sourceIds = sourceRows.stream().map(CutoverReadinessSourceDO::getId).collect(Collectors.toSet());
        var objectRows = objects.selectListByParents(CutoverReadinessSourceObjectListQuery.builder()
                .tenantId(root.getTenantId()).sourceFactIds(sourceIds).build());
        var watermarkRows = watermarks.selectListByParents(CutoverReadinessSourceWatermarkListQuery.builder()
                .tenantId(root.getTenantId()).sourceFactIds(sourceIds).build());
        var unmetRows = unmet.selectListByParents(CutoverReadinessUnmetListQuery.builder()
                .tenantId(root.getTenantId()).snapshotIds(Set.of(root.getId())).build());
        var context = new ImplementationReadinessContextFact(root.getProjectScopeVersion(),
                deviceRows.stream().map(row -> new DeviceFact(row.getDeviceId(), row.getSn(), row.getProjectAssignmentVersion())).toList(),
                new ApprovedPlanFact(root.getApprovedPlanId(), root.getApprovedPlanVersion()),
                sourceRows.stream().map(row -> new SourceFact(SourceCode.valueOf(row.getSourceCode()),
                        CompletionStatus.valueOf(row.getCompletionStatusCode()), row.getFactVersion(),
                        objectRows.stream().filter(value -> row.getId().equals(value.getSourceFactId()))
                                .map(CutoverReadinessSourceObjectDO::getSourceObjectId).toList(),
                        watermarkRows.stream().filter(value -> row.getId().equals(value.getSourceFactId()))
                                .map(value -> new WatermarkEntry(value.getAxisCode(), value.getObjectId(), value.getFactVersion())).toList(),
                        row.getReopenedFlag())).toList(),
                root.getProjectPlanVersionId() == null && root.getProjectPlanVersion() == null ? null
                        : new PlanApplicability(root.getProjectPlanVersionId(), root.getProjectPlanVersion(),
                        sourceRows.stream().filter(row -> !CompletionStatus.NOT_APPLICABLE.name().equals(row.getCompletionStatusCode()))
                                .map(row -> SourceCode.valueOf(row.getSourceCode())).collect(Collectors.toSet())));
        return new ImplementationReadinessSnapshotFact(root.getTenantId(), root.getProjectId(), root.getId(), root.getSnapshotNo(),
                0L, "CUTOVER", ImplementationReadinessSnapshotFact.Decision.valueOf(root.getDecision()), context,
                unmetRows.stream().map(CutoverReadinessUnmetDO::getUnmetCode).toList(), root.getCreateTime());
    }
}
