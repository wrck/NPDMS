package cn.iocoder.yudao.module.pms.platform.api.entity;

import java.util.Map;
import java.util.List;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;

/** A layout binding, not a business data instance or a business revision relation. */
public interface EntityFormApi {
    Binding read(EntityDataRef target, EntityActor actor);
    Layout layout(EntityDataRef target, EntityActor actor);
    Binding bind(Bind command);
    void copy(EntityDataRef source, EntityDataRef target, Integer expectedTargetVersion, EntityActor actor);

    record Binding(Long formRevisionId, Long extensionDefinitionRevisionId, Map<String, String> fieldBindings, int version) {}
    record Bind(EntityDataRef target, EntityActor actor, Integer expectedEntityVersion,
                int expectedBindingVersion, Long formRevisionId, Long extensionDefinitionRevisionId,
                Map<String, String> fieldBindings) {}
    record Layout(Binding binding, Long templateId, int revisionNo, int formVersion,
                  String engineCode, String designerVersion, String rendererVersion,
                  String formConfJson, String formRulesJson, List<DynamicFormFieldDescriptor> fields) {}
}
