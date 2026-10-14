package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileReferenceRespVO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.ExactFileReferenceQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;

/** Recover an uploaded slot by its complete business key, without changing its file identity. */
@Service
@RequiredArgsConstructor
public class FileCurrentReferenceQueryService {
    private final FileBusinessObjectPolicyRegistry policies;
    private final FileReferenceMapper references;
    private final FileQueryService files;

    public FileReferenceRespVO resolve(ExactFileReferenceQuery query, FileQueryService.Actor actor) {
        if (query == null || actor == null || actor.actorUserId() == null || actor.actorUserId() <= 0) {
            throw exception(FILE_COMMAND_INVALID);
        }
        if (actor.tenantId() == null || actor.tenantId() < 0 || !Objects.equals(actor.tenantId(), TenantContextHolder.getTenantId())
                || !Objects.equals(query.tenantId(), actor.tenantId())) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        ExactFileReferenceQuery key;
        try {
            key = new ExactFileReferenceQuery(actor.tenantId(),
                    FileActionCodes.requireText(query.ownerContext(), "ownerContext"),
                    FileActionCodes.requireText(query.objectType(), "objectType"),
                    FileActionCodes.requireText(query.objectId(), "objectId"),
                    FileActionCodes.requireText(query.purposeCode(), "purposeCode"),
                    FileActionCodes.requireText(query.referenceKey(), "referenceKey"));
        } catch (IllegalArgumentException invalid) {
            throw exception(FILE_COMMAND_INVALID);
        }
        try {
            policies.inspect(new FileBusinessObjectPolicyQuery(actor.tenantId(), actor.actorUserId(),
                    key.ownerContext(), key.objectType(), key.objectId(), key.purposeCode(), key.referenceKey(),
                    FileActionCodes.READ));
        } catch (ServiceException denied) {
            if (FILE_SCOPE_FORBIDDEN.getCode().equals(denied.getCode())) return null;
            throw denied;
        }
        var reference = references.selectExact(key);
        if (reference == null) return null;
        return files.getReference(new FileQueryService.ArtifactQuery(reference.getArtifactId(),
                key.ownerContext(), key.objectType(), key.objectId(), key.purposeCode(), key.referenceKey()), actor);
    }
}
