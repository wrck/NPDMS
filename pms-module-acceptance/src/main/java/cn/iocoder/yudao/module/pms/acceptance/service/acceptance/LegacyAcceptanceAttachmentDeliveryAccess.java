package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class LegacyAcceptanceAttachmentDeliveryAccess implements DeliveryMaterialUploadPolicyValidator {
 private final LegacyAcceptanceAttachmentFilePolicy policy;
 @Override public String ownerModule(){return "ACC";}@Override public boolean supportsEntityType(String type){return "acceptance".equals(type);}
 @Override public boolean allowsGenericDeliveryActions(String type){return false;}
 @Override public Long requireDeliveryAccess(Long tenant,Long actor,String type,String id,String purpose,boolean write,boolean lock,Long expected){
  if(!supportsEntityType(type)||write&&!LegacyAcceptanceAttachmentSources.SOURCE.equals(purpose))throw denied();
  return policy.require(tenant,actor,id,LegacyAcceptanceAttachmentFilePolicy.PURPOSE,write?FileActionCodes.UPLOAD:FileActionCodes.READ,lock,expected).scopeVersion();
 }
 @Override public FileBusinessObjectPolicyFact validateUpload(Long tenant,Long actor,String type,String id,String purpose,String action,boolean lock,Long expected){throw denied();}
 private static BusinessContractException denied(){return new BusinessContractException("LEGACY_ACCEPTANCE_NATIVE_COMMAND_REQUIRED","Use actual legacy native attachment Owner; report/template materials are independent");}
}
