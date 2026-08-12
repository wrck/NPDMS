package cn.iocoder.yudao.module.pms.service.controller.admin.srvissue;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueActionReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueAssignReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssuePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueRespVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvissue.SrvIssueDO;
import cn.iocoder.yudao.module.pms.service.service.srvissue.SrvIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 巡检问题与整改")
@RestController
@RequestMapping("/pms/srv-issue")
@Validated
public class SrvIssueController {

    @Resource
    private SrvIssueService srvIssueService;

    @PostMapping("/create")
    @Operation(summary = "创建巡检问题")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:create')")
    public CommonResult<Long> createSrvIssue(@Valid @RequestBody SrvIssueSaveReqVO createReqVO) {
        return success(srvIssueService.createSrvIssue(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新巡检问题")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:update')")
    public CommonResult<Boolean> updateSrvIssue(@Valid @RequestBody SrvIssueSaveReqVO updateReqVO) {
        srvIssueService.updateSrvIssue(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除巡检问题")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:delete')")
    public CommonResult<Boolean> deleteSrvIssue(@RequestParam("id") Long id) {
        srvIssueService.deleteSrvIssue(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得巡检问题分页")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:query')")
    public CommonResult<PageResult<SrvIssueRespVO>> getSrvIssuePage(@Validated SrvIssuePageReqVO pageReqVO) {
        PageResult<SrvIssueDO> pageResult = srvIssueService.getSrvIssuePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SrvIssueRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得巡检问题")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:query')")
    public CommonResult<SrvIssueRespVO> getSrvIssue(@RequestParam("id") Long id) {
        SrvIssueDO issue = srvIssueService.getSrvIssue(id);
        return success(BeanUtils.toBean(issue, SrvIssueRespVO.class));
    }

    @GetMapping("/list-by-task")
    @Operation(summary = "根据任务编号获得巡检问题列表")
    @Parameter(name = "taskId", description = "任务编号", required = true, example = "100")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:query')")
    public CommonResult<List<SrvIssueRespVO>> getSrvIssueListByTask(@RequestParam("taskId") Long taskId) {
        List<SrvIssueDO> list = srvIssueService.getSrvIssueListByTask(taskId);
        return success(BeanUtils.toBean(list, SrvIssueRespVO.class));
    }

    @PutMapping("/assign")
    @Operation(summary = "分派问题")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:update')")
    public CommonResult<Boolean> assignIssue(@Valid @RequestBody SrvIssueAssignReqVO reqVO) {
        srvIssueService.assignIssue(reqVO);
        return success(true);
    }

    @PutMapping("/resolve")
    @Operation(summary = "提交整改方案")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:update')")
    public CommonResult<Boolean> resolveIssue(@Valid @RequestBody SrvIssueActionReqVO reqVO) {
        srvIssueService.resolveIssue(reqVO);
        return success(true);
    }

    @PutMapping("/verify")
    @Operation(summary = "验证问题")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:update')")
    public CommonResult<Boolean> verifyIssue(@Valid @RequestBody SrvIssueActionReqVO reqVO) {
        srvIssueService.verifyIssue(reqVO);
        return success(true);
    }

    @GetMapping("/validate-closure")
    @Operation(summary = "校验巡检闭环")
    @Parameter(name = "taskId", description = "任务编号", required = true, example = "100")
    @PreAuthorize("@ss.hasPermission('pms:srv-issue:query')")
    public CommonResult<Boolean> validateInspectionClosure(@RequestParam("taskId") Long taskId) {
        return success(srvIssueService.validateInspectionClosure(taskId));
    }

}
