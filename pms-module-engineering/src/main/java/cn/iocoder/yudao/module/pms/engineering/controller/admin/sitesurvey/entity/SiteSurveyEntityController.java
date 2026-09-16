package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntityPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntityRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntityFormSchemaRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;

/**
 * 管理后台 - 现场工勘 Controller（FR-ENG-001）。
 * <p>
 * 路径前缀 {@code /api/v1/pms/site-surveys}。
 */
@Tag(name = "管理后台 - 现场工勘")
@RestController
@RequestMapping("/api/v1/pms/site-surveys")
@Validated
public class SiteSurveyEntityController {

    @Resource
    private SiteSurveyEntityService siteSurveyService;

    @Resource
    private cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyEntityFormService formService;

    @GetMapping("/form-schema")
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:query')")
    public CommonResult<SiteSurveyEntityFormSchemaRespVO> formSchema(
            @RequestParam Long revisionId, @RequestParam Integer revisionVersion) {
        var schema = formService.schema(revisionId, revisionVersion, false);
        return success(SiteSurveyEntityFormSchemaRespVO.from(schema, formService.fieldBindings(schema)));
    }

    @GetMapping("/default-form-schema")
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:query')")
    public CommonResult<SiteSurveyEntityFormSchemaRespVO> defaultFormSchema() {
        var schema = formService.defaultSchema();
        return success(SiteSurveyEntityFormSchemaRespVO.from(schema, formService.fieldBindings(schema)));
    }

    @PostMapping("/create")
    @Operation(summary = "创建现场工勘")
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:create')")
    public CommonResult<Long> createSiteSurveyEntity(@Valid @RequestBody SiteSurveyEntitySaveReqVO createReqVO) {
        return success(siteSurveyService.createSiteSurveyEntity(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新现场工勘")
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public CommonResult<Boolean> updateSiteSurveyEntity(@Valid @RequestBody SiteSurveyEntitySaveReqVO updateReqVO) {
        siteSurveyService.updateSiteSurveyEntity(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除现场工勘")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:delete')")
    public CommonResult<Boolean> deleteSiteSurveyEntity(@RequestParam("id") Long id, @RequestBody(required = false) ProjectBusinessExecutionSelection execution) {
        siteSurveyService.deleteSiteSurveyEntity(id, execution);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询现场工勘详情")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:query')")
    public CommonResult<SiteSurveyEntityRespVO> getSiteSurveyEntity(@RequestParam("id") Long id) {
        SiteSurveyEntityDO survey = siteSurveyService.getSiteSurveyEntity(id);
        return success(formService.response(survey));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询现场工勘")
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:query')")
    public CommonResult<PageResult<SiteSurveyEntityRespVO>> getSiteSurveyEntityPage(@Validated SiteSurveyEntityPageReqVO pageReqVO) {
        PageResult<SiteSurveyEntityDO> pageResult = siteSurveyService.getSiteSurveyEntityPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SiteSurveyEntityRespVO.class));
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认工勘")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public CommonResult<Boolean> confirmSiteSurveyEntity(@RequestParam("id") Long id, @RequestBody(required = false) ProjectBusinessExecutionSelection execution) {
        siteSurveyService.confirmSiteSurveyEntity(id, execution);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回工勘")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public CommonResult<Boolean> rejectSiteSurveyEntity(@RequestParam("id") Long id, @RequestBody(required = false) ProjectBusinessExecutionSelection execution) {
        siteSurveyService.rejectSiteSurveyEntity(id, execution);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档工勘")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-site-survey:update')")
    public CommonResult<Boolean> archiveSiteSurveyEntity(@RequestParam("id") Long id, @RequestBody(required = false) ProjectBusinessExecutionSelection execution) {
        siteSurveyService.archiveSiteSurveyEntity(id, execution);
        return success(true);
    }
}
