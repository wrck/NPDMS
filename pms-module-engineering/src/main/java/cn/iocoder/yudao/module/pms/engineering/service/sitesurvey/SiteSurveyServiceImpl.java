package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSiteSurvey(SiteSurveySaveReqVO createReqVO) {
        validateCodeUnique(createReqVO.getProjectId(), createReqVO.getCode(), null);
        SiteSurveyDO survey = BeanUtils.toBean(createReqVO, SiteSurveyDO.class);
        if (survey.getStatus() == null) {
            survey.setStatus(0); // 草稿
        }
        if (survey.getVersion() == null) {
            survey.setVersion(0);
        }
        siteSurveyMapper.insert(survey);
        return survey.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSiteSurvey(SiteSurveySaveReqVO updateReqVO) {
        SiteSurveyDO existing = validateSiteSurveyExists(updateReqVO.getId());
        validateCodeUnique(existing.getProjectId(), updateReqVO.getCode(), updateReqVO.getId());
        validateVersion(existing, updateReqVO.getVersion());
        SiteSurveyDO update = BeanUtils.toBean(updateReqVO, SiteSurveyDO.class);
        siteSurveyMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSiteSurvey(Long id) {
        validateSiteSurveyExists(id);
        siteSurveyMapper.deleteById(id);
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
        if (version != null && !Objects.equals(survey.getVersion(), version)) {
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
        survey.setVersion(survey.getVersion() + 1);
        siteSurveyMapper.updateById(survey);
    }
}
