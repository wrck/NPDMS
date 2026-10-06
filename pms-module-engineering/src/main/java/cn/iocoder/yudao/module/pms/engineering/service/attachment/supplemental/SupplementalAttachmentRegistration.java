package cn.iocoder.yudao.module.pms.engineering.service.attachment.supplemental;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.*;
@Service @RequiredArgsConstructor
public class SupplementalAttachmentRegistration {
 private final SupplementalAttachmentAccess access;private final FileArtifactApi files;private final PlatformDeliveryMaterialApi materials;
 public void unchanged(String previous,String requested){if(requested!=null&&!Objects.equals(previous==null?"":previous,requested))throw new BusinessContractException("SUPPLEMENTAL_LEGACY_URL_WRITE_DENIED","Use the common native attachment upload; legacy metadata remains read-only");}
 public void unchangedMetadata(Object previous,Object requested){
  if(requested!=null&&!Objects.equals(previous,requested)&&!(previous==null&&requested instanceof String value&&value.isBlank()))
   throw new BusinessContractException("SUPPLEMENTAL_FILE_METADATA_WRITE_DENIED","Manual attachment commands cannot replace legacy or generated file metadata");
 }
 public void sameProject(SupplementalAttachmentKind kind,Long id,Long previous,Long requested){if(!Objects.equals(previous,requested)&&!materials.listByEntity(kind.getModule(),kind.getType(),id).isEmpty())throw new BusinessContractException("SUPPLEMENTAL_ATTACHMENT_PROJECT_IMMUTABLE","Actual collected Owner project cannot change");}
 public void register(SupplementalAttachmentKind kind,Long id,boolean freeze){
  access.require(kind,TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),id.toString(),kind.getPurpose(),FileActionCodes.UPLOAD,true,null,freeze);
  var key=new FileReferenceSetKey(kind.getModule(),kind.getType(),id.toString(),kind.getPurpose());
  var observed=files.inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(key),FileActionCodes.READ));
  var locked=files.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(observed.stream().map(s->new FileReferenceSetExpectation(s.key(),s.scopeVersion(),s.activeFacts())).toList(),FileActionCodes.READ));
  locked.forEach(s->s.activeFacts().forEach(materials::registerNativeSourceFile));
 }
}
