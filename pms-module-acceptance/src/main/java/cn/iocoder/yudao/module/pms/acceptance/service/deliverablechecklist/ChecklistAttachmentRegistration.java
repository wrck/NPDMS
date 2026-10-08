package cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist;

import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** Called inside the native Owner save/submit transaction, never inside global upload completion. */
@Service @RequiredArgsConstructor
public class ChecklistAttachmentRegistration {
    private final FileArtifactApi files;
    private final PlatformDeliveryMaterialApi materials;
    public void register(Long id){
        var key=new FileReferenceSetKey("ACC",ChecklistAttachmentFilePolicy.TYPE,id.toString(),ChecklistAttachmentFilePolicy.PURPOSE);
        var seen=files.inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(key),FileActionCodes.READ));
        var locked=files.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(seen.stream()
                .map(set->new FileReferenceSetExpectation(set.key(),set.scopeVersion(),set.activeFacts())).toList(),FileActionCodes.READ));
        locked.forEach(set->set.activeFacts().forEach(materials::registerNativeSourceFile));
    }
}
