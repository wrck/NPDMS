package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDeliverableDefinitionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateGateDefinitionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateGateReferenceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateMilestoneDefinitionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateStageDefinitionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateTaskDefinitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateDeliverableDefinitionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateGateDefinitionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateGateReferenceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMilestoneDefinitionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateStageDefinitionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateTaskDefinitionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCandidate;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatcher;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplatePublishValidator;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_DELETE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NO_DRAFT_REVISION;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_PUBLISH_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_STATUS_INVALID;

/**
 * 项目模板基座 Service 实现（F-PM03 / PM-03）
 * <p>
 * 草稿即版本：revision_no=0 为草稿工作副本；发布校验通过后递增版本号冻结为
 * PUBLISHED 只读行，模板转 ACTIVE（BR-2/BR-3）。发布失败不落任何发布痕迹，
 * 重试沿用原版本号（BR-5）。
 */
@Service
@Validated
public class ProjectTemplateServiceImpl implements ProjectTemplateService {

    @Resource
    private ProjectTemplateMapper projectTemplateMapper;
    @Resource
    private ProjectTemplateRevisionMapper revisionMapper;
    @Resource
    private ProjectTemplateStageDefinitionMapper stageDefinitionMapper;
    @Resource
    private ProjectTemplateTaskDefinitionMapper taskDefinitionMapper;
    @Resource
    private ProjectTemplateMilestoneDefinitionMapper milestoneDefinitionMapper;
    @Resource
    private ProjectTemplateDeliverableDefinitionMapper deliverableDefinitionMapper;
    @Resource
    private ProjectTemplateGateDefinitionMapper gateDefinitionMapper;
    @Resource
    private ProjectTemplateGateReferenceMapper gateReferenceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProjectTemplate(ProjectTemplateDO template) {
        // BR-1 编码租户内唯一
        if (projectTemplateMapper.selectByCode(template.getCode()) != null) {
            throw exception(PROJECT_TEMPLATE_CODE_DUPLICATE);
        }
        template.setId(null);
        template.setStatus(TemplateRules.STATUS_DRAFT);
        template.setSystemReserved(Boolean.FALSE);
        if (template.getMatchPriority() == null) {
            template.setMatchPriority(100);
        }
        projectTemplateMapper.insert(template);
        // 草稿即版本：创建即生成 DRAFT 工作副本（revision_no=0）
        ProjectTemplateRevisionDO draft = new ProjectTemplateRevisionDO();
        draft.setTemplateId(template.getId());
        draft.setRevisionNo(TemplateRules.DRAFT_REVISION_NO);
        draft.setStatus(TemplateRules.REVISION_STATUS_DRAFT);
        revisionMapper.insert(draft);
        return template.getId();
    }

    @Override
    public void updateProjectTemplateIdentity(Long id, String name, Integer matchPriority, String description) {
        ProjectTemplateDO template = validateTemplateExists(id);
        // RETIRED 模板身份冻结；重新供给需新建模板
        if (!TemplateRules.canEditDraft(template.getStatus(), TemplateRules.REVISION_STATUS_DRAFT)) {
            throw exception(PROJECT_TEMPLATE_STATUS_INVALID);
        }
        ProjectTemplateDO updateObj = new ProjectTemplateDO();
        updateObj.setId(id);
        updateObj.setName(name);
        updateObj.setMatchPriority(matchPriority);
        updateObj.setDescription(description);
        projectTemplateMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectTemplateDraftContent(Long templateId, TemplateDefinitionContent content) {
        ProjectTemplateDO template = validateTemplateExists(templateId);
        ProjectTemplateRevisionDO draft = revisionMapper.selectDraftByTemplateId(templateId);
        if (draft == null) {
            throw exception(PROJECT_TEMPLATE_NO_DRAFT_REVISION);
        }
        // BR-3 已发布版本只读，仅草稿工作副本可编辑
        if (!TemplateRules.canEditDraft(template.getStatus(), draft.getStatus())) {
            throw exception(PROJECT_TEMPLATE_STATUS_INVALID);
        }
        // 四维条件与流程引用（草稿行原地更新）
        ProjectTemplateRevisionDO updateObj = new ProjectTemplateRevisionDO();
        updateObj.setId(draft.getId());
        updateObj.setSigningMethod(content.getSigningMethod());
        updateObj.setProjectCategory(content.getProjectCategory());
        updateObj.setImplementationMethod(content.getImplementationMethod());
        updateObj.setMajorProjectLevel(content.getMajorProjectLevel());
        updateObj.setProcessDefinitionKey(content.getProcessDefinitionKey());
        updateObj.setProcessDefinitionVersion(content.getProcessDefinitionVersion());
        revisionMapper.updateById(updateObj);
        // 定义行整体替换（物理删除+重插，规避 uk 与逻辑删除冲突）
        replaceDefinitionRows(draft.getId(), content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProjectTemplate(Long id) {
        ProjectTemplateDO template = validateTemplateExists(id);
        boolean hasPublished = !revisionMapper.selectPublishedListByTemplateId(id).isEmpty();
        // BR-8 系统保留不得删除；留痕：已发布版本不得物理删除
        if (!TemplateRules.canDelete(Boolean.TRUE.equals(template.getSystemReserved()), hasPublished)) {
            throw exception(PROJECT_TEMPLATE_DELETE_FORBIDDEN);
        }
        ProjectTemplateRevisionDO draft = revisionMapper.selectDraftByTemplateId(id);
        if (draft != null) {
            physicallyDeleteDefinitionRows(draft.getId());
            revisionMapper.deleteById(draft.getId());
        }
        projectTemplateMapper.deleteById(id);
    }

    @Override
    public PageResult<ProjectTemplateDO> getProjectTemplatePage(ProjectTemplatePageReqVO pageReqVO) {
        return projectTemplateMapper.selectPage(pageReqVO);
    }

    @Override
    public ProjectTemplateDO getProjectTemplate(Long id) {
        return projectTemplateMapper.selectById(id);
    }

    @Override
    public List<ProjectTemplateRevisionDO> getRevisionList(Long templateId) {
        return revisionMapper.selectListByTemplateId(templateId);
    }

    @Override
    public ProjectTemplateRevisionDO getRevision(Long templateId, Integer revisionNo) {
        return revisionMapper.selectByTemplateIdAndRevisionNo(templateId, revisionNo);
    }

    @Override
    public TemplateDefinitionContent getDraftContent(Long templateId) {
        ProjectTemplateRevisionDO draft = revisionMapper.selectDraftByTemplateId(templateId);
        if (draft == null) {
            throw exception(PROJECT_TEMPLATE_NO_DRAFT_REVISION);
        }
        return loadContent(draft);
    }

    @Override
    public TemplateDefinitionContent getRevisionContent(Long templateId, Integer revisionNo) {
        ProjectTemplateRevisionDO revision = revisionMapper.selectByTemplateIdAndRevisionNo(templateId, revisionNo);
        if (revision == null) {
            throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        }
        return loadContent(revision);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishProjectTemplate(Long id) {
        ProjectTemplateDO template = validateTemplateExists(id);
        ProjectTemplateRevisionDO draft = revisionMapper.selectDraftByTemplateId(id);
        if (draft == null) {
            throw exception(PROJECT_TEMPLATE_NO_DRAFT_REVISION);
        }
        // BR-3/BR-5 发布前置（RETIRED 不可再发布）
        if (!TemplateRules.canPublish(template.getStatus(), true)) {
            throw exception(PROJECT_TEMPLATE_STATUS_INVALID);
        }
        // BR-2 发布校验：失败保持草稿并列出失败项（重试用原版本号）
        TemplateDefinitionContent content = loadContent(draft);
        List<String> failures = TemplatePublishValidator.validate(content);
        if (!failures.isEmpty()) {
            String summary = String.join("；", failures);
            // 校验结果留痕到草稿行
            ProjectTemplateRevisionDO summaryUpdate = new ProjectTemplateRevisionDO();
            summaryUpdate.setId(draft.getId());
            summaryUpdate.setValidationSummary(summary.length() > 1000 ? summary.substring(0, 1000) : summary);
            revisionMapper.updateById(summaryUpdate);
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, summary);
        }
        // 版本冻结：递增版本号生成 PUBLISHED 只读行
        int nextRevisionNo = nextPublishedRevisionNo(id);
        ProjectTemplateRevisionDO published = new ProjectTemplateRevisionDO();
        published.setTemplateId(id);
        published.setRevisionNo(nextRevisionNo);
        published.setStatus(TemplateRules.REVISION_STATUS_PUBLISHED);
        published.setSigningMethod(draft.getSigningMethod());
        published.setProjectCategory(draft.getProjectCategory());
        published.setImplementationMethod(draft.getImplementationMethod());
        published.setMajorProjectLevel(draft.getMajorProjectLevel());
        published.setProcessDefinitionKey(draft.getProcessDefinitionKey());
        published.setProcessDefinitionVersion(draft.getProcessDefinitionVersion());
        published.setValidationSummary("发布校验通过");
        published.setPublishedBy(String.valueOf(SecurityFrameworkUtils.getLoginUserId()));
        published.setPublishedTime(LocalDateTime.now());
        revisionMapper.insert(published);
        copyDefinitionRows(draft.getId(), published.getId());
        // 模板转 ACTIVE
        ProjectTemplateDO statusUpdate = new ProjectTemplateDO();
        statusUpdate.setId(id);
        statusUpdate.setStatus(TemplateRules.STATUS_ACTIVE);
        projectTemplateMapper.updateById(statusUpdate);
    }

    @Override
    public void disableProjectTemplate(Long id) {
        ProjectTemplateDO template = validateTemplateExists(id);
        // BR-5 仅 ACTIVE 可停用；停用只阻新项目匹配
        if (!TemplateRules.canDisable(template.getStatus())) {
            throw exception(PROJECT_TEMPLATE_STATUS_INVALID);
        }
        ProjectTemplateDO updateObj = new ProjectTemplateDO();
        updateObj.setId(id);
        updateObj.setStatus(TemplateRules.STATUS_RETIRED);
        projectTemplateMapper.updateById(updateObj);
    }

    @Override
    public TemplateMatchResult matchPreview(String signingMethod, String projectCategory,
                                             String implementationMethod, String majorProjectLevel) {
        // BR-4 基于 ACTIVE 模板最新 PUBLISHED 版本条件 + 模板优先级
        List<ProjectTemplateDO> activeTemplates =
                projectTemplateMapper.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE);
        List<TemplateMatchCandidate> candidates = new ArrayList<>();
        for (ProjectTemplateDO activeTemplate : activeTemplates) {
            List<ProjectTemplateRevisionDO> publishedList =
                    revisionMapper.selectPublishedListByTemplateId(activeTemplate.getId());
            if (publishedList.isEmpty()) {
                continue;
            }
            ProjectTemplateRevisionDO latest = publishedList.get(0);
            TemplateMatchCandidate candidate = new TemplateMatchCandidate();
            candidate.setTemplateId(activeTemplate.getId());
            candidate.setCode(activeTemplate.getCode());
            candidate.setName(activeTemplate.getName());
            candidate.setMatchPriority(activeTemplate.getMatchPriority());
            candidate.setLatestRevisionNo(latest.getRevisionNo());
            candidate.setSigningMethod(latest.getSigningMethod());
            candidate.setProjectCategory(latest.getProjectCategory());
            candidate.setImplementationMethod(latest.getImplementationMethod());
            candidate.setMajorProjectLevel(latest.getMajorProjectLevel());
            candidates.add(candidate);
        }
        return TemplateMatcher.match(candidates, signingMethod, projectCategory,
                implementationMethod, majorProjectLevel);
    }

    // ========== 内部方法 ==========

    private ProjectTemplateDO validateTemplateExists(Long id) {
        ProjectTemplateDO template = projectTemplateMapper.selectById(id);
        if (template == null) {
            throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    private int nextPublishedRevisionNo(Long templateId) {
        List<ProjectTemplateRevisionDO> publishedList =
                revisionMapper.selectPublishedListByTemplateId(templateId);
        // 发布失败重试用原版本号：始终基于已发布最大版本号+1
        return publishedList.stream()
                .map(ProjectTemplateRevisionDO::getRevisionNo)
                .filter(no -> no != null && no > 0)
                .max(Integer::compareTo)
                .map(no -> no + 1)
                .orElse(1);
    }

    private TemplateDefinitionContent loadContent(ProjectTemplateRevisionDO revision) {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.setSigningMethod(revision.getSigningMethod());
        content.setProjectCategory(revision.getProjectCategory());
        content.setImplementationMethod(revision.getImplementationMethod());
        content.setMajorProjectLevel(revision.getMajorProjectLevel());
        content.setProcessDefinitionKey(revision.getProcessDefinitionKey());
        content.setProcessDefinitionVersion(revision.getProcessDefinitionVersion());
        content.setStages(BeanUtils.toBean(stageDefinitionMapper.selectListByRevisionId(revision.getId()),
                TemplateDefinitionContent.StageDef.class));
        content.setTasks(BeanUtils.toBean(taskDefinitionMapper.selectListByRevisionId(revision.getId()),
                TemplateDefinitionContent.TaskDef.class));
        content.setMilestones(BeanUtils.toBean(milestoneDefinitionMapper.selectListByRevisionId(revision.getId()),
                TemplateDefinitionContent.MilestoneDef.class));
        List<TemplateDefinitionContent.DeliverableDef> deliverables = BeanUtils.toBean(
                deliverableDefinitionMapper.selectListByRevisionId(revision.getId()),
                TemplateDefinitionContent.DeliverableDef.class);
        content.setDeliverables(deliverables);
        List<ProjectTemplateGateDefinitionDO> gateRows = gateDefinitionMapper.selectListByRevisionId(revision.getId());
        List<ProjectTemplateGateReferenceDO> refRows = gateReferenceMapper.selectListByRevisionId(revision.getId());
        List<TemplateDefinitionContent.GateDef> gates = new ArrayList<>();
        for (ProjectTemplateGateDefinitionDO gateRow : gateRows) {
            TemplateDefinitionContent.GateDef gate = BeanUtils.toBean(gateRow, TemplateDefinitionContent.GateDef.class);
            gate.setReferences(new ArrayList<>());
            for (ProjectTemplateGateReferenceDO refRow : refRows) {
                if (refRow.getGateCode().equals(gateRow.getGateCode())) {
                    gate.getReferences().add(BeanUtils.toBean(refRow, TemplateDefinitionContent.GateRef.class));
                }
            }
            gates.add(gate);
        }
        content.setGates(gates);
        return content;
    }

    /**
     * 定义行整体替换：草稿编辑语义（旧行物理删除后重插）
     */
    private void replaceDefinitionRows(Long revisionId, TemplateDefinitionContent content) {
        physicallyDeleteDefinitionRows(revisionId);
        insertDefinitionRows(revisionId, content);
    }

    /**
     * 草稿 → 已发布快照：复制定义行（草稿行保留，继续承载后续编辑）
     */
    private void copyDefinitionRows(Long draftRevisionId, Long publishedRevisionId) {
        TemplateDefinitionContent content = loadContent(newDraftView(draftRevisionId));
        insertDefinitionRows(publishedRevisionId, content);
    }

    /**
     * 构造仅含ID的版本视图，供 loadContent 复用
     */
    private ProjectTemplateRevisionDO newDraftView(Long revisionId) {
        ProjectTemplateRevisionDO view = new ProjectTemplateRevisionDO();
        view.setId(revisionId);
        return view;
    }

    private void insertDefinitionRows(Long revisionId, TemplateDefinitionContent content) {
        for (TemplateDefinitionContent.StageDef stage : content.getStages()) {
            ProjectTemplateStageDefinitionDO row = BeanUtils.toBean(stage, ProjectTemplateStageDefinitionDO.class);
            row.setId(null);
            row.setTemplateRevisionId(revisionId);
            stageDefinitionMapper.insert(row);
        }
        for (TemplateDefinitionContent.TaskDef task : content.getTasks()) {
            ProjectTemplateTaskDefinitionDO row = BeanUtils.toBean(task, ProjectTemplateTaskDefinitionDO.class);
            row.setId(null);
            row.setTemplateRevisionId(revisionId);
            taskDefinitionMapper.insert(row);
        }
        for (TemplateDefinitionContent.MilestoneDef milestone : content.getMilestones()) {
            ProjectTemplateMilestoneDefinitionDO row = BeanUtils.toBean(milestone, ProjectTemplateMilestoneDefinitionDO.class);
            row.setId(null);
            row.setTemplateRevisionId(revisionId);
            milestoneDefinitionMapper.insert(row);
        }
        for (TemplateDefinitionContent.DeliverableDef deliverable : content.getDeliverables()) {
            ProjectTemplateDeliverableDefinitionDO row = BeanUtils.toBean(deliverable, ProjectTemplateDeliverableDefinitionDO.class);
            row.setId(null);
            row.setTemplateRevisionId(revisionId);
            deliverableDefinitionMapper.insert(row);
        }
        for (TemplateDefinitionContent.GateDef gate : content.getGates()) {
            ProjectTemplateGateDefinitionDO row = BeanUtils.toBean(gate, ProjectTemplateGateDefinitionDO.class);
            row.setId(null);
            row.setTemplateRevisionId(revisionId);
            gateDefinitionMapper.insert(row);
            if (gate.getReferences() != null) {
                for (TemplateDefinitionContent.GateRef ref : gate.getReferences()) {
                    ProjectTemplateGateReferenceDO refRow = BeanUtils.toBean(ref, ProjectTemplateGateReferenceDO.class);
                    refRow.setId(null);
                    refRow.setTemplateRevisionId(revisionId);
                    refRow.setGateCode(gate.getGateCode());
                    gateReferenceMapper.insert(refRow);
                }
            }
        }
    }

    private void physicallyDeleteDefinitionRows(Long revisionId) {
        stageDefinitionMapper.physicallyDeleteByRevisionId(revisionId);
        taskDefinitionMapper.physicallyDeleteByRevisionId(revisionId);
        milestoneDefinitionMapper.physicallyDeleteByRevisionId(revisionId);
        deliverableDefinitionMapper.physicallyDeleteByRevisionId(revisionId);
        gateDefinitionMapper.physicallyDeleteByRevisionId(revisionId);
        gateReferenceMapper.physicallyDeleteByRevisionId(revisionId);
    }
}
