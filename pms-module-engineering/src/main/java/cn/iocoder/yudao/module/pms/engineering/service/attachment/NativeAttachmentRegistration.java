package cn.iocoder.yudao.module.pms.engineering.service.attachment;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/** Called only by native Owner commands inside their transaction. */
@Service @RequiredArgsConstructor
public class NativeAttachmentRegistration {
    private final NativeAttachmentAccess access;
    private final FileArtifactApi files;
    private final PlatformDeliveryMaterialApi materials;
    public record Registered(Long materialId,FileArtifactVersionFact file,boolean newlyRegistered) { }
    public void requireLegacyUnchanged(String existing,String requested) {
        if(requested!=null && !Objects.equals(existing==null?"":existing,requested))
            throw new BusinessContractException("NATIVE_ATTACHMENT_LEGACY_WRITE_DENIED","Use the common native file upload; legacy URLs remain read-only");
    }
    public void requireSameProject(NativeAttachmentKind kind,Long id,Long existing,Long requested) {
        if(!Objects.equals(existing,requested) && !materials.listByEntity(kind.getModule(),kind.getEntityType(),id).isEmpty())
            throw new BusinessContractException("NATIVE_ATTACHMENT_PROJECT_IMMUTABLE","Collected native materials retain their actual project Owner");
    }
    public List<Registered> register(NativeAttachmentKind kind,Long id){
        access.require(kind,TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),id.toString(),kind.getPurpose(),
                FileActionCodes.UPLOAD,true,null);
        var registeredIds=new java.util.HashSet<Long>(materials.listByEntity(kind.getModule(),kind.getEntityType(),id).stream()
                .map(PlatformDeliveryMaterialApi.DeliveryMaterialView::id).toList());
        var key=new FileReferenceSetKey(kind.getModule(),kind.getEntityType(),id.toString(),kind.getPurpose());
        var seen=files.inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(key),FileActionCodes.READ));
        var locked=files.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(seen.stream()
                .map(set->new FileReferenceSetExpectation(set.key(),set.scopeVersion(),set.activeFacts())).toList(),FileActionCodes.READ));
        return locked.stream().flatMap(set->set.activeFacts().stream()).map(file->{Long materialId=materials.registerNativeSourceFile(file);return new Registered(materialId,file,registeredIds.add(materialId));}).toList();
    }
}
