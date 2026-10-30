package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryMaterialService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class NativeGeneratedFileService implements NativeGeneratedFileApi {
    private final FileBusinessObjectPolicyRegistry policies;
    private final FileUploadApplicationService uploads;
    private final DeliveryMaterialService materials;
    private final DeliveryMaterialMapper materialMapper;
    private final FileEvidenceApi evidence;
    private final FileAccessTicketService tickets;
    private final PermissionApi permissions;

    @Override @Transactional(rollbackFor=Exception.class)
    public RegisteredFile create(NativeGeneratedFileCommand command) {
        if(command==null || !Objects.equals(command.tenantId(),TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(command.actorUserId(),SecurityFrameworkUtils.getLoginUserId())
                || command.actorUserId()==null || command.actorUserId()<=0
                || !permissions.hasAnyPermissions(command.actorUserId(),"pms:file:upload"))
            throw new BusinessContractException("NATIVE_FILE_ACCESS_DENIED","Authenticated file generation permission required");
        var policy=policies.lockNativeGeneratedFile(new NativeGeneratedFilePolicyQuery(command.tenantId(),command.actorUserId(),
                command.ownerContext(),command.objectType(),command.objectId(),command.expectedOwnerVersion(),command.purposeCode(),null));
        byte[] content=command.content();
        if(content==null || content.length==0)
            throw new BusinessContractException("NATIVE_FILE_CONTENT_REQUIRED","Generated file content is required");
        String digest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(content);
        String slot="generated:"+command.expectedOwnerVersion()+":"+digest;
        var initialized=uploads.initializeAuthorized(new FileUploadInitializeCommand(command.tenantId(),command.actorUserId(),
                command.operationId(),"CREATE_ARTIFACT",null,null,command.ownerContext(),command.objectType(),String.valueOf(command.objectId()),
                command.purposeCode(),slot,command.fileName(),command.categoryCode(),(long)content.length,command.mediaType(),digest),policy);
        policy=policies.lockNativeGeneratedFile(new NativeGeneratedFilePolicyQuery(command.tenantId(),command.actorUserId(),command.ownerContext(),
                command.objectType(),command.objectId(),command.expectedOwnerVersion(),command.purposeCode(),policy.scopeVersion()));
        var completed=uploads.completeAuthorized(new FileUploadCompleteCommand(command.tenantId(),command.actorUserId(),command.operationId(),
                initialized.artifactId(),initialized.sessionId(),null,digest),content,policy);
        var material=materials.registerNativeGeneratedDocument(completed.referenceId());
        return new RegisteredFile(material.getId(),completed.referenceId(),completed.artifactId(),completed.versionNo(),completed.sha256(),command.fileName());
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public String requestDownload(String ownerModule,String entityType,Long entityId,Long materialId) {
        var material=materialMapper.selectById(materialId);
        if(material==null || !TenantContextHolder.getRequiredTenantId().equals(material.getTenantId())
                || !((ownerModule.equals(material.getSourceOwnerModule()) && entityType.equals(material.getSourceEntityType()) && entityId.equals(material.getSourceEntityId()))
                || (ownerModule.equals(material.getOwnerModule()) && entityType.equals(material.getEntityType()) && entityId.equals(material.getEntityId()))))
            throw new BusinessContractException("NATIVE_FILE_ACCESS_DENIED","Material does not belong to this native owner");
        var document=evidence.inspectDocument(TenantContextHolder.getRequiredTenantId(),material.getFileReferenceId());
        if(document==null || !document.available())throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE","File unavailable");
        return tickets.create(new FileAccessTicketService.AccessCommand(TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),
                document.artifactId(),document.versionNo(),FileActionCodes.DOWNLOAD,document.ownerContext(),document.objectType(),document.objectId(),
                document.purposeCode(),document.referenceKey())).getShortLivedUrl();
    }
}
