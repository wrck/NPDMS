package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Validation;

import java.util.List;

/** Project template supply service. V2 runtime is Designer -> Compiler -> ExecutionSnapshot. */
public interface ProjectTemplateService {

    Validation validateProjectTemplate(Long id);

    Long copyProjectTemplate(Long id, Integer expectedVersion,
            cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateCopyReqVO body,
            String idempotencyKey);

    Long createProjectTemplate(ProjectTemplateDO template);

    void updateProjectTemplateIdentity(Long id, String name, Integer matchPriority, String description);

    /** V2 authoring write. New callers should use this method. */
    void updateProjectTemplateDesigner(Long templateId, TemplateDesignerDocument designer);

    /** V2 authoring read. Legacy drafts are imported in-memory without mutating history. */
    TemplateDesignerDocument getDraftDesigner(Long templateId);

    /** Immutable V2 runtime truth; legacy published rows are adapted without rewriting them. */
    TemplateExecutionSnapshot getExecutionSnapshot(Long templateId, Integer revisionNo);

    /**
     * Legacy compatibility adapter for existing clients. The service converts this payload to the
     * V2 designer and no longer treats legacy element rows as the new authoring truth.
     */
    void updateProjectTemplateDraftContent(Long templateId, TemplateDefinitionContent content);

    void deleteProjectTemplate(Long id);

    PageResult<ProjectTemplateDO> getProjectTemplatePage(ProjectTemplatePageReqVO pageReqVO);

    ProjectTemplateDO getProjectTemplate(Long id);

    List<ProjectTemplateRevisionDO> getRevisionList(Long templateId);

    ProjectTemplateRevisionDO getRevision(Long templateId, Integer revisionNo);

    ProjectTemplateRevisionDO getRevisionById(Long revisionId);

    /** Legacy compatibility projection generated from Designer/Snapshot when V2 is present. */
    TemplateDefinitionContent getDraftContent(Long templateId);

    /** Legacy compatibility projection generated from Designer/Snapshot when V2 is present. */
    TemplateDefinitionContent getRevisionContent(Long templateId, Integer revisionNo);

    void publishProjectTemplate(Long id);

    void disableProjectTemplate(Long id);

    TemplateMatchResult matchPreview(String signingMethod, String projectCategory,
                                      String implementationMethod, String majorProjectLevel);
}
