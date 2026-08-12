package cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstanceApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstancePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstanceRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstanceSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.forminstance.FormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.service.forminstance.FormInstanceService;
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

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 准备数据表单实例 Controller（FR-ENG-007）。
 * <p>
 * 路径前缀 {@code /pms/eng-form-instance}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-form-instance:*}。
 */
@Tag(name = "管理后台 - PMS 准备数据表单实例")
@RestController
@RequestMapping("/pms/eng-form-instance")
@Validated
public class FormInstanceController {

    @Resource
    private FormInstanceService formInstanceService;

    @PostMapping("/create")
    @Operation(summary = "创建表单实例")
    @PreAuthorize("@ss.hasPermission('pms:eng-form-instance:create')")
    public CommonResult<Long> createFormInstance(@Valid @RequestBody FormInstanceSaveReqVO createReqVO) {
        return success(formInstanceService.createFormInstance(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新表单实例")
    @PreAuthorize("@ss.hasPermission('pms:eng-form-instance:update')")
    public CommonResult<Boolean> updateFormInstance(@Valid @RequestBody FormInstanceSaveReqVO updateReqVO) {
        formInstanceService.updateFormInstance(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除表单实例")
    @Parameter(name = "id", description = "实例编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-form-instance:delete')")
    public CommonResult<Boolean> deleteFormInstance(@RequestParam("id") Long id) {
        formInstanceService.deleteFormInstance(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询表单实例详情")
    @Parameter(name = "id", description = "实例编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-form-instance:query')")
    public CommonResult<FormInstanceRespVO> getFormInstance(@RequestParam("id") Long id) {
        FormInstanceDO entity = formInstanceService.getFormInstance(id);
        return success(BeanUtils.toBean(entity, FormInstanceRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询表单实例")
    @PreAuthorize("@ss.hasPermission('pms:eng-form-instance:query')")
    public CommonResult<PageResult<FormInstanceRespVO>> getFormInstancePage(@Validated FormInstancePageReqVO pageReqVO) {
        PageResult<FormInstanceDO> pageResult = formInstanceService.getFormInstancePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, FormInstanceRespVO.class));
    }

    @PutMapping("/save")
    @Operation(summary = "保存填报（0 待填 / 4 已驳回 → 1 已填）")
    @PreAuthorize("@ss.hasPermission('pms:eng-form-instance:update')")
    public CommonResult<Boolean> saveFormInstance(@Valid @RequestBody FormInstanceSaveReqVO reqVO) {
        formInstanceService.saveFormInstance(reqVO);
        return success(true);
    }

    @PutMapping("/submit")
    @Operation(summary = "提交表单实例（0 待填 / 1 已填 / 4 已驳回 → 2 已提交）")
    @Parameter(name = "id", description = "实例编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-form-instance:submit')")
    public CommonResult<Boolean> submitFormInstance(@RequestParam("id") Long id) {
        formInstanceService.submitFormInstance(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审核表单实例（2 已提交 → 3 已审核 / 4 已驳回）")
    @PreAuthorize("@ss.hasPermission('pms:eng-form-instance:audit')")
    public CommonResult<Boolean> approveFormInstance(@Valid @RequestBody FormInstanceApproveReqVO reqVO) {
        formInstanceService.approveFormInstance(reqVO);
        return success(true);
    }
}
