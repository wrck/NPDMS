package cn.iocoder.yudao.module.pms.platform.service.file;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryMaterialService;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class BusinessGrantGeneratedFileService implements BusinessGrantGeneratedFileApi {
 private final List<BusinessGrantGeneratedFilePolicyProvider> owners;private final FileUploadApplicationService uploads;
 private final DeliveryMaterialService materials;private final OperationAuditApi audit;
 @Override @Transactional(rollbackFor=Exception.class)
 public RegisteredFile create(BusinessGrantGeneratedFileCommand c){
  if(c==null||!Objects.equals(c.tenantId(),TenantContextHolder.getRequiredTenantId()))throw denied();
  var policy=policy(c,null);byte[] bytes=c.content();String hash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
  String slot="grant:"+c.grantId()+":"+hash;
  var init=uploads.initializeAuthorized(new FileUploadInitializeCommand(c.tenantId(),policy.executionUserId(),c.operationId(),
   "CREATE_ARTIFACT",null,null,c.ownerContext(),c.objectType(),String.valueOf(c.objectId()),c.purposeCode(),slot,c.fileName(),
   c.categoryCode(),(long)bytes.length,c.mediaType(),hash),policy.filePolicy());
  var checked=policy(c,policy.filePolicy().scopeVersion());
  if(!Objects.equals(policy.executionUserId(),checked.executionUserId()))throw denied();
  var completed=uploads.completeAuthorized(new FileUploadCompleteCommand(c.tenantId(),checked.executionUserId(),c.operationId(),
   init.artifactId(),init.sessionId(),null,hash),bytes,checked.filePolicy());
  var material=materials.registerNativeGeneratedDocument(completed.referenceId());
  audit.record(c.tenantId(),checked.executionUserId(),c.operationId(),"BUSINESS_GRANT_DOCUMENT_GENERATED",c.objectType(),String.valueOf(c.objectId()),"COMPLETED",
   Map.of("subjectType","BUSINESS_GRANT","channel","PUBLIC_LINK","grantId",c.grantId(),"issuanceVersion",c.issuanceVersion(),
    "executionUserId",checked.executionUserId(),"materialId",material.getId(),"artifactId",completed.artifactId(),"fileVersion",completed.versionNo()));
  return new RegisteredFile(material.getId(),completed.referenceId(),completed.artifactId(),completed.versionNo(),completed.sha256(),c.fileName(),c.grantId(),c.issuanceVersion(),checked.executionUserId());
 }
 private BusinessGrantGeneratedFilePolicyProvider.Policy policy(BusinessGrantGeneratedFileCommand c,Long scope){
  var matches=owners.stream().filter(p->c.ownerContext().equals(p.ownerContext())&&c.objectType().equals(p.objectType())).toList();
  if(matches.size()!=1)throw denied();
  var p=matches.getFirst().lockAndRevalidate(new BusinessGrantGeneratedFilePolicyQuery(c.tenantId(),c.ownerContext(),c.objectType(),c.objectId(),
   c.expectedOwnerVersion(),c.grantId(),c.issuanceVersion(),c.purposeCode(),scope));
  if(p==null||!Objects.equals(c.grantId(),p.grantId())||!Objects.equals(c.issuanceVersion(),p.issuanceVersion())
   ||p.executionUserId()==null||p.executionUserId()<=0||p.filePolicy()==null||!p.filePolicy().allowed()
   ||p.filePolicy().scopeVersion()==null||scope!=null&&!scope.equals(p.filePolicy().scopeVersion()))throw denied();
  return p;
 }
 private static BusinessContractException denied(){return new BusinessContractException("BUSINESS_GRANT_DOCUMENT_DENIED","Explicit native business grant and execution attribution required");}
}
