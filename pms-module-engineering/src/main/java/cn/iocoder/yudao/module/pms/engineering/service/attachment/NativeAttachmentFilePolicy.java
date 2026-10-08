package cn.iocoder.yudao.module.pms.engineering.service.attachment;

import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NativeAttachmentFilePolicy implements FileBusinessObjectPolicyProvider {
    private final NativeAttachmentKind kind;
    private final NativeAttachmentAccess access;
    @Override public String ownerContext(){return kind.getModule();}
    @Override public String objectType(){return kind.getEntityType();}
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery q){return access.require(kind,q.tenantId(),q.actorUserId(),q.objectId(),q.purposeCode(),q.requiredAction(),false,null);}
    @Override public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery q){return access.require(kind,q.tenantId(),q.actorUserId(),q.objectId(),q.purposeCode(),q.requiredAction(),true,q.expectedScopeVersion());}
    @Override public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery q){return access.require(kind,q.tenantId(),q.actorUserId(),q.key().objectId(),q.key().purposeCode(),q.requiredAction(),false,null);}
    @Override public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery q){return access.require(kind,q.tenantId(),q.actorUserId(),q.key().objectId(),q.key().purposeCode(),q.requiredAction(),true,q.expectedScopeVersion());}
}
