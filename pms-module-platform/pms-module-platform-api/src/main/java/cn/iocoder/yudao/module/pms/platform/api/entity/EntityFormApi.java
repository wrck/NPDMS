package cn.iocoder.yudao.module.pms.platform.api.entity;

import java.util.Map;
import java.util.List;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;

/** A layout binding, not a business data instance or a business revision relation. */
public interface EntityFormApi {
    Binding read(EntityDataRef target, EntityActor actor);
    Layout layout(EntityDataRef target, EntityActor actor);
    Binding bind(Bind command);
    void copy(EntityDataRef source, EntityDataRef target, Long expectedTargetVersion, EntityActor actor);

    record Binding(Long formRevisionId, Long extensionDefinitionRevisionId, Map<String, String> fieldBindings, int version) {}
    /** Published layouts that supplied edited fields before the first binding was saved. */
    record DefinitionSource(Long formRevisionId, Map<String,String> fieldBindings) { }
    record Bind(EntityDataRef target, EntityActor actor, Long expectedEntityVersion,
                int expectedBindingVersion, Long formRevisionId, Long extensionDefinitionRevisionId,
                Map<String, String> fieldBindings, boolean bindRemainingFields,boolean preserveExistingFields,
                List<DefinitionSource> definitionSources) {
        public Bind { definitionSources=definitionSources==null?List.of():List.copyOf(definitionSources); }
        public Bind(EntityDataRef target,EntityActor actor,Long expectedEntityVersion,int expectedBindingVersion,
                    Long formRevisionId,Long extensionDefinitionRevisionId,Map<String,String> fieldBindings,
                    boolean bindRemainingFields,boolean preserveExistingFields){
            this(target,actor,expectedEntityVersion,expectedBindingVersion,formRevisionId,extensionDefinitionRevisionId,
                    fieldBindings,bindRemainingFields,preserveExistingFields,List.of());
        }
        public Bind(EntityDataRef target,EntityActor actor,Long expectedEntityVersion,int expectedBindingVersion,
                    Long formRevisionId,Long extensionDefinitionRevisionId,Map<String,String> fieldBindings,boolean bindRemainingFields){
            this(target,actor,expectedEntityVersion,expectedBindingVersion,formRevisionId,extensionDefinitionRevisionId,fieldBindings,bindRemainingFields,false);
        }
        public Bind(EntityDataRef target, EntityActor actor, Long expectedEntityVersion,
                    int expectedBindingVersion, Long formRevisionId, Long extensionDefinitionRevisionId,
                    Map<String, String> fieldBindings) {
            this(target, actor, expectedEntityVersion, expectedBindingVersion, formRevisionId,
                    extensionDefinitionRevisionId, fieldBindings, false);
        }
    }
    record Layout(Binding binding, Long templateId, int revisionNo, int formVersion,
                  String engineCode, String designerVersion, String rendererVersion,
                  String formConfJson, String formRulesJson, List<DynamicFormFieldDescriptor> fields) {}
}
