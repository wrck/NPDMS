package cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.externalprocurement.ExternalProcurementDO;
import cn.iocoder.yudao.module.pms.engineering.service.externalprocurement.ExternalProcurementService;
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
 * 管理后台 - PMS 外采申请 Controller（FR-ENG-002）。
 * <p>
 * 路径前缀 {@code /pms/eng-ext-proc}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-ext-proc:*}。
 */
@Tag(name = "管理后台 - PMS 外采申请")
@RestController
@RequestMapping("/pms/eng-ext-proc")
@Validated
public class ExternalProcurementController {

    @Resource
    private ExternalProcurementService externalProcurementService;

    @PostMapping("/create")
    @Operation(summary = "创建外采申请")
    @PreAuthorize("@ss.hasPermission('pms:eng-ext-proc:create')")
    public CommonResult<Long> createExternalProcurement(@Valid @RequestBody ExternalProcurementSaveReqVO createReqVO) {
        return success(externalProcurementService.createExternalProcurement(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新外采申请")
    @PreAuthorize("@ss.hasPermission('pms:eng-ext-proc:update')")
    public CommonResult<Boolean> updateExternalProcurement(@Valid @RequestBody ExternalProcurementSaveReqVO updateReqVO) {
        externalProcurementService.updateExternalProcurement(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除外采申请")
    @Parameter(name = "id", description = "外采申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-ext-proc:delete')")
    public CommonResult<Boolean> deleteExternalProcurement(@RequestParam("id") Long id) {
        externalProcurementService.deleteExternalProcurement(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询外采申请详情")
    @Parameter(name = "id", description = "外采申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-ext-proc:query')")
    public CommonResult<ExternalProcurementRespVO> getExternalProcurement(@RequestParam("id") Long id) {
        ExternalProcurementDO entity = externalProcurementService.getExternalProcurement(id);
        return success(BeanUtils.toBean(entity, ExternalProcurementRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询外采申请")
    @PreAuthorize("@ss.hasPermission('pms:eng-ext-proc:query')")
    public CommonResult<PageResult<ExternalProcurementRespVO>> getExternalProcurementPage(@Validated ExternalProcurementPageReqVO pageReqVO) {
        PageResult<ExternalProcurementDO> pageResult = externalProcurementService.getExternalProcurementPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ExternalProcurementRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交外采申请（0 草稿 / 4 已驳回 → 1 已提交）")
    @Parameter(name = "id", description = "外采申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-ext-proc:update')")
    public CommonResult<Boolean> submitExternalProcurement(@RequestParam("id") Long id) {
        externalProcurementService.submitExternalProcurement(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批外采申请（1 已提交 / 2 审批中 → 3/4/0/2）")
    @PreAuthorize("@ss.hasPermission('pms:eng-ext-proc:update')")
    public CommonResult<Boolean> approveExternalProcurement(@Valid @RequestBody ExternalProcurementApproveReqVO reqVO) {
        externalProcurementService.approveExternalProcurement(reqVO);
        return success(true);
    }

    @PutMapping("/withdraw")
    @Operation(summary = "撤回外采申请（1 已提交 / 2 审批中 → 5 已撤回）")
    @Parameter(name = "id", description = "外采申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-ext-proc:update')")
    public CommonResult<Boolean> withdrawExternalProcurement(@RequestParam("id") Long id) {
        externalProcurementService.withdrawExternalProcurement(id);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "终止外采申请（非 3 已通过 / 非 6 已终止 → 6 已终止）")
    @Parameter(name = "id", description = "外采申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-ext-proc:update')")
    public CommonResult<Boolean> terminateExternalProcurement(@RequestParam("id") Long id) {
        externalProcurementService.terminateExternalProcurement(id);
        return success(true);
    }
}
