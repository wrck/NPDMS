package cn.iocoder.yudao.module.pms.service.controller.admin.srvreport;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo.SrvReportPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo.SrvReportRespVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo.SrvReportSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvreport.SrvReportDO;
import cn.iocoder.yudao.module.pms.service.service.srvreport.SrvReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 巡检报告")
@RestController
@RequestMapping("/pms/srv-report")
@Validated
public class SrvReportController {

    @Resource
    private SrvReportService srvReportService;

    @PostMapping("/create")
    @Operation(summary = "创建巡检报告")
    @PreAuthorize("@ss.hasPermission('pms:srv-report:create')")
    public CommonResult<Long> createSrvReport(@Valid @RequestBody SrvReportSaveReqVO createReqVO) {
        return success(srvReportService.createSrvReport(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新巡检报告")
    @PreAuthorize("@ss.hasPermission('pms:srv-report:update')")
    public CommonResult<Boolean> updateSrvReport(@Valid @RequestBody SrvReportSaveReqVO updateReqVO) {
        srvReportService.updateSrvReport(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除巡检报告")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-report:delete')")
    public CommonResult<Boolean> deleteSrvReport(@RequestParam("id") Long id) {
        srvReportService.deleteSrvReport(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得巡检报告分页")
    @PreAuthorize("@ss.hasPermission('pms:srv-report:query')")
    public CommonResult<PageResult<SrvReportRespVO>> getSrvReportPage(@Validated SrvReportPageReqVO pageReqVO) {
        PageResult<SrvReportDO> pageResult = srvReportService.getSrvReportPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SrvReportRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得巡检报告")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-report:query')")
    public CommonResult<SrvReportRespVO> getSrvReport(@RequestParam("id") Long id) {
        SrvReportDO report = srvReportService.getSrvReport(id);
        return success(BeanUtils.toBean(report, SrvReportRespVO.class));
    }

    @PutMapping("/generate")
    @Operation(summary = "生成报告")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-report:update')")
    public CommonResult<Boolean> generateSrvReport(@RequestParam("id") Long id) {
        srvReportService.generateSrvReport(id);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档报告")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-report:update')")
    public CommonResult<Boolean> archiveSrvReport(@RequestParam("id") Long id) {
        srvReportService.archiveSrvReport(id);
        return success(true);
    }

}
