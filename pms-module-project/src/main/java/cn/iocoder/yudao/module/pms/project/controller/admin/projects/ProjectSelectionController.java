package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceShipmentApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.service.projectselection.ProjectSelectionQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/projects")
@Validated
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectSelectionController {
    private final ProjectSelectionQueryService queries;

    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi activeUserSelectionApi;

    @GetMapping("/member-filter-options")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<cn.iocoder.yudao.framework.common.pojo.PageResult<cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi.MemberOption>> memberFilterOptions(
            @Valid cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectMemberFilterPageReqVO query,
            jakarta.servlet.http.HttpServletRequest request) {
        if (!java.util.Set.of("field", "keyword", "pageNo", "pageSize").containsAll(request.getParameterMap().keySet()))
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST);
        return success(activeUserSelectionApi.pageProjectMembers(
                new cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi.MemberFilterQuery(
                        query.getPageNo(), query.getPageSize(), query.getKeyword(), query.getField())));
    }


    @GetMapping("/options")
    public CommonResult<List<ProjectBusinessContextApi.Option>> options(
            @RequestParam(value = "keyword", required = false) @Size(max = 128) String keyword) {
        return success(queries.options(new ProjectSelectionQueryService.OptionsQuery(actor(), keyword)));
    }

    @GetMapping("/{projectId}/context")
    public CommonResult<ProjectSelectionQueryService.ProjectContext> context(@PathVariable("projectId") @Positive Long projectId) {
        return success(queries.context(new ProjectSelectionQueryService.ProjectQuery(actor(), projectId)));
    }

    @Data
    public static class DevicePageRequest {
        @Size(max = 128) private String keyword;
        @NotNull @Min(1) private Integer pageNo = 1;
        @NotNull @Min(1) @Max(100) private Integer pageSize = 20;
    }

    @GetMapping("/{projectId}/devices")
    public CommonResult<ProjectDeviceShipmentApi.DevicePage> devices(
            @PathVariable("projectId") @Positive Long projectId, @Valid DevicePageRequest request) {
        return success(queries.devices(new ProjectSelectionQueryService.DevicePageQuery(actor(), projectId,
                request.getKeyword(), request.getPageNo(), request.getPageSize())));
    }

    private static ProjectSelectionQueryService.Actor actor() {
        return new ProjectSelectionQueryService.Actor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId());
    }
}
