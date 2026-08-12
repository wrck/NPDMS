package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveyRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.SiteSurveyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 现场工勘 Controller（FR-ENG-001）。
 * <p>
 * 路径前缀 {@code /pms/eng-site-survey}。
 */
@Tag(name = "管理后台 - 现场工勘")
@RestController
@RequestMapping("/pms/eng-site-survey")
@Validated
public class SiteSurveyController {

    @Resource
    private SiteSurveyService siteSurveyService;

    @PostMapping("/create")
    @Operation(summary = "创建现场工勘")
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:create')")
    public CommonResult<Long> createSiteSurvey(@Valid @RequestBody SiteSurveySaveReqVO createReqVO) {
        return success(siteSurveyService.createSiteSurvey(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新现场工勘")
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public CommonResult<Boolean> updateSiteSurvey(@Valid @RequestBody SiteSurveySaveReqVO updateReqVO) {
        siteSurveyService.updateSiteSurvey(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除现场工勘")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:delete')")
    public CommonResult<Boolean> deleteSiteSurvey(@RequestParam("id") Long id) {
        siteSurveyService.deleteSiteSurvey(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询现场工勘详情")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:query')")
    public CommonResult<SiteSurveyRespVO> getSiteSurvey(@RequestParam("id") Long id) {
        SiteSurveyDO survey = siteSurveyService.getSiteSurvey(id);
        return success(BeanUtils.toBean(survey, SiteSurveyRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询现场工勘")
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:query')")
    public CommonResult<PageResult<SiteSurveyRespVO>> getSiteSurveyPage(@Validated SiteSurveyPageReqVO pageReqVO) {
        PageResult<SiteSurveyDO> pageResult = siteSurveyService.getSiteSurveyPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SiteSurveyRespVO.class));
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认工勘")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public CommonResult<Boolean> confirmSiteSurvey(@RequestParam("id") Long id) {
        siteSurveyService.confirmSiteSurvey(id);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回工勘")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public CommonResult<Boolean> rejectSiteSurvey(@RequestParam("id") Long id) {
        siteSurveyService.rejectSiteSurvey(id);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档工勘")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public CommonResult<Boolean> archiveSiteSurvey(@RequestParam("id") Long id) {
        siteSurveyService.archiveSiteSurvey(id);
        return success(true);
    }
}
