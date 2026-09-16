package cn.iocoder.yudao.module.pms.platform.api.entity;

import java.util.List;
import java.util.Map;

/** Values and their immutable definition revision are independent of form instances. */
public interface EntityExtensionApi {
    Values read(EntityDataRef target, EntityActor actor);
    Values save(Save command);
    void validateComplete(EntityDataRef target, EntityActor actor);
    void copy(EntityDataRef source, EntityDataRef target, Integer expectedTargetVersion, EntityActor actor);

    record Definition(String code, String label, EntityField.Type type, boolean required,
                      Integer maxLength, List<String> allowedValues) {}
    record DefinitionRevision(Long id, Long tenantId, String ownerModule, String entityType,
                              int revisionNo, List<Definition> fields) {}
    DefinitionRevision publishDefinition(Long tenantId, String ownerModule, String entityType,
                                         List<Definition> fields, EntityActor actor);
    DefinitionRevision definition(Long id, EntityRef entity, EntityActor actor);
    record Values(Long definitionRevisionId, Map<String, Object> fields, int version) {}
    record Save(EntityDataRef target, EntityActor actor, Integer expectedEntityVersion,
                int expectedValueVersion, Long definitionRevisionId, Map<String, Object> fields) {}
}
