package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.Objects;

@Component @RequiredArgsConstructor
public class DeliveryDocumentOriginResolver {
    private final ObjectProvider<FileDocumentSourceProvider> providers;
    private final DeliveryOwnerAccess owners;
    public FileDocumentSourceProvider.Scope resolve(FileEvidenceApi.Document document) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        var matches=providers.orderedStream().map(provider->provider.resolve(tenant,document.ownerContext(),
                document.objectType(),document.objectId(),document.purposeCode())).filter(Objects::nonNull).toList();
        if(matches.size()>1)throw new BusinessContractException("DELIVERY_SOURCE_AMBIGUOUS","文件匹配多个原生业务来源");
        if(!matches.isEmpty())return matches.getFirst();
        if("PLT".equals(document.ownerContext()) && "DELIVERY_MATERIAL".equals(document.objectType())) {
            var key=document.objectId().split(":",3);
            if(key.length!=3)return null;
            Long id;
            try { id=Long.valueOf(key[2]); } catch(NumberFormatException invalid){return null;}
            Long project=owners.projectId(key[0],key[1],id);
            // 模板根代表项目交付业务类型；purposeCode仍是要求编码，不能充当分类。
            String classification="ACC".equals(key[0]) && "project_deliverable".equals(key[1])
                    ? key[0]+"/"+key[1] : document.purposeCode();
            return new FileDocumentSourceProvider.Scope(project,classification,key[0],key[1],id,null);
        }
        return null;
    }
}
