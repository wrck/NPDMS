package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** Validity follows the immutable scored result and its actual document/signature files. */
@Component @RequiredArgsConstructor
public class SatisfactionBusinessResultSource implements BusinessResultChangeSource, BusinessResultInventorySource {
    public static final Type TYPE = new Type("ACC", "SATISFACTION", "SATISFACTION_PASSED");
    private final SatisfactionCollectionTaskMapper tasks;
    private final SatisfactionResultMapper results;
    private final SatisfactionResultFileMapper files;
    private final FileEvidenceApi fileEvidence;

    @Override public Descriptor descriptor() { return new Descriptor(TYPE, true, true, true); }
    @Override public boolean transactionalChangeCoverage() { return true; }
    @Override public Query changeQuery(BusinessOperationResultEvent event) {
        if (event == null || !TYPE.ownerContext().equals(event.ownerContext()) || !TYPE.entityType().equals(event.objectType())
                || event.revisionId() == null) throw new IllegalArgumentException("SATISFACTION_RESULT_EVENT_INVALID");
        return new Query(event.tenantId(), event.projectId(), TYPE, event.objectId(), event.revisionId());
    }
    @Override @Transactional(readOnly = true)
    public InventoryPage inventory(InventoryQuery query) {
        if (query == null || !TYPE.equals(query.type()) || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        var ids = results.selectResultInventory(new SatisfactionResultInventoryQuery(query.tenantId(), query.projectId(),
                BusinessResultInventorySource.nativeObjects(query), BusinessResultSource.nativeId(query.after()), query.limit()+1, query.historical()));
        return BusinessResultInventorySource.nativePage(query, ids, id -> inspect(new Query(query.tenantId(), query.projectId(), TYPE, null, id)));
    }
    @Override public Observation inspect(Query query) { return inspect(query, false); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Observation lockAndInspect(Query query) { return inspect(query, true); }

    private Observation inspect(Query query, boolean lock) {
        if (query == null || !TYPE.equals(query.type()) || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        Long taskId = BusinessResultSource.nativeId(query.objectId()), resultId = BusinessResultSource.nativeId(query.resultId());
        if (lock && (taskId == null || resultId == null)) throw new IllegalArgumentException("RESULT_EXACT_IDENTITY_REQUIRED");
        var observed = resultId == null ? null : results.selectById(resultId);
        if (resultId != null && observed == null) return absent(Status.NOT_FOUND, "RESULT_NOT_FOUND");
        if (observed != null) {
            if (!resultId.equals(observed.getId()) || !query.tenantId().equals(observed.getTenantId())
                    || taskId != null && !taskId.equals(observed.getCollectionTaskId()))
                throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
            taskId = observed.getCollectionTaskId();
        }
        if (taskId == null) return absent(Status.NOT_FOUND, "RESULT_NOT_FOUND");
        var task = lock ? tasks.selectByIdForUpdate(query.tenantId(), taskId) : tasks.selectById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getDeleted())) return absent(Status.NOT_FOUND, "RESULT_NOT_FOUND");
        if (!taskId.equals(task.getId()) || !query.tenantId().equals(task.getTenantId()) || !query.projectId().equals(task.getProjectId()))
            throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
        if (resultId == null) resultId = task.getResultId();
        if (resultId == null) return absent(Status.NOT_FORMED, "SATISFACTION_RESULT_NOT_FORMED");
        var result = lock ? results.selectByIdForUpdate(query.tenantId(), resultId) : results.selectById(resultId);
        if (result == null) return absent(Status.NOT_FOUND, "RESULT_NOT_FOUND");
        if (!resultId.equals(result.getId()) || !query.tenantId().equals(result.getTenantId()) || !task.getId().equals(result.getCollectionTaskId()))
            throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
        boolean current = "EFFECTIVE".equals(result.getResultStatus()) && Boolean.TRUE.equals(result.getPassed())
                && result.getEffectiveTo() == null && result.getId().equals(task.getResultId());
        if (result.getVersion() == null || result.getResultVersion() == null || result.getEffectiveFrom() == null)
            return absent(Status.UNAVAILABLE, "SATISFACTION_RESULT_INCONSISTENT");
        if (current && lock && !validFiles(query.tenantId(), result))
            return absent(Status.UNAVAILABLE, "SATISFACTION_RESULT_FILES_INVALID");
        var validity = current ? Validity.CURRENT : "INVALIDATED".equals(result.getResultStatus()) ? Validity.REVOKED : Validity.NOT_CURRENT;
        return Observation.available(new Result(query.tenantId(), query.projectId(), TYPE, task.getId().toString(), result.getId().toString(),
                result.getResultVersion().toString(), result.getVersion() + ":" + task.getVersion(), validity, result.getEffectiveFrom()));
    }

    private boolean validFiles(Long tenant, cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResultDO result) {
        var rows = files.selectListByResult(new SatisfactionResultFilesQuery(tenant, result.getId()));
        if (rows.stream().noneMatch(row -> "RESULT_DOCUMENT".equals(row.getFileRole()))
                || rows.stream().noneMatch(row -> "SIGNATURE".equals(row.getFileRole()))) return false;
        for (var row : rows) {
            boolean document = "RESULT_DOCUMENT".equals(row.getFileRole());
            String purpose = document ? "SATISFACTION_RESULT_DOCUMENT" : "SIGNATURE".equals(row.getFileRole())
                    ? "SATISFACTION_SIGNATURE" : "ATTACHMENT".equals(row.getFileRole()) ? "SATISFACTION_ATTACHMENT" : null;
            if (purpose == null || !tenant.equals(row.getTenantId()) || !result.getId().equals(row.getResultId())) return false;
            var fact = fileEvidence.lockAndRevalidate(new FileEvidenceApi.Query(tenant, row.getArtifactId(), row.getVersionNo(), "ACC",
                    document ? "SATISFACTION_RESULT" : "SATISFACTION_RESPONSE", (document ? result.getId() : result.getResponseId()).toString(),
                    purpose, row.getReferenceKey(), row.getFileHash()));
            if (fact == null || !fact.valid() || !Objects.equals(fact.artifactVersion(), row.getArtifactVersion())
                    || !Objects.equals(fact.referenceVersion(), row.getReferenceVersion())
                    || !Objects.equals(fact.availabilityVersion(), row.getAvailabilityVersion())) return false;
        }
        return true;
    }
    private Observation absent(Status status, String reason) { return Observation.absent(status, reason); }
}
