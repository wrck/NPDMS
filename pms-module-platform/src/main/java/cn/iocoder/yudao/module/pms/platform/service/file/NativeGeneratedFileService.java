package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialIdLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryOwnerAccess;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryMaterialService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.List;

@Service @RequiredArgsConstructor
public class NativeGeneratedFileService implements NativeGeneratedFileApi {
    private final FileBusinessObjectPolicyRegistry policies;
    private final FileUploadApplicationService uploads;
    private final DeliveryMaterialService materials;
    private final DeliveryMaterialMapper materialMapper;
    private final FileEvidenceApi evidence;
    private final FileAccessTicketService tickets;
    private final PermissionApi permissions;
    private final DeliveryOwnerAccess owners;

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
        if(ownerModule==null || entityType==null || entityId==null || entityId<=0 || materialId==null || materialId<=0
                || SecurityFrameworkUtils.getLoginUserId()==null || SecurityFrameworkUtils.getLoginUserId()<=0)
            throw new BusinessContractException("NATIVE_FILE_ACCESS_DENIED","Authenticated native owner required");
        var material=materialMapper.selectById(materialId);
        if(!belongsTo(material,ownerModule,entityType,entityId))
            throw new BusinessContractException("NATIVE_FILE_ACCESS_DENIED","Material does not belong to this native owner");
        // Match native withdrawal lock order; recheck a current row before any access ticket can be issued.
        owners.require(ownerModule,entityType,entityId,material.getTypeCode(),false,true);
        var rows=materialMapper.selectMaterialsForUpdate(new DeliveryMaterialIdLockQuery(TenantContextHolder.getRequiredTenantId(),List.of(materialId)));
        if(rows.size()!=1 || !belongsTo(rows.getFirst(),ownerModule,entityType,entityId)
                || !DeliveryMaterialDO.STATUS_ACTIVE.equals(rows.getFirst().getStatus()) || !"FILE".equals(rows.getFirst().getMaterialKind()))
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE","Material unavailable");
        material=rows.getFirst();
        var document=evidence.inspectDocument(TenantContextHolder.getRequiredTenantId(),material.getFileReferenceId());
        if(document==null || !document.available() || !Objects.equals(material.getFileArtifactId(),document.artifactId())
                || !Objects.equals(material.getFileVersionNo(),document.versionNo()) || !Objects.equals(material.getFileSha256(),document.sha256())
                || !ownerModule.equals(document.ownerContext()) || !entityType.equals(document.objectType()) || !entityId.toString().equals(document.objectId()))
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE","File unavailable");
        return tickets.create(new FileAccessTicketService.AccessCommand(TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),
                document.artifactId(),document.versionNo(),FileActionCodes.DOWNLOAD,document.ownerContext(),document.objectType(),document.objectId(),
                document.purposeCode(),document.referenceKey())).getShortLivedUrl();
    }

    private boolean belongsTo(DeliveryMaterialDO material,String module,String type,Long id) {
        return material!=null && TenantContextHolder.getRequiredTenantId().equals(material.getTenantId())
                && ((module.equals(material.getSourceOwnerModule()) && type.equals(material.getSourceEntityType()) && id.equals(material.getSourceEntityId()))
                || (module.equals(material.getOwnerModule()) && type.equals(material.getEntityType()) && id.equals(material.getEntityId())));
    }
}
