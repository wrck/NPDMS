package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;

import jakarta.validation.Valid;

/**
 * PMS 现场工勘 Service 接口（FR-ENG-001）。
 * <p>
 * 状态流转：0 草稿 → 1 已确认 / 2 已驳回；1 已确认 → 3 已归档。
 */
public interface SiteSurveyService {

    /**
     * 创建现场工勘
     */
    Long createSiteSurvey(@Valid SiteSurveySaveReqVO createReqVO);

    /**
     * 更新现场工勘
     */
    void updateSiteSurvey(@Valid SiteSurveySaveReqVO updateReqVO);

    /**
     * 删除现场工勘
     */
    void deleteSiteSurvey(Long id);

    /**
     * 查询现场工勘详情
     */
    SiteSurveyDO getSiteSurvey(Long id);

    /**
     * 分页查询现场工勘
     */
    PageResult<SiteSurveyDO> getSiteSurveyPage(SiteSurveyPageReqVO pageReqVO);

    /**
     * 确认工勘：草稿(0) → 已确认(1)
     */
    void confirmSiteSurvey(Long id);

    /**
     * 驳回工勘：草稿(0) → 已驳回(2)
     */
    void rejectSiteSurvey(Long id);

    /**
     * 归档工勘：已确认(1) → 已归档(3)
     */
    void archiveSiteSurvey(Long id);
}
