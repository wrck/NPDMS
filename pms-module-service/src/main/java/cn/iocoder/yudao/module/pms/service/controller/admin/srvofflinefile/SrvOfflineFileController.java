package cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFilePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFileRespVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFileSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvofflinefile.SrvOfflineFileDO;
import cn.iocoder.yudao.module.pms.service.service.srvofflinefile.SrvOfflineFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 离线巡检文件")
@RestController
@RequestMapping("/pms/srv-offline-file")
@Validated
public class SrvOfflineFileController {

    @Resource
    private SrvOfflineFileService srvOfflineFileService;

    @PostMapping("/create")
    @Operation(summary = "创建离线巡检文件")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:create')")
    public CommonResult<Long> createSrvOfflineFile(@Valid @RequestBody SrvOfflineFileSaveReqVO createReqVO) {
        return success(srvOfflineFileService.createSrvOfflineFile(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新离线巡检文件")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> updateSrvOfflineFile(@Valid @RequestBody SrvOfflineFileSaveReqVO updateReqVO) {
        srvOfflineFileService.updateSrvOfflineFile(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除离线巡检文件")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:delete')")
    public CommonResult<Boolean> deleteSrvOfflineFile(@RequestParam("id") Long id) {
        srvOfflineFileService.deleteSrvOfflineFile(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得离线巡检文件分页")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:query')")
    public CommonResult<PageResult<SrvOfflineFileRespVO>> getSrvOfflineFilePage(@Validated SrvOfflineFilePageReqVO pageReqVO) {
        PageResult<SrvOfflineFileDO> pageResult = srvOfflineFileService.getSrvOfflineFilePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SrvOfflineFileRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得离线巡检文件")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:query')")
    public CommonResult<SrvOfflineFileRespVO> getSrvOfflineFile(@RequestParam("id") Long id) {
        SrvOfflineFileDO offlineFile = srvOfflineFileService.getSrvOfflineFile(id);
        return success(BeanUtils.toBean(offlineFile, SrvOfflineFileRespVO.class));
    }

    @PutMapping("/start-parse")
    @Operation(summary = "开始解析")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> startParse(@RequestParam("id") Long id) {
        srvOfflineFileService.startParse(id);
        return success(true);
    }

    @PutMapping("/parse-success")
    @Operation(summary = "解析成功")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> parseSuccess(@RequestParam("id") Long id) {
        srvOfflineFileService.parseSuccess(id);
        return success(true);
    }

    @PutMapping("/parse-failed")
    @Operation(summary = "解析失败")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-task:update')")
    public CommonResult<Boolean> parseFailed(@RequestParam("id") Long id) {
        srvOfflineFileService.parseFailed(id);
        return success(true);
    }

}
