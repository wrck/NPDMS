package cn.iocoder.yudao.module.pms.engineering.service.attachment.supplemental;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class BriefingAttachmentDeliveryAccess implements DeliveryMaterialUploadPolicyValidator {
 private final SupplementalAttachmentAccess access;private static final SupplementalAttachmentKind KIND=SupplementalAttachmentKind.BRIEFING;
 @Override public String ownerModule(){return KIND.getModule();}
 @Override public boolean supportsEntityType(String type){return KIND.getType().equals(type);}
 @Override public boolean allowsGenericDeliveryActions(String type){return false;}
 @Override public Long requireDeliveryAccess(Long tenant,Long actor,String type,String id,String purpose,boolean write,boolean lock,Long expected){
  if(!supportsEntityType(type)||write&&!KIND.source().equals(purpose))throw denied();
  return access.require(KIND,tenant,actor,id,KIND.getPurpose(),write?FileActionCodes.UPLOAD:FileActionCodes.READ,lock,expected,false).scopeVersion();
 }
 @Override public FileBusinessObjectPolicyFact validateUpload(Long tenant,Long actor,String type,String id,String purpose,String action,boolean lock,Long expected){throw denied();}
 private static BusinessContractException denied(){return new BusinessContractException("SUPPLEMENTAL_NATIVE_COMMAND_REQUIRED","Use the native attachment Owner; generated snapshots and catalog slots are independent");}
}
