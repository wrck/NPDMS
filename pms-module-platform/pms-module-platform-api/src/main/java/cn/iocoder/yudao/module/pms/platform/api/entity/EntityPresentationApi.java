package cn.iocoder.yudao.module.pms.platform.api.entity;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;
import java.util.List;

/** Read-only presentation catalog. It never binds a form or defines business fields. */
public interface EntityPresentationApi {
    List<Presentation> list(Query query);

    record Query(EntityDataRef target, EntityActor actor, String categoryCode) { }
    record Presentation(Long templateId, String name, Long revisionId, int revisionNo, int version,
                        String engineCode, String designerVersion, String rendererVersion,
                        String formConfJson, String formRulesJson, List<DynamicFormFieldDescriptor> fields) { }
}
