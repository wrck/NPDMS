package cn.iocoder.yudao.module.pms.cutover.api.result;

import cn.iocoder.yudao.module.pms.cutover.service.collection.CutoverCollectionResultFacts;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.collection.CutoverCollectionLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class CutoverResultApiImpl implements CutoverResultApi {
    private final CutoverSuccessMapper successes;
    private final CutoverClosureMapper closures;
    private final CutoverTaskMapper tasks;
    private final cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureStorage devices;
    private final CutoverCollectionEvidenceMapper evidence;
    private final CutoverClosureAttachmentMapper attachments;
    private final CutoverCollectionLogMapper logs;

    @Override @Transactional(readOnly = true)
    public List<Result> successes(ProjectQuery query) {
        scope(query.tenantId(), query.projectId());
        return successes.projectSuccesses(query).stream().map(row -> read(row, false)).toList();
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Result lockAndInspect(ResultQuery query) {
        scope(query.tenantId(), query.projectId());
        var task = tasks.selectForUpdate(new CutoverTaskRowQuery(query.tenantId(), query.taskId()));
        if (task == null || !Objects.equals(task.getProjectId(), query.projectId()))
            throw new IllegalArgumentException("割接归档任务身份无效");
        var row = closures.selectByTaskForUpdate(new CutoverClosureRowQuery(query.tenantId(), query.taskId()));
        if (row == null || !Objects.equals(row.getId(), query.closureId()) || !Objects.equals(row.getVersion(), query.closureVersion())
                || !Objects.equals(row.getProjectId(), query.projectId())) throw new IllegalArgumentException("割接归档结果身份已失效");
        return read(row, true);
    }
    private Result read(CutoverClosureDO row, boolean lock) {
        var taskQuery = new CutoverTaskRowQuery(row.getTenantId(), row.getTaskId());
        var task = lock ? tasks.selectForUpdate(taskQuery) : tasks.selectById(row.getTaskId());
        if (task == null || !Objects.equals(task.getTenantId(), row.getTenantId()) || !Objects.equals(task.getProjectId(), row.getProjectId())
                || !"ARCHIVED".equals(task.getTaskStatus()) || !"SUBMITTED".equals(row.getStatusCode())
                || !"SUCCESS".equals(row.getFinalResultCode()) || row.getArchivedAt() == null || row.getSubmittedAt() == null
                || row.getResultRef() == null) throw new IllegalArgumentException("割接尚未形成成功归档事实");
        var children = new CutoverClosureChildrenQuery(row.getTenantId(), row.getId());
        var events = lock ? evidence.selectListByClosureForUpdate(children) : evidence.selectListByClosure(children);
        var files = attachments.selectListByClosure(children);
        var collectionFiles = new ArrayList<CollectionFile>();
        var manualFiles = new ArrayList<ManualFile>();
        for (var fact : events) {
            if (!"POST_COLLECTION".equals(fact.getCollectionStageCode())) continue;
            if ("CALLBACK_SUCCEEDED".equals(fact.getEvidenceTypeCode())) {
                var log = logs.findResult(new cn.iocoder.yudao.module.pms.cutover.dal.mysql.collection.query.CutoverCollectionResultQuery(
                        row.getTenantId(), fact.getCollectionTaskId(), Long.valueOf(fact.getResultVersion())));
                if (log == null || !Objects.equals(log.getTaskId(), row.getTaskId()) || !Objects.equals(log.getDeviceId(), fact.getDeviceId()))
                    throw new IllegalArgumentException("割接采集文件证据缺失");
                var result = CutoverCollectionResultFacts.read(log);
                collectionFiles.add(new CollectionFile(fact.getDeviceId(), fact.getCollectionStageCode(), result.actorId(),
                        result.platformTaskId(), result.resultVersion(), result.fileVersionId()));
            } else if ("MANUAL_UPLOAD".equals(fact.getEvidenceTypeCode())) {
                var attachment = files.stream().filter(file -> Objects.equals(file.getId(), fact.getManualAttachmentId())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("割接人工降级文件证据缺失"));
                manualFiles.add(new ManualFile(fact.getDeviceId(), fact.getCollectionStageCode(), attachment.getArtifactId(),
                        attachment.getFileVersionNo(), attachment.getReferenceKey(), attachment.getFileHash()));
            }
        }
        var frozen = devices.frozen(row).stream()
                .map(device -> new Device(device.getDeviceId(), device.getSn(), device.getProjectAssignmentVersion())).toList();
        if (frozen.isEmpty()) throw new IllegalArgumentException("割接成功事实缺少冻结设备范围");
        return new Result(row.getTenantId(), row.getProjectId(), row.getTaskId(), task.getTaskNo(), task.getTaskName(),
                row.getId(), row.getVersion(), row.getResultRef(), row.getSubmittedBy(), row.getArchivedAt(), frozen, collectionFiles, manualFiles);
    }
    private void scope(Long tenant, Long project) {
        if (!Objects.equals(tenant, TenantContextHolder.getTenantId()) || project == null || project <= 0)
            throw new SecurityException("割接结果租户或项目身份无效");
    }
}
