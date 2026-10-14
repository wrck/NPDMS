package cn.iocoder.yudao.module.pms.cutover.framework.adapter;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.module.pms.cutover.service.collection.CutoverCollectionResultFacts;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.collection.CutoverCollectionLogDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.collection.CutoverCollectionLogMapper;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationService;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CutoverCollectionResultReceiver implements CollectionBusinessResultReceiver {
    private final CutoverCollectionSources sources;
    private final CutoverCollectionLogMapper logs;
    private final CutoverChecklistApplicationService checklist;
    private final CutoverClosureApplicationService closure;

    @Override public Set<String> entries() {
        var values = new HashSet<>(CutoverCollectionSources.CLOSURE_STAGES.keySet());
        values.add(CutoverCollectionSources.CHECKLIST);
        return Set.copyOf(values);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Result result) {
        if (result == null || !Objects.equals(TenantContextHolder.getRequiredTenantId(), result.tenantId())
                || !entries().contains(result.entry()) || !"CUT".equals(result.sourceContext())
                || result.executionId() == null || result.executionId() <= 0 || result.actorId() == null || result.actorId() <= 0
                || result.resultVersion() == null || result.resultVersion() <= 0 || result.fileVersionId() == null
                || result.platformTaskId() == null || result.platformTaskId().isBlank()
                || !Set.of("SUCCESS", "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "TIMED_OUT", "CANCELLED")
                        .contains(Objects.toString(result.externalStatus(), ""))) throw new CollectionOperationException("割接采集回执无效");
        var owner = sources.owner(result.tenantId(), result.entry(), result.objectId(), true);
        if (!Objects.equals(owner.task().getProjectId(), result.projectId()) || !owner.objectType().equals(result.sourceObjectType())
                || sources.taskDevices(result.tenantId(), owner.task().getId()).stream()
                        .noneMatch(d -> Objects.equals(d.getDeviceId(), result.deviceId()))) {
            throw new CollectionOperationException("采集回执与割接归属不一致");
        }
        var old = logs.findResult(new cn.iocoder.yudao.module.pms.cutover.dal.mysql.collection.query.CutoverCollectionResultQuery(
                result.tenantId(), result.platformTaskId(), result.resultVersion()));
        if (old != null) {
            if (!Objects.equals(old.getTaskId(), owner.task().getId()) || !Objects.equals(CutoverCollectionResultFacts.read(old), result)) {
                throw new CollectionOperationException("采集回执重放与原证据不一致");
            }
            return;
        }
        var row = new CutoverCollectionLogDO();
        row.setId(IdUtil.getSnowflakeNextId()); row.setTenantId(result.tenantId()); row.setTaskId(owner.task().getId());
        CutoverCollectionResultFacts.apply(new CutoverCollectionResultFacts.Write(row, result));
        row.setCreator(result.actorId().toString()); row.setVersion(0L);
        if (logs.insert(row) != 1) throw new IllegalStateException("CUT_COLLECTION_RESULT_CREATE_FAILED");
        if (CutoverCollectionSources.CHECKLIST.equals(result.entry())) checklist.receivePlatformResult(result);
        else closure.receivePlatformResult(result);
    }
}
