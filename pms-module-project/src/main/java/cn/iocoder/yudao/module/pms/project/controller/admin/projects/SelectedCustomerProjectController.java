package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.SelectedCustomerProjectCreateReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ProjectSiteCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/projects")
@Validated
@RequiredArgsConstructor
public class SelectedCustomerProjectController {
    private final ProjectManualCreationApplicationService creationService;

    // Spring MVC对输入VO执行Bean Validation；不修改旧创建VO的有效输入。
    // https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html
    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ManualProjectCreateResult> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody SelectedCustomerProjectCreateReqVO request) {
        var draft = BeanUtils.toBean(request, ProjectMasterDO.class);
        var sites = request.getSites() == null ? List.<ProjectSiteCommand>of()
                : request.getSites().stream().map(site -> new ProjectSiteCommand(
                        site.getSiteId(), site.getSiteVersion(), site.getPrimarySite())).toList();
        var command = new ManualProjectCreateCommand(draft, request.getOrderOfficeCompanyId(),
                request.getOrderOfficeDepartmentId(), sites, request.getTemplateRevisionId(),
                request.getCandidateWatermark(), request.getServiceManagerUserId(), idempotencyKey,
                DigestUtil.sha256Hex(JsonUtils.toJsonString(request)));
        return success(creationService.createWithSelectedCustomer(command,
                new ProjectManualCreationApplicationService.Actor(
                        TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                        UUID.randomUUID().toString())));
    }
}
