package cn.iocoder.yudao.module.pms.engineering.controller.admin.issue;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssuePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssueRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssueSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssueVerifyReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.issue.IssueDO;
import cn.iocoder.yudao.module.pms.engineering.service.issue.IssueService;
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
 * 管理后台 - PMS 实施问题 Controller（FR-ENG-026）。
 * <p>
 * 路径前缀 {@code /pms/eng-issue}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-issue:*}。
 * 未关闭问题阻断项目验收，由 {@code validateProjectAcceptance} 提供。
 */
@Tag(name = "管理后台 - PMS 实施问题")
@RestController
@RequestMapping("/pms/eng-issue")
@Validated
public class IssueController {

    @Resource
    private IssueService issueService;

    @PostMapping("/create")
    @Operation(summary = "创建问题")
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:create')")
    public CommonResult<Long> createIssue(@Valid @RequestBody IssueSaveReqVO createReqVO) {
        return success(issueService.createIssue(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新问题（已关闭不允许修改）")
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:update')")
    public CommonResult<Boolean> updateIssue(@Valid @RequestBody IssueSaveReqVO updateReqVO) {
        issueService.updateIssue(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除问题")
    @Parameter(name = "id", description = "问题编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:delete')")
    public CommonResult<Boolean> deleteIssue(@RequestParam("id") Long id) {
        issueService.deleteIssue(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询问题详情")
    @Parameter(name = "id", description = "问题编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:query')")
    public CommonResult<IssueRespVO> getIssue(@RequestParam("id") Long id) {
        IssueDO entity = issueService.getIssue(id);
        return success(BeanUtils.toBean(entity, IssueRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询问题")
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:query')")
    public CommonResult<PageResult<IssueRespVO>> getIssuePage(@Validated IssuePageReqVO pageReqVO) {
        PageResult<IssueDO> pageResult = issueService.getIssuePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, IssueRespVO.class));
    }

    @PutMapping("/start-rectify")
    @Operation(summary = "开始整改（0待处理 → 1整改中）")
    @Parameter(name = "id", description = "问题编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:update')")
    public CommonResult<Boolean> startRectify(@RequestParam("id") Long id) {
        issueService.startRectify(id);
        return success(true);
    }

    @PutMapping("/submit-for-verify")
    @Operation(summary = "提交验证（1整改中 → 2待验证）")
    @Parameter(name = "id", description = "问题编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:update')")
    public CommonResult<Boolean> submitForVerify(@RequestParam("id") Long id) {
        issueService.submitForVerify(id);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭问题（2待验证 → 3已关闭，需复测结果）")
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:verify')")
    public CommonResult<Boolean> close(@Valid @RequestBody IssueVerifyReqVO reqVO) {
        issueService.close(reqVO);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "验证驳回（2待验证 → 1整改中，需驳回原因）")
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:verify')")
    public CommonResult<Boolean> reject(@Valid @RequestBody IssueVerifyReqVO reqVO) {
        issueService.reject(reqVO);
        return success(true);
    }

    @PutMapping("/suspend")
    @Operation(summary = "挂起（任意非终态 → 4已挂起）")
    @Parameter(name = "id", description = "问题编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:update')")
    public CommonResult<Boolean> suspend(@RequestParam("id") Long id) {
        issueService.suspend(id);
        return success(true);
    }

    @PutMapping("/resume")
    @Operation(summary = "恢复（4已挂起 → 1整改中）")
    @Parameter(name = "id", description = "问题编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:update')")
    public CommonResult<Boolean> resume(@RequestParam("id") Long id) {
        issueService.resume(id);
        return success(true);
    }

    @GetMapping("/validate-acceptance")
    @Operation(summary = "验收门禁：检查项目是否存在未关闭问题")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-issue:query')")
    public CommonResult<Boolean> validateAcceptance(@RequestParam("projectId") Long projectId) {
        issueService.validateProjectAcceptance(projectId);
        return success(true);
    }
}
