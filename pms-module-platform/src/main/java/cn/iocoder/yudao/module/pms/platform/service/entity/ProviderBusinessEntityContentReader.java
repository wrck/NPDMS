package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntityIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** One bridge for all explicit native identities; no Owner-name branches or second field catalog. */
@Component
@RequiredArgsConstructor
public class ProviderBusinessEntityContentReader implements BusinessEntityContentReader {
    private final BusinessEntityIdentityResolver identities;
    private final EntityProviderRegistry registry;
    private final BusinessModelCatalog catalog;
    private final EntityExtensionApi extensions;

    public boolean supports(String owner, String type) { return identities.hasMapping(owner,type); }
    public BusinessEntityData read(EntityDataRef target, EntityActor actor) {
        var nativeTarget=registry.nativeRef(target);
        var provider=registry.fields(nativeTarget.entity());
        registry.requireReadable(nativeTarget,actor);
        var descriptor=catalog.require(target.entity().ownerModule(),target.entity().entityType());
        Map<String,Object> values=new LinkedHashMap<>();
        var facts=provider.read(nativeTarget,actor);
        descriptor.fields().stream().filter(BusinessFieldDescriptor::readable).forEach(field -> {
            var fact=facts.get(field.code());
            if (fact != null && fact.readable()) values.put(field.code(),fact.value());
        });
        if (descriptor.capabilities().stream().anyMatch(cap -> cap.enabled() && cap.type()==BusinessCapabilityType.DYNAMIC_FORM))
            extensions.read(nativeTarget,actor).fields().forEach((code,value) -> {
                if (descriptor.fields().stream().noneMatch(field -> field.code().equals(code))) values.put(code,value);
            });
        return new BusinessEntityData(target.entity(),target.revisionId(),Collections.unmodifiableMap(values),
                provider.concurrencyBasis(nativeTarget,actor),true,null);
    }
}
