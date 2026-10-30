package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;

/** Initial Owner configuration from the project's exact publication, independent of node admission. */
public interface ProjectBusinessConfigurationApi {
    Configuration resolve(Query query);

    record Query(Long tenantId, Long actorId, Long projectId, ProjectWorkBindingTarget target) { }

    /** Parameters belong to the Owner; no runtime node identity or permanent authorization is returned. */
    record Configuration(Long tenantId, Long projectId, Long projectTemplateId, Long templateRevisionId,
                         Integer templateRevisionNo, Long dynamicFormRevisionId, String parameters) { }
}
