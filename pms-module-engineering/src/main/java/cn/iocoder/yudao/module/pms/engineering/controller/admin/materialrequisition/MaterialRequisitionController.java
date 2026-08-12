package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialrequisition.MaterialRequisitionDO;
import cn.iocoder.yudao.module.pms.engineering.service.materialrequisition.MaterialRequisitionService;
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
 * 管理后台 - PMS OA领料申请 Controller（FR-ENG-002）。
 * <p>
 * 路径前缀 {@code /pms/eng-material-req}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-material-req:*}。
 */
@Tag(name = "管理后台 - PMS OA领料申请")
@RestController
@RequestMapping("/pms/eng-material-req")
@Validated
public class MaterialRequisitionController {

    @Resource
    private MaterialRequisitionService materialRequisitionService;

    @PostMapping("/create")
    @Operation(summary = "创建领料申请")
    @PreAuthorize("@ss.hasPermission('pms:eng-material-req:create')")
    public CommonResult<Long> createMaterialRequisition(@Valid @RequestBody MaterialRequisitionSaveReqVO createReqVO) {
        return success(materialRequisitionService.createMaterialRequisition(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新领料申请")
    @PreAuthorize("@ss.hasPermission('pms:eng-material-req:update')")
    public CommonResult<Boolean> updateMaterialRequisition(@Valid @RequestBody MaterialRequisitionSaveReqVO updateReqVO) {
        materialRequisitionService.updateMaterialRequisition(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除领料申请")
    @Parameter(name = "id", description = "领料申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-req:delete')")
    public CommonResult<Boolean> deleteMaterialRequisition(@RequestParam("id") Long id) {
        materialRequisitionService.deleteMaterialRequisition(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询领料申请详情")
    @Parameter(name = "id", description = "领料申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-req:query')")
    public CommonResult<MaterialRequisitionRespVO> getMaterialRequisition(@RequestParam("id") Long id) {
        MaterialRequisitionDO entity = materialRequisitionService.getMaterialRequisition(id);
        return success(BeanUtils.toBean(entity, MaterialRequisitionRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询领料申请")
    @PreAuthorize("@ss.hasPermission('pms:eng-material-req:query')")
    public CommonResult<PageResult<MaterialRequisitionRespVO>> getMaterialRequisitionPage(@Validated MaterialRequisitionPageReqVO pageReqVO) {
        PageResult<MaterialRequisitionDO> pageResult = materialRequisitionService.getMaterialRequisitionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, MaterialRequisitionRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交领料申请（0 草稿 / 4 已驳回 → 1 已提交）")
    @Parameter(name = "id", description = "领料申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-req:update')")
    public CommonResult<Boolean> submitMaterialRequisition(@RequestParam("id") Long id) {
        materialRequisitionService.submitMaterialRequisition(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批领料申请（1 已提交 / 2 审批中 → 3/4/0/2）")
    @PreAuthorize("@ss.hasPermission('pms:eng-material-req:update')")
    public CommonResult<Boolean> approveMaterialRequisition(@Valid @RequestBody MaterialRequisitionApproveReqVO reqVO) {
        materialRequisitionService.approveMaterialRequisition(reqVO);
        return success(true);
    }

    @PutMapping("/withdraw")
    @Operation(summary = "撤回领料申请（1 已提交 / 2 审批中 → 5 已撤回）")
    @Parameter(name = "id", description = "领料申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-req:update')")
    public CommonResult<Boolean> withdrawMaterialRequisition(@RequestParam("id") Long id) {
        materialRequisitionService.withdrawMaterialRequisition(id);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "终止领料申请（非 3 已通过 / 非 6 已终止 → 6 已终止）")
    @Parameter(name = "id", description = "领料申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-req:update')")
    public CommonResult<Boolean> terminateMaterialRequisition(@RequestParam("id") Long id) {
        materialRequisitionService.terminateMaterialRequisition(id);
        return success(true);
    }
}
