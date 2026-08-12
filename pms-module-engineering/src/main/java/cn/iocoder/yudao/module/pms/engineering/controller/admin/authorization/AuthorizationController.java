package cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.authorization.AuthorizationDO;
import cn.iocoder.yudao.module.pms.engineering.service.authorization.AuthorizationService;
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
 * 管理后台 - PMS 授权与借货 Controller（FR-ENG-010）。
 * <p>
 * 路径前缀 {@code /pms/eng-authorization}，对应菜单权限 {@code pms:eng-authorization:*}。
 */
@Tag(name = "管理后台 - PMS 授权与借货")
@RestController
@RequestMapping("/pms/eng-authorization")
@Validated
public class AuthorizationController {

    @Resource
    private AuthorizationService authorizationService;

    @PostMapping("/create")
    @Operation(summary = "创建授权")
    @PreAuthorize("@ss.hasPermission('pms:eng-authorization:create')")
    public CommonResult<Long> createAuthorization(@Valid @RequestBody AuthorizationSaveReqVO createReqVO) {
        return success(authorizationService.createAuthorization(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新授权")
    @PreAuthorize("@ss.hasPermission('pms:eng-authorization:update')")
    public CommonResult<Boolean> updateAuthorization(@Valid @RequestBody AuthorizationSaveReqVO updateReqVO) {
        authorizationService.updateAuthorization(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除授权")
    @Parameter(name = "id", description = "授权ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-authorization:delete')")
    public CommonResult<Boolean> deleteAuthorization(@RequestParam("id") Long id) {
        authorizationService.deleteAuthorization(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询授权详情")
    @Parameter(name = "id", description = "授权ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-authorization:query')")
    public CommonResult<AuthorizationRespVO> getAuthorization(@RequestParam("id") Long id) {
        AuthorizationDO entity = authorizationService.getAuthorization(id);
        return success(BeanUtils.toBean(entity, AuthorizationRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询授权")
    @PreAuthorize("@ss.hasPermission('pms:eng-authorization:query')")
    public CommonResult<PageResult<AuthorizationRespVO>> getAuthorizationPage(@Validated AuthorizationPageReqVO pageReqVO) {
        PageResult<AuthorizationDO> pageResult = authorizationService.getAuthorizationPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, AuthorizationRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交授权（0 草稿 / 4 已驳回 / 5 已撤回 → 1 已提交 → 2 审批中）")
    @Parameter(name = "id", description = "授权ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-authorization:submit')")
    public CommonResult<Boolean> submitAuthorization(@RequestParam("id") Long id) {
        authorizationService.submitAuthorization(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批授权（2 审批中 → 3 已通过 / 4 已驳回 / 6 已终止）")
    @PreAuthorize("@ss.hasPermission('pms:eng-authorization:audit')")
    public CommonResult<Boolean> approveAuthorization(@Valid @RequestBody AuthorizationApproveReqVO reqVO) {
        authorizationService.approveAuthorization(reqVO);
        return success(true);
    }

    @PutMapping("/recall")
    @Operation(summary = "撤回授权（1 已提交 / 2 审批中 → 5 已撤回）")
    @Parameter(name = "id", description = "授权ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-authorization:recall')")
    public CommonResult<Boolean> recallAuthorization(@RequestParam("id") Long id) {
        authorizationService.recallAuthorization(id);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "终止授权（3 已通过 → 6 已终止）")
    @Parameter(name = "id", description = "授权ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-authorization:terminate')")
    public CommonResult<Boolean> terminateAuthorization(@RequestParam("id") Long id) {
        authorizationService.terminateAuthorization(id);
        return success(true);
    }
}
