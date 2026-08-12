package cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo.FormTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo.FormTemplateRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo.FormTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.formtemplate.FormTemplateDO;
import cn.iocoder.yudao.module.pms.engineering.service.formtemplate.FormTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 准备数据表单模板 Controller（FR-ENG-007）。
 * <p>
 * 路径前缀 {@code /pms/eng-form-template}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-form-template:*}。
 */
@Tag(name = "管理后台 - PMS 准备数据表单模板")
@RestController
@RequestMapping("/pms/eng-form-template")
@Validated
public class FormTemplateController {

    @Resource
    private FormTemplateService formTemplateService;

    @PostMapping("/create")
    @Operation(summary = "创建表单模板")
    @PreAuthorize("@ss.hasPermission('pms:eng-form-template:create')")
    public CommonResult<Long> createFormTemplate(@Valid @RequestBody FormTemplateSaveReqVO createReqVO) {
        return success(formTemplateService.createFormTemplate(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新表单模板")
    @PreAuthorize("@ss.hasPermission('pms:eng-form-template:update')")
    public CommonResult<Boolean> updateFormTemplate(@Valid @RequestBody FormTemplateSaveReqVO updateReqVO) {
        formTemplateService.updateFormTemplate(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除表单模板")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-form-template:delete')")
    public CommonResult<Boolean> deleteFormTemplate(@RequestParam("id") Long id) {
        formTemplateService.deleteFormTemplate(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询表单模板详情")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-form-template:query')")
    public CommonResult<FormTemplateRespVO> getFormTemplate(@RequestParam("id") Long id) {
        FormTemplateDO entity = formTemplateService.getFormTemplate(id);
        return success(BeanUtils.toBean(entity, FormTemplateRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询表单模板")
    @PreAuthorize("@ss.hasPermission('pms:eng-form-template:query')")
    public CommonResult<PageResult<FormTemplateRespVO>> getFormTemplatePage(@Validated FormTemplatePageReqVO pageReqVO) {
        PageResult<FormTemplateDO> pageResult = formTemplateService.getFormTemplatePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, FormTemplateRespVO.class));
    }

    @PutMapping("/publish")
    @Operation(summary = "发布表单模板（0 草稿 → 1 已发布）")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-form-template:publish')")
    public CommonResult<Boolean> publishFormTemplate(@RequestParam("id") Long id) {
        formTemplateService.publishFormTemplate(id);
        return success(true);
    }

    @PutMapping("/disable")
    @Operation(summary = "停用表单模板（1 已发布 → 2 已停用）")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-form-template:publish')")
    public CommonResult<Boolean> disableFormTemplate(@RequestParam("id") Long id) {
        formTemplateService.disableFormTemplate(id);
        return success(true);
    }

    @PutMapping("/enable")
    @Operation(summary = "重新启用表单模板（2 已停用 → 1 已发布）")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-form-template:publish')")
    public CommonResult<Boolean> enableFormTemplate(@RequestParam("id") Long id) {
        formTemplateService.enableFormTemplate(id);
        return success(true);
    }

    @GetMapping("/published-list")
    @Operation(summary = "查询已发布模板列表（供实例创建时下拉选择）")
    @Parameter(name = "productType", description = "产品类型（可空）")
    @PreAuthorize("@ss.hasPermission('pms:eng-form-template:query')")
    public CommonResult<List<FormTemplateRespVO>> getPublishedFormTemplateList(
            @RequestParam(value = "productType", required = false) String productType) {
        List<FormTemplateDO> list = formTemplateService.getPublishedFormTemplateList(productType);
        return success(BeanUtils.toBean(list, FormTemplateRespVO.class));
    }
}
