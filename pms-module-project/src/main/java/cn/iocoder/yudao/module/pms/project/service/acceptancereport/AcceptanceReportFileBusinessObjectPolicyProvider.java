package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.projection.AcceptanceReportFileScope;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceReportFileScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AcceptanceReportFileBusinessObjectPolicyProvider implements FileBusinessObjectPolicyProvider {

    static final String OWNER_CONTEXT = "ACC";
    static final String OBJECT_TYPE = "ACCEPTANCE_REPORT_VERSION";
    static final String ATTACHMENT_PURPOSE = "ACCEPTANCE_REPORT_ATTACHMENT";
    static final String ARCHIVE_PURPOSE = "ACCEPTANCE_REPORT_ARCHIVE";
    static final String CATEGORY_CODE = "ACCEPTANCE_REPORT_ATTACHMENT";
    private static final long MAX_SIZE_BYTES = 52_428_800L;
    private static final Set<String> MEDIA_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "text/plain", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final Set<String> READ_ACTIONS = Set.of(
            FileActionCodes.READ, FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);
    private static final Set<String> WRITE_ACTIONS = Set.of(
            FileActionCodes.UPLOAD, FileActionCodes.REFERENCE, FileActionCodes.REPLACE, FileActionCodes.DETACH);

    private final AcceptanceReportVersionMapper reportVersionMapper;
    private final ProjectScopeApi projectScopeApi;

    @Override
    public String ownerContext() {
        return OWNER_CONTEXT;
    }

    @Override
    public String objectType() {
        return OBJECT_TYPE;
    }

    @Override
    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        if (!validReferenceKey(query.referenceKey())) return denied();
        return resolve(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(),
                query.requiredAction(), false, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        if (!validReferenceKey(query.referenceKey())) return denied();
        return resolve(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(),
                query.requiredAction(), true, query.expectedScopeVersion());
    }

    @Override
    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        return resolveSet(query.tenantId(), query.actorUserId(), query.key(), query.requiredAction(), false, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        return resolveSet(query.tenantId(), query.actorUserId(), query.key(), query.requiredAction(), true,
                query.expectedScopeVersion());
    }

    private FileBusinessObjectPolicyFact resolveSet(Long tenantId, Long actorUserId, FileReferenceSetKey key,
                                                    String action, boolean lock, Long expectedScopeVersion) {
        if (key == null || !OWNER_CONTEXT.equals(key.ownerContext()) || !OBJECT_TYPE.equals(key.objectType())) {
            return denied();
        }
        return resolve(tenantId, actorUserId, key.objectId(), key.purposeCode(), action, lock, expectedScopeVersion);
    }

    private FileBusinessObjectPolicyFact resolve(Long tenantId, Long actorUserId, String objectId, String purpose,
                                                 String action, boolean lock, Long expectedScopeVersion) {
        Long reportVersionId = parsePositiveLong(objectId);
        if (reportVersionId == null || !supportedPurposeAction(purpose, action)) return denied();
        try {
            AcceptanceReportFileScope report = reportVersionMapper.selectFileScope(
                    new AcceptanceReportFileScopeQuery(tenantId, reportVersionId));
            if (!validReport(reportVersionId, report) || !stateAllows(report.reportStatus(), action)) return denied();
            String projectAction = READ_ACTIONS.contains(action)
                    ? ProjectScopeApi.ACTION_VIEW : ProjectScopeApi.ACTION_EDIT;
            ProjectScopeResult scope = lock
                    ? projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                    tenantId, actorUserId, report.projectId(), projectAction, expectedScopeVersion))
                    : projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                    tenantId, actorUserId, report.projectId(), projectAction));
            if (!validScope(report.projectId(), expectedScopeVersion, lock, scope)) return denied();
            String mutability = "DRAFT".equals(report.reportStatus()) ? "MUTABLE" : "IMMUTABLE";
            return new FileBusinessObjectPolicyFact(true, scope.treeVersion(), mutability, "MULTIPLE",
                    Set.of(CATEGORY_CODE), MEDIA_TYPES, MAX_SIZE_BYTES, "INTERNAL");
        } catch (RuntimeException unavailable) {
            return denied();
        }
    }

    private boolean supportedPurposeAction(String purpose, String action) {
        if (ATTACHMENT_PURPOSE.equals(purpose)) {
            return READ_ACTIONS.contains(action) || WRITE_ACTIONS.contains(action) || FileActionCodes.ARCHIVE.equals(action);
        }
        return ARCHIVE_PURPOSE.equals(purpose) && FileActionCodes.ARCHIVE.equals(action);
    }

    private boolean stateAllows(String reportStatus, String action) {
        if (READ_ACTIONS.contains(action)) return Set.of("DRAFT", "EFFECTIVE", "SUPERSEDED", "REVOKED").contains(reportStatus);
        if (WRITE_ACTIONS.contains(action)) return "DRAFT".equals(reportStatus);
        return FileActionCodes.ARCHIVE.equals(action) && !"DRAFT".equals(reportStatus);
    }

    private boolean validReport(Long reportVersionId, AcceptanceReportFileScope report) {
        return report != null && Objects.equals(report.reportVersionId(), reportVersionId)
                && report.acceptanceId() != null && report.acceptanceId() > 0
                && report.projectId() != null && report.projectId() > 0
                && report.projectTaskId() != null && report.projectTaskId() > 0;
    }

    private boolean validScope(Long projectId, Long expectedScopeVersion, boolean lock, ProjectScopeResult scope) {
        return scope != null && scope.treeVersion() != null
                && (!lock || Objects.equals(scope.treeVersion(), expectedScopeVersion))
                && scope.fullProjectIds() != null && scope.fullProjectIds().contains(projectId);
    }

    private Long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private boolean validReferenceKey(String value) {
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, null, null, Set.of(), Set.of(), null, null);
    }
}
