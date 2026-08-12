package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationService;
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
 * 管理后台 - 硬件安装 Controller（FR-ENG-022）。
 * <p>
 * 路径前缀 {@code /pms/eng-installation}。
 */
@Tag(name = "管理后台 - 硬件安装")
@RestController
@RequestMapping("/pms/eng-installation")
@Validated
public class InstallationController {

    @Resource
    private InstallationService installationService;

    @PostMapping("/create")
    @Operation(summary = "创建硬件安装记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-installation:create')")
    public CommonResult<Long> createInstallation(@Valid @RequestBody InstallationSaveReqVO createReqVO) {
        return success(installationService.createInstallation(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新硬件安装记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-installation:update')")
    public CommonResult<Boolean> updateInstallation(@Valid @RequestBody InstallationSaveReqVO updateReqVO) {
        installationService.updateInstallation(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除硬件安装记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-installation:delete')")
    public CommonResult<Boolean> deleteInstallation(@RequestParam("id") Long id) {
        installationService.deleteInstallation(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询硬件安装详情")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-installation:query')")
    public CommonResult<InstallationRespVO> getInstallation(@RequestParam("id") Long id) {
        InstallationDO installation = installationService.getInstallation(id);
        return success(BeanUtils.toBean(installation, InstallationRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询硬件安装")
    @PreAuthorize("@ss.hasPermission('pms:eng-installation:query')")
    public CommonResult<PageResult<InstallationRespVO>> getInstallationPage(@Validated InstallationPageReqVO pageReqVO) {
        PageResult<InstallationDO> pageResult = installationService.getInstallationPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, InstallationRespVO.class));
    }

    @PutMapping("/start")
    @Operation(summary = "开始安装")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-installation:update')")
    public CommonResult<Boolean> startInstallation(@RequestParam("id") Long id) {
        installationService.startInstallation(id);
        return success(true);
    }

    @PutMapping("/complete")
    @Operation(summary = "完成安装")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-installation:update')")
    public CommonResult<Boolean> completeInstallation(@RequestParam("id") Long id) {
        installationService.completeInstallation(id);
        return success(true);
    }

    @PutMapping("/mark-abnormal")
    @Operation(summary = "标记安装异常")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-installation:update')")
    public CommonResult<Boolean> markAbnormal(@RequestParam("id") Long id) {
        installationService.markAbnormal(id);
        return success(true);
    }
}
