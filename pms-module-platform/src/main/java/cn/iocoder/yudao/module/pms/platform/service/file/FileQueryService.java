package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileArtifactRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileCursorPageRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileReferenceRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileVersionRespVO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.ExactFileReferenceQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionCursorQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_ARTIFACT_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_REFERENCE_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_VERSION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class FileQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final FileBusinessObjectPolicyRegistry policyRegistry;
    private final FileArtifactMapper artifactMapper;
    private final FileVersionMapper versionMapper;
    private final FileReferenceMapper referenceMapper;
    private final SecurityFrameworkService securityFrameworkService;

    public FileArtifactRespVO getArtifact(ArtifactQuery query, Actor actor) {
        ValidatedKey key = validate(query, actor, FileActionCodes.READ);
        if (!canRead(key)) return null;
        FileReferenceDO reference = requireReference(referenceMapper.selectExact(key.referenceQuery()), key.artifactId());
        FileArtifactDO artifact = requireArtifact(artifactMapper.selectOne(
                new FileArtifactLockQuery(actor.tenantId(), key.artifactId())));
        FileVersionDO version = requireVersion(versionMapper.selectOne(new FileVersionLockQuery(
                actor.tenantId(), key.artifactId(), reference.getFileVersionNo())));

        FileArtifactRespVO response = new FileArtifactRespVO();
        response.setArtifactId(artifact.getId());
        response.setName(artifact.getName());
        response.setCategoryCode(artifact.getCategoryCode());
        response.setOwnerContext(artifact.getOwnerContext());
        response.setLifecycleStatus(artifact.getLifecycleStatusCode());
        response.setArtifactVersion(artifact.getVersion());
        response.setReference(toReference(reference));
        response.setAllowedActions(allowedActions(key, reference.getStatusCode(), version, actor));
        response.setCreatedAt(artifact.getCreateTime());
        return response;
    }

    public FileCursorPageRespVO<FileVersionRespVO> getVersions(
            ArtifactQuery query, String cursor, Integer requestedPageSize, Actor actor) {
        ValidatedKey key = validate(query, actor, FileActionCodes.READ);
        if (!canRead(key)) return new FileCursorPageRespVO<>(List.of(), null, false);
        requireReference(referenceMapper.selectExact(key.referenceQuery()), key.artifactId());
        requireArtifact(artifactMapper.selectOne(new FileArtifactLockQuery(actor.tenantId(), key.artifactId())));

        VersionCursor parsed = parseCursor(cursor);
        int pageSize = pageSize(requestedPageSize);
        List<FileVersionDO> fetched = versionMapper.selectCursor(new FileVersionCursorQuery(
                actor.tenantId(), key.artifactId(), parsed.versionNo(), parsed.id(), pageSize + 1));
        boolean hasMore = fetched.size() > pageSize;
        List<FileVersionDO> page = hasMore ? fetched.subList(0, pageSize) : fetched;
        List<FileVersionRespVO> items = page.stream().map(this::toVersion).toList();
        String nextCursor = hasMore ? page.getLast().getVersionNo() + ":" + page.getLast().getId() : null;
        return new FileCursorPageRespVO<>(items, nextCursor, hasMore);
    }

    public FileReferenceRespVO getReference(ArtifactQuery query, Actor actor) {
        ValidatedKey key = validate(query, actor, FileActionCodes.READ);
        if (!canRead(key)) return null;
        return toReference(requireReference(referenceMapper.selectExact(key.referenceQuery()), key.artifactId()));
    }

    private boolean canRead(ValidatedKey key) {
        try {
            policyRegistry.inspect(key.policyQuery());
            return true;
        } catch (ServiceException ex) {
            if (FILE_SCOPE_FORBIDDEN.getCode().equals(ex.getCode())) return false;
            throw ex;
        }
    }

    private List<String> allowedActions(ValidatedKey key, String referenceStatus,
                                        FileVersionDO version, Actor actor) {
        List<String> actions = new ArrayList<>();
        actions.add(FileActionCodes.READ);
        if (!"ACTIVE".equals(referenceStatus) || !"AVAILABLE".equals(version.getAvailabilityStatusCode())) {
            return List.copyOf(actions);
        }
        addAllowedAction(actions, key, actor, FileActionCodes.DOWNLOAD, "pms:file:download", true);
        addAllowedAction(actions, key, actor, FileActionCodes.PREVIEW, "pms:file:preview",
                FileAccessTicketService.isPreviewable(version.getDetectedMediaType()));
        return List.copyOf(actions);
    }

    private void addAllowedAction(List<String> actions, ValidatedKey key, Actor actor, String action,
                                  String permission, boolean formatSupported) {
        if (!formatSupported || !securityFrameworkService.hasPermission(permission)) {
            return;
        }
        try {
            policyRegistry.inspect(key.policyQuery(action));
            actions.add(action);
        } catch (ServiceException ignored) {
            // allowedActions is a projection; the action endpoint remains the permission truth.
        }
    }

    private ValidatedKey validate(ArtifactQuery query, Actor actor, String action) {
        if (query == null || actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorUserId() == null || actor.actorUserId() <= 0
                || query.artifactId() == null || query.artifactId() <= 0) {
            throw exception(FILE_COMMAND_INVALID);
        }
        try {
            String ownerContext = FileActionCodes.requireText(query.ownerContext(), "ownerContext");
            String objectType = FileActionCodes.requireText(query.objectType(), "objectType");
            String objectId = FileActionCodes.requireText(query.objectId(), "objectId");
            String purposeCode = FileActionCodes.requireText(query.purposeCode(), "purposeCode");
            String referenceKey = FileActionCodes.requireText(query.referenceKey(), "referenceKey");
            ExactFileReferenceQuery referenceQuery = new ExactFileReferenceQuery(actor.tenantId(), ownerContext,
                    objectType, objectId, purposeCode, referenceKey);
            FileBusinessObjectPolicyQuery policyQuery = new FileBusinessObjectPolicyQuery(actor.tenantId(),
                    actor.actorUserId(), ownerContext, objectType, objectId, purposeCode, referenceKey, action);
            return new ValidatedKey(query.artifactId(), referenceQuery, policyQuery);
        } catch (IllegalArgumentException ex) {
            throw exception(FILE_COMMAND_INVALID);
        }
    }

    private FileArtifactDO requireArtifact(FileArtifactDO artifact) {
        if (artifact == null) throw exception(FILE_ARTIFACT_NOT_FOUND);
        return artifact;
    }

    private FileVersionDO requireVersion(FileVersionDO version) {
        if (version == null) throw exception(FILE_VERSION_NOT_FOUND);
        return version;
    }

    private FileReferenceDO requireReference(FileReferenceDO reference, Long artifactId) {
        if (reference == null || !artifactId.equals(reference.getArtifactId())) {
            throw exception(FILE_REFERENCE_NOT_FOUND);
        }
        return reference;
    }

    private int pageSize(Integer requested) {
        if (requested == null) return DEFAULT_PAGE_SIZE;
        if (requested < 1 || requested > MAX_PAGE_SIZE) throw exception(FILE_COMMAND_INVALID);
        return requested;
    }

    private VersionCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new VersionCursor(null, null);
        String[] parts = cursor.split(":", -1);
        try {
            if (parts.length != 2) throw new NumberFormatException();
            int versionNo = Integer.parseInt(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (versionNo <= 0 || id <= 0) throw new NumberFormatException();
            return new VersionCursor(versionNo, id);
        } catch (NumberFormatException ex) {
            throw exception(FILE_COMMAND_INVALID);
        }
    }

    private FileVersionRespVO toVersion(FileVersionDO row) {
        FileVersionRespVO response = new FileVersionRespVO();
        response.setId(row.getId());
        response.setVersionNo(row.getVersionNo());
        response.setSha256(row.getSha256());
        response.setSizeBytes(row.getSizeBytes());
        response.setMediaType(row.getDetectedMediaType());
        response.setScanStatus(row.getScanStatusCode());
        response.setAvailabilityStatus(row.getAvailabilityStatusCode());
        response.setAvailabilityVersion(row.getAvailabilityVersion());
        response.setUnavailableReasonCode(row.getUnavailableReasonCode());
        response.setVersionNote(row.getVersionNote());
        response.setCreatedBy(row.getCreatedBy());
        response.setCreatedAt(row.getCreatedAt());
        return response;
    }

    private FileReferenceRespVO toReference(FileReferenceDO row) {
        FileReferenceRespVO response = new FileReferenceRespVO();
        response.setReferenceId(row.getId());
        response.setOwnerContext(row.getOwnerContext());
        response.setObjectType(row.getObjectType());
        response.setObjectId(row.getObjectId());
        response.setPurposeCode(row.getPurposeCode());
        response.setReferenceKey(row.getReferenceKey());
        response.setArtifactId(row.getArtifactId());
        response.setVersionNo(row.getFileVersionNo());
        response.setSensitivityCode(row.getSensitivityCode());
        response.setStatus(row.getStatusCode());
        response.setScopeVersion(row.getScopeVersion());
        response.setReferenceVersion(row.getVersion());
        response.setCreatedAt(row.getCreateTime());
        response.setUpdatedAt(row.getUpdateTime());
        return response;
    }

    public record ArtifactQuery(Long artifactId, String ownerContext, String objectType,
                                String objectId, String purposeCode, String referenceKey) {
    }

    public record Actor(Long tenantId, Long actorUserId) {
    }

    private record VersionCursor(Integer versionNo, Long id) {
    }

    private record ValidatedKey(Long artifactId, ExactFileReferenceQuery referenceQuery,
                                FileBusinessObjectPolicyQuery policyQuery) {
        FileBusinessObjectPolicyQuery policyQuery(String action) {
            return new FileBusinessObjectPolicyQuery(policyQuery.tenantId(), policyQuery.actorUserId(),
                    policyQuery.ownerContext(), policyQuery.objectType(), policyQuery.objectId(),
                    policyQuery.purposeCode(), policyQuery.referenceKey(), action.toUpperCase(Locale.ROOT));
        }
    }
}
