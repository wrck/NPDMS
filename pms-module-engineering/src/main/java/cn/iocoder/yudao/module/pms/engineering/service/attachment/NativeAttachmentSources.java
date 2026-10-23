package cn.iocoder.yudao.module.pms.engineering.service.attachment;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class NativeAttachmentSources implements FileDocumentSourceProvider {
    private final NativeAttachmentOwners owners;
    @Override public List<Descriptor> descriptors(){return Arrays.stream(NativeAttachmentKind.values()).map(k->new Descriptor(k.sourceCode(),k.getTitle())).toList();}
    @Override public Scope resolve(Long tenant,FileEvidenceApi.Document document){
        String module=document.ownerContext(),type=document.objectType(),object=document.objectId(),purpose=document.purposeCode();
        var kind=NativeAttachmentKind.find(module,type);
        if(kind==null||!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId())||!kind.getPurpose().equals(purpose))return null;
        Long id;try{id=Long.valueOf(object);}catch(RuntimeException invalid){return null;}if(id<=0)return null;
        var row=owners.find(kind,tenant,id,false);if(row==null||!Objects.equals(tenant,row.tenantId()))return null;
        return new Scope(row.projectId(),kind.sourceCode(),module,type,id,null);
    }
}
