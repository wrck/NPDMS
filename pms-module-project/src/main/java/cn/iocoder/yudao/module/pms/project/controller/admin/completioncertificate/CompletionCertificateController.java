package cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate.vo.CompletionCertificatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate.vo.CompletionCertificateRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate.vo.CompletionCertificateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.project.service.completioncertificate.CompletionCertificateService;
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
 * 电子完工证明 Controller
 * <p>
 * 【待确认：法律效力口径】电子完工证明的法律效力以公司法务口径为准，本实现仅承载流程数据。
 */
@Tag(name = "管理后台 - 电子完工证明")
@RestController
@RequestMapping("/pms/acc-completion-certificate")
@Validated
public class CompletionCertificateController {

    @Resource
    private CompletionCertificateService completionCertificateService;

    @PostMapping("/create")
    @Operation(summary = "创建电子完工证明")
    @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:create')")
    public CommonResult<Long> create(@Valid @RequestBody CompletionCertificateSaveReqVO createReqVO) {
        return success(completionCertificateService.createCompletionCertificate(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新电子完工证明")
    @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody CompletionCertificateSaveReqVO updateReqVO) {
        completionCertificateService.updateCompletionCertificate(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除电子完工证明")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        completionCertificateService.deleteCompletionCertificate(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得电子完工证明分页")
    @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:query')")
    public CommonResult<PageResult<CompletionCertificateRespVO>> getPage(@Validated CompletionCertificatePageReqVO pageReqVO) {
        PageResult<CompletionCertificateDO> pageResult = completionCertificateService.getCompletionCertificatePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CompletionCertificateRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得电子完工证明")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:query')")
    public CommonResult<CompletionCertificateRespVO> get(@RequestParam("id") Long id) {
        CompletionCertificateDO entity = completionCertificateService.getCompletionCertificate(id);
        return success(BeanUtils.toBean(entity, CompletionCertificateRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交电子完工证明（0草稿 → 1待客户确认）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        completionCertificateService.submitCompletionCertificate(id);
        return success(true);
    }

    @PutMapping("/customer-confirm")
    @Operation(summary = "客户确认（1待客户确认 → 2客户已确认）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:audit')")
    public CommonResult<Boolean> customerConfirm(@RequestParam("id") Long id) {
        completionCertificateService.customerConfirm(id);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回（1待客户确认 → 4已驳回）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:audit')")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id) {
        completionCertificateService.rejectCompletionCertificate(id);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档（2客户已确认 → 3已归档）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:acc-completion-certificate:audit')")
    public CommonResult<Boolean> archive(@RequestParam("id") Long id) {
        completionCertificateService.archiveCompletionCertificate(id);
        return success(true);
    }

}
