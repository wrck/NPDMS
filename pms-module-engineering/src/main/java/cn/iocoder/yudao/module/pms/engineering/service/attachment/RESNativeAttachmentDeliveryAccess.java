package cn.iocoder.yudao.module.pms.engineering.service.attachment;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class RESNativeAttachmentDeliveryAccess implements DeliveryMaterialUploadPolicyValidator {
    private final NativeAttachmentAccess access;
    @Override public String ownerModule(){return "RES";}
    @Override public boolean supportsEntityType(String type){return NativeAttachmentKind.find(ownerModule(),type)!=null;}
    @Override public Long requireDeliveryAccess(Long tenant,Long actor,String type,String id,String purpose,boolean write,boolean lock,Long expected){
        var kind=NativeAttachmentKind.find(ownerModule(),type);
        if(kind==null || write&&!kind.sourceCode().equals(purpose))throw new BusinessContractException("NATIVE_ATTACHMENT_ACCESS_DENIED","Native source type required");
        return access.require(kind,tenant,actor,id,kind.getPurpose(),write?FileActionCodes.UPLOAD:FileActionCodes.READ,lock,expected).scopeVersion();
    }
    @Override public FileBusinessObjectPolicyFact validateUpload(Long tenant,Long actor,String type,String id,String purpose,String action,boolean lock,Long expected){
        throw new BusinessContractException("NATIVE_ATTACHMENT_COMMAND_REQUIRED","Use the actual native Owner file key");
    }
}
