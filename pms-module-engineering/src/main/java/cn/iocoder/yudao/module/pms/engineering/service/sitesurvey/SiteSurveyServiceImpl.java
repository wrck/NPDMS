package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 现场工勘 Service 实现（FR-ENG-001）。
 * <p>
 * 状态流转：0 草稿 → 1 已确认 / 2 已驳回；1 已确认 → 3 已归档。
 */
@Service
@Validated
public class SiteSurveyServiceImpl implements SiteSurveyService {

    @Resource
    private SiteSurveyMapper siteSurveyMapper;
    @Resource
    private EngineeringLocationFactService locationFactService;
    @Resource
    private SiteSurveyFormService formService;
    @Resource
    private cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateApi projectEndDateApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSiteSurvey(SiteSurveySaveReqVO createReqVO) {
        validateCodeUnique(createReqVO.getProjectId(), createReqVO.getCode(), null);
        SiteSurveyDO survey = BeanUtils.toBean(createReqVO, SiteSurveyDO.class);
        survey.setId(null);
        survey.setStatus(0);
        survey.setVersion(0);
        survey.setOutsourceRequired(Boolean.TRUE.equals(createReqVO.getOutsourceRequired()));
        validateForm(survey, true);
        updateProjectEndDate(createReqVO, null);
        siteSurveyMapper.insert(survey);
        applyLocation(survey, createReqVO.getLocation(), createReqVO.getLocationMaintenance(), 0);
        updateChecked(survey);
        return survey.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSiteSurvey(SiteSurveySaveReqVO updateReqVO) {
        SiteSurveyDO existing = validateSiteSurveyExists(updateReqVO.getId());
        validateStatus(existing, 0);
        if (!Objects.equals(existing.getProjectId(), updateReqVO.getProjectId())
                || !Objects.equals(existing.getCode(), updateReqVO.getCode())) throw exception(SITE_SURVEY_FORM_INVALID);
        validateCodeUnique(existing.getProjectId(), updateReqVO.getCode(), updateReqVO.getId());
        validateVersion(existing, updateReqVO.getVersion());
        SiteSurveyDO update = BeanUtils.toBean(updateReqVO, SiteSurveyDO.class);
        update.setStatus(existing.getStatus());
        update.setOutsourceRequired(updateReqVO.getOutsourceRequired() == null
                ? existing.getOutsourceRequired() : updateReqVO.getOutsourceRequired());
        // This reference is written only by successful outsourcing creation, never by form values.
        update.setOutsourceRequestId(existing.getOutsourceRequestId());
        boolean bindingChanged = !Objects.equals(existing.getFormRevisionId(), update.getFormRevisionId())
                || !Objects.equals(existing.getFormRevisionVersion(), update.getFormRevisionVersion());
        if (existing.getFormRevisionId() != null && update.getFormRevisionId() == null) throw exception(SITE_SURVEY_FORM_INVALID);
        if (bindingChanged && existing.getFormExtraValues() != null && !existing.getFormExtraValues().isEmpty()) {
            var fields = formService.schema(update.getFormRevisionId(), update.getFormRevisionVersion(), true).fields()
                    .stream().map(cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor::fieldKey)
                    .collect(java.util.stream.Collectors.toSet());
            if (!fields.containsAll(existing.getFormExtraValues().keySet())) throw exception(SITE_SURVEY_FORM_INVALID);
        }
        validateForm(update, bindingChanged);
        updateProjectEndDate(updateReqVO, existing);
        applyLocation(update, updateReqVO.getLocation(), updateReqVO.getLocationMaintenance(),
                existing.getVersion() + 1);
        updateChecked(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSiteSurvey(Long id) {
        SiteSurveyDO existing = validateSiteSurveyExists(id);
        validateStatus(existing, 0);
        if (existing.getOutsourceRequestId() != null) throw exception(SITE_SURVEY_OUTSOURCE_DELETE_BLOCKED);
        if (siteSurveyMapper.deleteDraft(new cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyMutation(
                existing.getId(), existing.getTenantId(), existing.getVersion())) != 1) {
            throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        }
    }

    @Override
    public SiteSurveyDO getSiteSurvey(Long id) {
        return siteSurveyMapper.selectById(id);
    }

    @Override
    public PageResult<SiteSurveyDO> getSiteSurveyPage(SiteSurveyPageReqVO pageReqVO) {
        return siteSurveyMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmSiteSurvey(Long id) {
        SiteSurveyDO survey = validateSiteSurveyExists(id);
        validateStatus(survey, 0); // 草稿 → 已确认
        validateForm(survey, false);
        updateStatus(survey, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectSiteSurvey(Long id) {
        SiteSurveyDO survey = validateSiteSurveyExists(id);
        validateStatus(survey, 0); // 草稿 → 已驳回
        updateStatus(survey, 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveSiteSurvey(Long id) {
        SiteSurveyDO survey = validateSiteSurveyExists(id);
        validateStatus(survey, 1); // 已确认 → 已归档
        updateStatus(survey, 3);
    }

    // ==================== 内部工具方法 ====================

    private SiteSurveyDO validateSiteSurveyExists(Long id) {
        SiteSurveyDO survey = siteSurveyMapper.selectById(id);
        if (survey == null) {
            throw exception(SITE_SURVEY_NOT_EXISTS);
        }
        return survey;
    }

    private void validateCodeUnique(Long projectId, String code, Long excludeId) {
        SiteSurveyDO existing = siteSurveyMapper.selectByProjectIdAndCode(projectId, code);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw exception(SITE_SURVEY_CODE_DUPLICATE);
        }
    }

    private void validateVersion(SiteSurveyDO survey, Integer version) {
        if (version == null || !Objects.equals(survey.getVersion(), version)) {
            throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(SiteSurveyDO survey, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(survey.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(SITE_SURVEY_STATUS_INVALID);
    }

    private void updateStatus(SiteSurveyDO survey, int newStatus) {
        survey.setStatus(newStatus);
        // @Version owns oldVersion -> newVersion; do not increment it before updateById.
        // https://baomidou.com/plugins/optimistic-locker/
        updateChecked(survey);
    }

    private void updateChecked(SiteSurveyDO survey) {
        if (siteSurveyMapper.updateById(survey) != 1) throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public void associateOutsourceRequest(Long surveyId, Long projectId, Long outsourceRequestId) {
        if (surveyId == null) throw exception(SITE_SURVEY_OUTSOURCE_INVALID);
        SiteSurveyDO survey = validateSiteSurveyExists(surveyId);
        validateStatus(survey, 0);
        if (!Objects.equals(projectId, survey.getProjectId()) || !Boolean.TRUE.equals(survey.getOutsourceRequired())
                || outsourceRequestId == null || survey.getOutsourceRequestId() != null) {
            throw exception(SITE_SURVEY_OUTSOURCE_INVALID);
        }
        survey.setOutsourceRequestId(outsourceRequestId);
        updateChecked(survey);
    }

    private void validateForm(SiteSurveyDO survey, boolean binding) {
        if (survey.getFormRevisionId() != null || survey.getFormExtraValues() != null) {
            formService.validate(survey, binding);
        }
    }

    private void updateProjectEndDate(SiteSurveySaveReqVO request, SiteSurveyDO existing) {
        Object requested = request.getFormExtraValues() == null ? null : request.getFormExtraValues().get("extra_requiredEndDate");
        Object previous = existing == null || existing.getFormExtraValues() == null ? null : existing.getFormExtraValues().get("extra_requiredEndDate");
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
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public void releaseDeletedOutsourceRequest(Long surveyId, Long outsourceRequestId) {
        SiteSurveyDO survey = validateSiteSurveyExists(surveyId);
        if (!Objects.equals(survey.getOutsourceRequestId(), outsourceRequestId)) return;
        validateStatus(survey, 0);
        survey.setOutsourceRequestId(null);
        updateChecked(survey);
    }

    private void applyLocation(SiteSurveyDO survey, String fallbackLocation,
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
