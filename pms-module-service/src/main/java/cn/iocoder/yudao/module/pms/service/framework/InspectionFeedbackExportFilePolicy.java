package cn.iocoder.yudao.module.pms.service.framework;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionFeedbackExportMapper;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component @RequiredArgsConstructor
public class InspectionFeedbackExportFilePolicy implements FileBusinessObjectPolicyProvider, FileDocumentSourceProvider {
    private final InspectionFeedbackExportAccess access;
    private final InspectionFeedbackExportMapper exports;
    @Override public String ownerContext() { return "SRV"; }
    @Override public String objectType() { return "INSPECTION_FEEDBACK_EXPORT"; }
    @Override public List<Descriptor> descriptors() { return List.of(new Descriptor("INS.EXPORT", "巡检误报导出")); }
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        return resolve(new Resolution(query, false, null, null, null));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return resolve(new Resolution(query.toInspectionQuery(), true, query.expectedScopeVersion(), null, null));
    }
    @Override public FileBusinessObjectPolicyFact inspectFrozenFileVersion(FrozenFileVersionAccessQuery query) {
        return resolve(new Resolution(query.scope(), false, query.expectedScopeVersion(), query, null));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateFrozenFileVersion(FrozenFileVersionAccessQuery query) {
        return resolve(new Resolution(query.scope(), true, query.expectedScopeVersion(), query, null));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateNativeGeneratedFile(NativeGeneratedFilePolicyQuery query) {
        return resolve(new Resolution(new FileBusinessObjectPolicyQuery(query.tenantId(), query.actorUserId(), query.ownerContext(),
                query.objectType(), String.valueOf(query.objectId()), query.purposeCode(), "generated", FileActionCodes.UPLOAD),
                true, query.expectedScopeVersion(), null, query.expectedOwnerVersion()));
    }
    @Override public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        var key = query.key();
        return inspect(new FileBusinessObjectPolicyQuery(query.tenantId(), query.actorUserId(), key.ownerContext(), key.objectType(),
                key.objectId(), key.purposeCode(), "reference-set", query.requiredAction()));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery query) {
        var key = query.key();
        return lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(query.tenantId(), query.actorUserId(), key.ownerContext(), key.objectType(),
                key.objectId(), key.purposeCode(), "reference-set", query.requiredAction(), query.expectedScopeVersion()));
    }
    private record Resolution(FileBusinessObjectPolicyQuery query, boolean lock, Long scopeVersion,
                              FrozenFileVersionAccessQuery frozen, Long ownerVersion) { }
    private FileBusinessObjectPolicyFact resolve(Resolution request) {
        var query = request.query();
        if (!Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()) || !ownerContext().equals(query.ownerContext())
                || !objectType().equals(query.objectType()) || !"EXPORT".equals(query.purposeCode())) return denied();
        Long id;
        try { id = Long.valueOf(query.objectId()); } catch (NumberFormatException invalid) { return denied(); }
        var row = access.authorized(new InspectionFeedbackExportAccess.Query(InspectionRequestContext.builder()
                .tenantId(query.tenantId()).actorId(query.actorUserId()).build(), id, request.lock()));
        if (request.scopeVersion() != null && !Objects.equals(request.scopeVersion(), row.getVersion())) return denied();
        boolean generating = request.ownerVersion() != null && FileActionCodes.UPLOAD.equals(query.requiredAction())
                && "GENERATING".equals(row.getStatusCode()) && Objects.equals(request.ownerVersion(), row.getVersion());
        boolean reading = Set.of(FileActionCodes.READ, FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW).contains(query.requiredAction())
                && "AVAILABLE".equals(row.getStatusCode())
                && ("reference-set".equals(query.referenceKey()) || Objects.equals(row.getReferenceKey(), query.referenceKey()));
        if (reading && request.frozen() != null) reading = Objects.equals(row.getArtifactId(), request.frozen().artifactId())
                && Objects.equals(row.getFileVersionNo(), request.frozen().versionNo());
        var media = "CSV".equals(row.getFormatCode()) ? Set.of("text/csv", "text/plain")
                : Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return new FileBusinessObjectPolicyFact(generating || reading, row.getVersion(), generating ? "MUTABLE" : "IMMUTABLE",
                "SINGLE", Set.of("INSPECTION_FEEDBACK_EXPORT"), media, 52_428_800L, "INTERNAL");
    }
    @Override public Scope resolve(Long tenantId, FileEvidenceApi.Document document) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId()) || !ownerContext().equals(document.ownerContext())
                || !objectType().equals(document.objectType()) || !"EXPORT".equals(document.purposeCode())) return null;
        Long id;
        try { id = Long.valueOf(document.objectId()); } catch (NumberFormatException invalid) { return null; }
        var row = exports.selectById(id);
        if (row == null || !Objects.equals(tenantId, row.getTenantId())) return null;
        return new Scope(null, "INS.EXPORT", "SRV", "inspectionFeedbackExport", id, row.getId());
    }
    private static FileBusinessObjectPolicyFact denied() { return new FileBusinessObjectPolicyFact(false, null, null, null, null, null, null, null); }
}
