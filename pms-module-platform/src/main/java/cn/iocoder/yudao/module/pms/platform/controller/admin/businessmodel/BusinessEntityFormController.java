package cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.capability.DeclaredBusinessCapabilityAdapterFactory;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Common layout/definition access; business values still save through the shared operation endpoint. */
@RestController
@RequestMapping("/api/v1/pms/business-models/{ownerModule}/{entityType}")
@Validated
@RequiredArgsConstructor
public class BusinessEntityFormController {
    private final EntityFormApi forms;
    private final EntityExtensionApi extensions;
    private final DeclaredBusinessCapabilityAdapterFactory defaults;
    private final BusinessCallerContext callers;
    public record FormData(EntityFormApi.Layout layout,EntityExtensionApi.Values extensions,List<EntityExtensionApi.Definition> definitions) { }
    public record BindRequest(@NotNull @PositiveOrZero Long expectedEntityVersion,@PositiveOrZero int expectedBindingVersion,
            @NotNull @Positive Long formRevisionId,Long extensionDefinitionRevisionId,Map<String,String> fieldBindings,boolean bindRemainingFields) { }
    private EntityDataRef target(String owner,String type,Long id) {
        var caller=callers.require();var target=EntityDataRef.current(new EntityRef(caller.tenantId(),owner,type,id));
        if(!defaults.supports(target.entity())) throw new BusinessContractException("CAPABILITY_UNAVAILABLE","Declared form capability is unavailable");
        return target;
    }
    private EntityActor actor(){var caller=callers.require();return new EntityActor(caller.tenantId(),caller.userId(),caller.entryCorrelationId());}
    @GetMapping("/form")
    @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    @Transactional(readOnly=true)
    public CommonResult<FormData> read(@PathVariable String ownerModule,@PathVariable String entityType,@RequestParam @Positive Long entityId) {
        var target=target(ownerModule,entityType,entityId);var actor=actor();var values=extensions.read(target,actor);var layout=forms.layout(target,actor);
        Long definition=values.definitionRevisionId();
        if(layout!=null && layout.binding().extensionDefinitionRevisionId()!=null) definition=layout.binding().extensionDefinitionRevisionId();
        return success(new FormData(layout,values,definition==null?List.of():extensions.definition(definition,target.entity(),actor).fields()));
    }
    @PostMapping("/form/binding")
    @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    @Transactional
    public CommonResult<EntityFormApi.Binding> bind(@PathVariable String ownerModule,@PathVariable String entityType,
            @RequestParam @Positive Long entityId,@Valid @RequestBody BindRequest request) {
        return success(forms.bind(new EntityFormApi.Bind(target(ownerModule,entityType,entityId),actor(),request.expectedEntityVersion(),
                request.expectedBindingVersion(),request.formRevisionId(),request.extensionDefinitionRevisionId(),request.fieldBindings(),request.bindRemainingFields())));
    }
}
