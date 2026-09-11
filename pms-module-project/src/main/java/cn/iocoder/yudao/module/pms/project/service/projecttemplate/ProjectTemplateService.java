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

    /** V2 authoring write. Implemented by the primary V2 service. */
    default void updateProjectTemplateDesigner(Long templateId, TemplateDesignerDocument designer) {
        throw new UnsupportedOperationException("template designer v2 is not available on the legacy service");
    }

    /** V2 authoring read. */
    default TemplateDesignerDocument getDraftDesigner(Long templateId) {
        throw new UnsupportedOperationException("template designer v2 is not available on the legacy service");
    }

    /** Immutable V2 runtime truth. */
    default TemplateExecutionSnapshot getExecutionSnapshot(Long templateId, Integer revisionNo) {
        throw new UnsupportedOperationException("template execution snapshot v2 is not available on the legacy service");
    }

    /** Legacy compatibility adapter for existing clients. */
    void updateProjectTemplateDraftContent(Long templateId, TemplateDefinitionContent content);

    void deleteProjectTemplate(Long id);

    PageResult<ProjectTemplateDO> getProjectTemplatePage(ProjectTemplatePageReqVO pageReqVO);

    ProjectTemplateDO getProjectTemplate(Long id);

    List<ProjectTemplateRevisionDO> getRevisionList(Long templateId);

    ProjectTemplateRevisionDO getRevision(Long templateId, Integer revisionNo);

    ProjectTemplateRevisionDO getRevisionById(Long revisionId);

    TemplateDefinitionContent getDraftContent(Long templateId);

    TemplateDefinitionContent getRevisionContent(Long templateId, Integer revisionNo);

    void publishProjectTemplate(Long id);

    void disableProjectTemplate(Long id);

    TemplateMatchResult matchPreview(String signingMethod, String projectCategory,
                                      String implementationMethod, String majorProjectLevel);
}
