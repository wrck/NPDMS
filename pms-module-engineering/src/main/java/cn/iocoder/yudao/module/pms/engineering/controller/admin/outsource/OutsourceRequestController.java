package cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.OutsourceRequestDO;
import cn.iocoder.yudao.module.pms.engineering.service.outsource.OutsourceRequestService;
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
 * 管理后台 - PMS 外包申请 Controller（FR-ENG-002）。
 * <p>
 * 路径前缀 {@code /pms/eng-outsource}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-outsource:*}。
 */
@Tag(name = "管理后台 - PMS 外包申请")
@RestController
@RequestMapping("/pms/eng-outsource")
@Validated
public class OutsourceRequestController {

    @Resource
    private OutsourceRequestService outsourceRequestService;

    @PostMapping("/create")
    @Operation(summary = "创建外包申请")
    @PreAuthorize("@ss.hasPermission('pms:eng-outsource:create')")
    public CommonResult<Long> createOutsourceRequest(@Valid @RequestBody OutsourceRequestSaveReqVO createReqVO) {
        return success(outsourceRequestService.createOutsourceRequest(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新外包申请")
    @PreAuthorize("@ss.hasPermission('pms:eng-outsource:update')")
    public CommonResult<Boolean> updateOutsourceRequest(@Valid @RequestBody OutsourceRequestSaveReqVO updateReqVO) {
        outsourceRequestService.updateOutsourceRequest(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除外包申请")
    @Parameter(name = "id", description = "外包申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-outsource:delete')")
    public CommonResult<Boolean> deleteOutsourceRequest(@RequestParam("id") Long id) {
        outsourceRequestService.deleteOutsourceRequest(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询外包申请详情")
    @Parameter(name = "id", description = "外包申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-outsource:query')")
    public CommonResult<OutsourceRequestRespVO> getOutsourceRequest(@RequestParam("id") Long id) {
        OutsourceRequestDO entity = outsourceRequestService.getOutsourceRequest(id);
        return success(BeanUtils.toBean(entity, OutsourceRequestRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询外包申请")
    @PreAuthorize("@ss.hasPermission('pms:eng-outsource:query')")
    public CommonResult<PageResult<OutsourceRequestRespVO>> getOutsourceRequestPage(@Validated OutsourceRequestPageReqVO pageReqVO) {
        PageResult<OutsourceRequestDO> pageResult = outsourceRequestService.getOutsourceRequestPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, OutsourceRequestRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交外包申请（0 草稿 / 4 已驳回 → 1 已提交）")
    @Parameter(name = "id", description = "外包申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-outsource:update')")
    public CommonResult<Boolean> submitOutsourceRequest(@RequestParam("id") Long id) {
        outsourceRequestService.submitOutsourceRequest(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批外包申请（1 已提交 / 2 审批中 → 3/4/0/2）")
    @PreAuthorize("@ss.hasPermission('pms:eng-outsource:update')")
    public CommonResult<Boolean> approveOutsourceRequest(@Valid @RequestBody OutsourceRequestApproveReqVO reqVO) {
        outsourceRequestService.approveOutsourceRequest(reqVO);
        return success(true);
    }

    @PutMapping("/withdraw")
    @Operation(summary = "撤回外包申请（1 已提交 / 2 审批中 → 5 已撤回）")
    @Parameter(name = "id", description = "外包申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-outsource:update')")
    public CommonResult<Boolean> withdrawOutsourceRequest(@RequestParam("id") Long id) {
        outsourceRequestService.withdrawOutsourceRequest(id);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "终止外包申请（非 3 已通过 / 非 6 已终止 → 6 已终止）")
    @Parameter(name = "id", description = "外包申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-outsource:update')")
    public CommonResult<Boolean> terminateOutsourceRequest(@RequestParam("id") Long id) {
        outsourceRequestService.terminateOutsourceRequest(id);
        return success(true);
    }
}
