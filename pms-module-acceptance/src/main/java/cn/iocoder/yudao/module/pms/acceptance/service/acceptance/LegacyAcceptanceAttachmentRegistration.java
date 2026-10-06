package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.*;import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.*;
/** Authorized maintenance of the legacy acceptance Owner; never maps to acceptanceActivity. */
@Service @RequiredArgsConstructor
public class LegacyAcceptanceAttachmentRegistration {
 private final LegacyAcceptanceAttachmentFilePolicy policy;private final FileArtifactApi files;private final PlatformDeliveryMaterialApi materials;
 public void sameProject(Long id,Long previous,Long requested){if(!Objects.equals(previous,requested)&&!materials.listByEntity("ACC","acceptance",id).isEmpty())throw new BusinessContractException("LEGACY_ACCEPTANCE_PROJECT_IMMUTABLE","Collected materials retain their actual Owner project");}
 public void register(Long id,boolean submit){
  policy.require(TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),id.toString(),LegacyAcceptanceAttachmentFilePolicy.PURPOSE,FileActionCodes.UPLOAD,true,null,submit);
  var key=new FileReferenceSetKey("ACC",LegacyAcceptanceAttachmentFilePolicy.TYPE,id.toString(),LegacyAcceptanceAttachmentFilePolicy.PURPOSE);
  var seen=files.inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(key),FileActionCodes.READ));
  var locked=files.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(seen.stream().map(s->new FileReferenceSetExpectation(s.key(),s.scopeVersion(),s.activeFacts())).toList(),FileActionCodes.READ));
  locked.forEach(s->s.activeFacts().forEach(materials::registerNativeSourceFile));
 }
}
