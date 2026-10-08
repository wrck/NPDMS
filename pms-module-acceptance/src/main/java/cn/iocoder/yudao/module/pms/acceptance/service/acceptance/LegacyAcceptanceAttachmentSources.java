package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class LegacyAcceptanceAttachmentSources implements FileDocumentSourceProvider {
    public static final String SOURCE = "ACC.LEGACY_ACCEPTANCE_ATTACHMENT";
    private final AcceptanceMapper rows;
    @Override public List<Descriptor> descriptors(){return List.of(new Descriptor(SOURCE,"旧验收附件"));}
    @Override public Scope resolve(Long tenant,String owner,String type,String object,String purpose){
        if(!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()) || !"ACC".equals(owner)
                || !LegacyAcceptanceAttachmentFilePolicy.TYPE.equals(type) || !LegacyAcceptanceAttachmentFilePolicy.PURPOSE.equals(purpose))return null;
        Long id;try{id=Long.valueOf(object);}catch(RuntimeException invalid){return null;}if(id<=0)return null;
        var row=rows.selectById(id);if(row==null||!Objects.equals(tenant,row.getTenantId()))return null;
        return new Scope(row.getProjectId(),SOURCE,"ACC","acceptance",row.getId(),null);
    }
}
