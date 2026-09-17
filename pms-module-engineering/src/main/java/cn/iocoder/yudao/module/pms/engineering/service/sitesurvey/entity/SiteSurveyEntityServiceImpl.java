package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntityPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 现场工勘 Service 实现（FR-ENG-001）。
 * <p>
 * 状态流转：0 草稿 → 1 已确认 / 2 已驳回；1 已确认 → 3 已归档。
 */
@Service
@Validated
public class SiteSurveyEntityServiceImpl implements SiteSurveyEntityService {

    @Resource
    private SiteSurveyEntityMapper siteSurveyEntityMapper;
    @Resource
    private SiteSurveyEntityProvider entityProvider;
    @Resource
    private cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi projectScopes;
    @Resource
    private EngineeringLocationFactService locationFactService;
    @Resource
    private SiteSurveyEntityFormService formService;
    @Resource
    private SiteSurveyEntityWriteAccess writeAccess;
    @Resource
    private cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateApi projectEndDateApi;
    @Resource
    private cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents ruleEvents;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSiteSurveyEntity(SiteSurveyEntitySaveReqVO createReqVO) {
        writeAccess.lock(createReqVO.getProjectId(), "pms:sol-site-survey:create", createReqVO.getExecution(), "SOL.SITE_SURVEY.CREATE", null);
        validateCodeUnique(createReqVO.getProjectId(), createReqVO.getCode(), null);
        SiteSurveyEntityDO survey = BeanUtils.toBean(createReqVO, SiteSurveyEntityDO.class);
        survey.setId(null);
        survey.setTenantId(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        survey.setStatus(0);
        survey.setVersion(0);
        survey.setOutsourceRequired(Boolean.TRUE.equals(createReqVO.getOutsourceRequired()));
        formService.prepare(survey, createReqVO);
        updateProjectEndDate(createReqVO, null);
        siteSurveyEntityMapper.insert(survey);
        applyLocation(survey, createReqVO.getLocation(), createReqVO.getLocationMaintenance(), 0);
        updateChecked(survey);
        formService.persist(survey, createReqVO);
        return survey.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSiteSurveyEntity(SiteSurveyEntitySaveReqVO updateReqVO) {
        SiteSurveyEntityDO existing = validateSiteSurveyEntityExists(updateReqVO.getId());
        writeAccess.lock(existing.getProjectId(), "pms:sol-site-survey:update", updateReqVO.getExecution(), "SOL.SITE_SURVEY.UPDATE", existing.getId());
        validateStatus(existing, 0);
        if (!Objects.equals(existing.getProjectId(), updateReqVO.getProjectId())
                || !Objects.equals(existing.getCode(), updateReqVO.getCode())) throw exception(SITE_SURVEY_FORM_INVALID);
        validateCodeUnique(existing.getProjectId(), updateReqVO.getCode(), updateReqVO.getId());
        validateVersion(existing, updateReqVO.getVersion());
        SiteSurveyEntityDO update = BeanUtils.toBean(updateReqVO, SiteSurveyEntityDO.class);
        update.setStatus(existing.getStatus());
        update.setOutsourceRequired(updateReqVO.getOutsourceRequired() == null
                ? existing.getOutsourceRequired() : updateReqVO.getOutsourceRequired());
        // This reference is written only by successful outsourcing creation, never by form values.
        update.setOutsourceRequestId(existing.getOutsourceRequestId());
        update.setTenantId(existing.getTenantId());
        formService.prepare(update, updateReqVO);
        updateProjectEndDate(updateReqVO, existing);
        applyLocation(update, updateReqVO.getLocation(), updateReqVO.getLocationMaintenance(),
                existing.getVersion() + 1);
        updateChecked(update);
        formService.persist(update, updateReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSiteSurveyEntity(Long id, ProjectBusinessExecutionSelection execution) {
        SiteSurveyEntityDO existing = validateSiteSurveyEntityExists(id);
        writeAccess.lock(existing.getProjectId(), "pms:sol-site-survey:delete", execution, "SOL.SITE_SURVEY.DELETE", existing.getId());
        validateStatus(existing, 0);
        if (existing.getOutsourceRequestId() != null) throw exception(SITE_SURVEY_OUTSOURCE_DELETE_BLOCKED);
        if (siteSurveyEntityMapper.deleteDraft(new cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityMutation(
                existing.getId(), existing.getTenantId(), existing.getVersion())) != 1) {
            throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        }
        requestRuleReevaluation(existing);
    }

    @Override
    public SiteSurveyEntityDO getSiteSurveyEntity(Long id) {
        var tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        entityProvider.requireReadable(cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef.current(
                new cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef(tenantId, "SOL", "SITE_SURVEY", id)),
                new cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor(tenantId,
                        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(), null));
        return siteSurveyEntityMapper.selectById(id);
    }

    @Override
    public PageResult<SiteSurveyEntityDO> getSiteSurveyEntityPage(SiteSurveyEntityPageReqVO pageReqVO) {
        var query = BeanUtils.toBean(pageReqVO,
                cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityPageQuery.class);
        query.setTenantId(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        query.setVisibleProjectIds(projectScopes.resolveAllCurrent(
                new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery(query.getTenantId(),
                        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                        cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW)));
        return siteSurveyEntityMapper.selectPage(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmSiteSurveyEntity(Long id, ProjectBusinessExecutionSelection execution) {
        SiteSurveyEntityDO survey = validateSiteSurveyEntityExists(id);
        writeAccess.lock(survey.getProjectId(), "pms:sol-site-survey:update", execution, "SOL.SITE_SURVEY.CONFIRM", survey.getId());
        validateStatus(survey, 0); // 草稿 → 已确认
        validateForm(survey, false);
        updateStatus(survey, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectSiteSurveyEntity(Long id, ProjectBusinessExecutionSelection execution) {
        SiteSurveyEntityDO survey = validateSiteSurveyEntityExists(id);
        writeAccess.lock(survey.getProjectId(), "pms:sol-site-survey:update", execution, "SOL.SITE_SURVEY.REJECT", survey.getId());
        validateStatus(survey, 0); // 草稿 → 已驳回
        updateStatus(survey, 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveSiteSurveyEntity(Long id, ProjectBusinessExecutionSelection execution) {
        SiteSurveyEntityDO survey = validateSiteSurveyEntityExists(id);
        writeAccess.lock(survey.getProjectId(), "pms:sol-site-survey:update", execution, "SOL.SITE_SURVEY.ARCHIVE", survey.getId());
        validateStatus(survey, 1); // 已确认 → 已归档
        updateStatus(survey, 3);
    }

    // ==================== 内部工具方法 ====================

    private SiteSurveyEntityDO validateSiteSurveyEntityExists(Long id) {
        SiteSurveyEntityDO survey = siteSurveyEntityMapper.selectById(id);
        if (survey == null) {
            throw exception(SITE_SURVEY_NOT_EXISTS);
        }
        return survey;
    }

    private void validateCodeUnique(Long projectId, String code, Long excludeId) {
        SiteSurveyEntityDO existing = siteSurveyEntityMapper.selectByProjectIdAndCode(projectId, code);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw exception(SITE_SURVEY_CODE_DUPLICATE);
        }
    }

    private void validateVersion(SiteSurveyEntityDO survey, Integer version) {
        if (version == null || !Objects.equals(survey.getVersion(), version)) {
            throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(SiteSurveyEntityDO survey, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(survey.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(SITE_SURVEY_STATUS_INVALID);
    }

    private void updateStatus(SiteSurveyEntityDO survey, int newStatus) {
        survey.setStatus(newStatus);
        if (newStatus == 1) survey.setConfirmedAt(java.time.LocalDateTime.now());
        if (newStatus == 3) survey.setArchivedAt(java.time.LocalDateTime.now());
        // @Version owns oldVersion -> newVersion; do not increment it before updateById.
        // https://baomidou.com/plugins/optimistic-locker/
        updateChecked(survey);
    }

    private void updateChecked(SiteSurveyEntityDO survey) {
        if (siteSurveyEntityMapper.updateById(survey) != 1) throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        requestRuleReevaluation(survey);
    }

    private void requestRuleReevaluation(SiteSurveyEntityDO survey) {
        ruleEvents.changed(survey.getProjectId(), "SiteSurvey", survey.getId(),
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                "site-survey:" + survey.getId() + ":" + survey.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('pms:sol-site-survey:update')")
    public void associateOutsourceRequest(Long surveyId, Long projectId, Long outsourceRequestId, ProjectBusinessExecutionSelection execution) {
        if (surveyId == null) throw exception(SITE_SURVEY_OUTSOURCE_INVALID);
        SiteSurveyEntityDO survey = validateSiteSurveyEntityExists(surveyId);
        writeAccess.lock(survey.getProjectId(), "pms:sol-site-survey:update", execution);
        validateStatus(survey, 0);
        if (!Objects.equals(projectId, survey.getProjectId()) || !Boolean.TRUE.equals(survey.getOutsourceRequired())
                || outsourceRequestId == null || survey.getOutsourceRequestId() != null) {
            throw exception(SITE_SURVEY_OUTSOURCE_INVALID);
        }
        survey.setOutsourceRequestId(outsourceRequestId);
        updateChecked(survey);
    }

    private void validateForm(SiteSurveyEntityDO survey, boolean binding) {
        formService.validate(survey, binding);
    }

    private void updateProjectEndDate(SiteSurveyEntitySaveReqVO request, SiteSurveyEntityDO existing) {
        Object requested = request.getBusinessValues() == null ? null : request.getBusinessValues().get("requiredEndDate");
        Object previous = existing == null || existing.getRequiredEndDate() == null ? null : existing.getRequiredEndDate().toString();
        if (Objects.equals(requested, previous) && !Boolean.TRUE.equals(request.getProjectEndDateChanged())) return;
        if (requested == null && previous == null) return;
        if (!(requested instanceof String date) || date.isBlank()) throw exception(SITE_SURVEY_FORM_INVALID);
        projectEndDateApi.updateFromSurvey(new cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateCommand(
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(),
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(), request.getProjectId(),
                request.getProjectEndDateVersion(), java.time.LocalDate.parse(date)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('pms:sol-site-survey:update')")
    public void releaseDeletedOutsourceRequest(Long surveyId, Long outsourceRequestId, ProjectBusinessExecutionSelection execution) {
        SiteSurveyEntityDO survey = validateSiteSurveyEntityExists(surveyId);
        writeAccess.lock(survey.getProjectId(), "pms:sol-site-survey:update", execution);
        if (!Objects.equals(survey.getOutsourceRequestId(), outsourceRequestId)) return;
        validateStatus(survey, 0);
        survey.setOutsourceRequestId(null);
        updateChecked(survey);
    }

    private void applyLocation(SiteSurveyEntityDO survey, String fallbackLocation,
                               LocationMaintenanceCommand command,
                               Integer sourceVersion) {
        if (command == null) {
            if (fallbackLocation == null || fallbackLocation.isBlank()) {
                throw exception(SITE_SURVEY_LOCATION_REQUIRED);
            }
            survey.setLocationResolutionStatus("UNRESOLVED");
            return;
        }
        EngineeringLocationFactService.LocationFact fact = locationFactService.maintain(survey.getProjectId(),
                "SITE_SURVEY", survey.getId(), sourceVersion, fallbackLocation, command);
        if (!"RESOLVED".equals(fact.resolutionStatus())) {
            throw exception(SITE_SURVEY_LOCATION_INVALID);
        }
        survey.setAddressId(fact.addressId());
        survey.setAddressVersion(fact.addressVersion());
        survey.setSiteId(fact.siteId());
        survey.setSiteVersion(fact.siteVersion());
        survey.setSiteLocationId(fact.siteLocationId());
        survey.setSiteLocationVersion(fact.siteLocationVersion());
        survey.setLocationResolutionStatus(fact.resolutionStatus());
        survey.setAddressSnapshot(fact.addressSnapshot());
        survey.setLocationSnapshot(fact.locationSnapshot());
    }
}
