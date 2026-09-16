package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntityPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;

import jakarta.validation.Valid;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;

/**
 * PMS 现场工勘 Service 接口（FR-ENG-001）。
 * <p>
 * 状态流转：0 草稿 → 1 已确认 / 2 已驳回；1 已确认 → 3 已归档。
 */
public interface SiteSurveyEntityService {
    void associateOutsourceRequest(Long surveyId, Long projectId, Long outsourceRequestId, ProjectBusinessExecutionSelection execution);
    void releaseDeletedOutsourceRequest(Long surveyId, Long outsourceRequestId, ProjectBusinessExecutionSelection execution);

    /**
     * 创建现场工勘
     */
    Long createSiteSurveyEntity(@Valid SiteSurveyEntitySaveReqVO createReqVO);

    /**
     * 更新现场工勘
     */
    void updateSiteSurveyEntity(@Valid SiteSurveyEntitySaveReqVO updateReqVO);

    /**
     * 删除现场工勘
     */
    void deleteSiteSurveyEntity(Long id, ProjectBusinessExecutionSelection execution);

    /**
     * 查询现场工勘详情
     */
    SiteSurveyEntityDO getSiteSurveyEntity(Long id);

    /**
     * 分页查询现场工勘
     */
    PageResult<SiteSurveyEntityDO> getSiteSurveyEntityPage(SiteSurveyEntityPageReqVO pageReqVO);

    /**
     * 确认工勘：草稿(0) → 已确认(1)
     */
    void confirmSiteSurveyEntity(Long id, ProjectBusinessExecutionSelection execution);

    /**
     * 驳回工勘：草稿(0) → 已驳回(2)
     */
    void rejectSiteSurveyEntity(Long id, ProjectBusinessExecutionSelection execution);

    /**
     * 归档工勘：已确认(1) → 已归档(3)
     */
    void archiveSiteSurveyEntity(Long id, ProjectBusinessExecutionSelection execution);
}
