package cn.iocoder.yudao.module.pms.engineering.service.attachment.supplemental;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;import java.util.*;
@Component @RequiredArgsConstructor
public class SupplementalAttachmentSources implements FileDocumentSourceProvider {
 private final SupplementalAttachmentAccess access;
 @Override public List<Descriptor> descriptors(){return Arrays.stream(SupplementalAttachmentKind.values()).map(k->new Descriptor(k.source(),k.getTitle())).toList();}
 @Override public Scope resolve(Long tenant,String module,String type,String id,String purpose){
  var kind=Arrays.stream(SupplementalAttachmentKind.values()).filter(k->k.getModule().equals(module)&&k.getType().equals(type)&&k.getPurpose().equals(purpose)).findFirst().orElse(null);
  if(kind==null||!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()))return null;
  Long key;try{key=Long.valueOf(id);}catch(RuntimeException invalid){return null;}if(key<=0)return null;
  var row=access.owner(kind,tenant,key,false);return row==null||!Objects.equals(tenant,row.tenant())?null:new Scope(row.project(),kind.source(),module,type,key,null);
 }
}
