package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;

import java.util.List;

/**
 * 项目模板基座 Service（F-PM03 / PM-03）
 * <p>
 * 供给端：模板身份维护、草稿内容编辑、发布校验与版本冻结（BR-2/BR-3）、
 * 四维匹配预演（BR-4）、停用（BR-5）与系统保留编码保护（BR-8）。
 * 消费端（项目创建实例化）属 F-PM01，不在本接口范围。
 */
public interface ProjectTemplateService {

    /**
     * 创建模板（生成 DRAFT 草稿工作副本，revision_no=0）
     *
     * @return 模板编号
     */
    Long createProjectTemplate(ProjectTemplateDO template);

    /**
     * 编辑模板身份字段（名称/优先级/描述）；编码不可修改，RETIRED 模板身份冻结
     */
    void updateProjectTemplateIdentity(Long id, String name, Integer matchPriority, String description);

    /**
     * 编辑模板草稿内容（四维条件+流程引用+六类定义行整体替换）；仅 DRAFT 版本可编辑（BR-3）
     */
    void updateProjectTemplateDraftContent(Long templateId, TemplateDefinitionContent content);

    /**
     * 删除模板：仅无 PUBLISHED 版本且非系统保留（BR-8/留痕）
     */
    void deleteProjectTemplate(Long id);

    /**
     * 分页查询模板（状态/编码/名称过滤，优先级升序）
     */
    PageResult<ProjectTemplateDO> getProjectTemplatePage(ProjectTemplatePageReqVO pageReqVO);

    /**
     * 查询模板
     */
    ProjectTemplateDO getProjectTemplate(Long id);

    /**
     * 查询模板全部版本（版本号倒序）
     */
    List<ProjectTemplateRevisionDO> getRevisionList(Long templateId);

    /**
     * 查询指定版本（已发布版本只读）
     */
    ProjectTemplateRevisionDO getRevision(Long templateId, Integer revisionNo);

    /**
     * 按稳定ID查询指定模板版本。
     */
    ProjectTemplateRevisionDO getRevisionById(Long revisionId);

    /**
     * 读取模板草稿内容（含六类定义行）
     */
    TemplateDefinitionContent getDraftContent(Long templateId);

    /**
     * 读取指定版本完整内容（版本快照只读）
     */
    TemplateDefinitionContent getRevisionContent(Long templateId, Integer revisionNo);

    /**
     * 发布：校验（BR-2）→ 冻结新 PUBLISHED 版本（revision_no 递增）→ 模板转 ACTIVE；
     * 校验失败保持草稿并抛出失败项（重试用原版本号）
     */
    void publishProjectTemplate(Long id);

    /**
     * 停用：仅 ACTIVE 可停用；只阻新项目匹配，不解除已建项目绑定（BR-5）
     */
    void disableProjectTemplate(Long id);

    /**
     * 四维匹配预演：唯一命中返回模板；无匹配/同优先级多匹配返回冲突清单（BR-4）
     */
    TemplateMatchResult matchPreview(String signingMethod, String projectCategory,
                                      String implementationMethod, String majorProjectLevel);
}
