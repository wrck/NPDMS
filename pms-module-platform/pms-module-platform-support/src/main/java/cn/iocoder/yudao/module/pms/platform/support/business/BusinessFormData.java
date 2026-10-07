package cn.iocoder.yudao.module.pms.platform.support.business;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import java.util.List;
/** Existing layout and extensions are one inherited presentation, independent of a form instance. */
public record BusinessFormData(EntityFormApi.Layout layout, EntityExtensionApi.Values extensions,
        List<EntityExtensionApi.Definition> definitions) {
    public record BindingPatch(int expectedVersion,Long formRevisionId,Long extensionDefinitionRevisionId,
            java.util.Map<String,String> fieldBindings,boolean bindRemainingFields) { }
}
