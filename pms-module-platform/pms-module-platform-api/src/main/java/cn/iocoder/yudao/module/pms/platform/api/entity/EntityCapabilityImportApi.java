package cn.iocoder.yudao.module.pms.platform.api.entity;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;
import java.util.List;
import java.util.Map;

/** Insert-only legacy handover, invoked inside the owning business module's object transaction. */
public interface EntityCapabilityImportApi {
    SourceForm inspect(Source source, EntityActor actor);
    void importContent(Import command);
    void importFiles(RevisionRef target, Source source, String fileObjectType, EntityActor actor);

    record Source(EntityRef legacyOwner, Long instanceId, Long formRevisionId) {}
    record SourceForm(Long formRevisionId, List<DynamicFormFieldDescriptor> fields) {}
    record Import(EntityDataRef target, Source source, Map<String, String> fixedBindings,
                  Map<String, Object> extensionValues, EntityActor actor) {}
}
