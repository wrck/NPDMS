package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormPublishRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormRevisionCreatedRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormRevisionPatchReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormRevisionPatchedRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormRevisionRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormSelectionRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormTemplateCreateReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormTemplateCommandRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormTemplateCreatedRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormTemplatePatchReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormTemplateRespVO;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormCommandService;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormCommands;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;

@Tag(name = "管理后台 - PMS 共享动态表单模板")
@RestController
@RequestMapping("/api/v1/pms")
@Validated
@RequiredArgsConstructor
public class DynamicFormTemplateController {

    private final DynamicFormCommandService commandService;
    private final DynamicFormQueryService queryService;
    private final Environment environment;

    @GetMapping("/dynamic-form-templates")
    @Operation(summary = "分页查询动态表单模板")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:query')")
    public CommonResult<PageResult<DynamicFormTemplateRespVO>> page(@Valid DynamicFormTemplatePageReqVO request) {
        return trusted(() -> {
            var result = queryService.pageTemplates(actor(), request.getPageNo(), request.getPageSize());
            return success(new PageResult<>(result.list().stream().map(DynamicFormTemplateRespVO::of).toList(),
                    result.total()));
        });
    }

    @PostMapping("/dynamic-form-templates")
    @Operation(summary = "创建动态表单模板及初始草稿")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:manage')")
    public CommonResult<DynamicFormTemplateCreatedRespVO> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody DynamicFormTemplateCreateReqVO request) {
        return trusted(() -> success(DynamicFormTemplateCreatedRespVO.of(commandService.createTemplate(
                new DynamicFormCommands.CreateTemplate(actor(), key, request.getTemplateCode(),
                        request.getTemplateName(), request.getCategoryCode(), request.getDescription())))));
    }

    @GetMapping("/dynamic-form-templates/{templateId}")
    @Operation(summary = "查询动态表单模板详情")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:query')")
    public CommonResult<DynamicFormTemplateRespVO> get(@PathVariable Long templateId) {
        return trusted(() -> success(DynamicFormTemplateRespVO.of(queryService.getTemplate(actor(), templateId))));
    }

    @PatchMapping("/dynamic-form-templates/{templateId}")
    @Operation(summary = "修改动态表单模板元数据")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:manage')")
    public CommonResult<DynamicFormTemplateCommandRespVO> patch(
            @PathVariable Long templateId, @RequestHeader("If-Match") Integer version,
            @RequestBody DynamicFormTemplatePatchReqVO request) {
        return trusted(() -> success(DynamicFormTemplateCommandRespVO.of(commandService.patchTemplate(
                new DynamicFormCommands.PatchTemplate(actor(), templateId, version,
                        field(request.isTemplateNamePresent(), request.getTemplateName()),
                        field(request.isCategoryCodePresent(), request.getCategoryCode()),
                        field(request.isDescriptionPresent(), request.getDescription()), operationId())))));
    }

    @PostMapping("/dynamic-form-templates/{templateId}/revisions")
    @Operation(summary = "从当前发布修订创建下一草稿")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:manage')")
    public CommonResult<DynamicFormRevisionCreatedRespVO> createRevision(
            @PathVariable Long templateId, @RequestHeader("If-Match") Integer version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return trusted(() -> success(DynamicFormRevisionCreatedRespVO.of(commandService.createRevision(
                new DynamicFormCommands.CreateRevision(actor(), templateId, version, key)))));
    }

    @GetMapping("/dynamic-form-template-revisions/{revisionId}")
    @Operation(summary = "按修订标识查询动态表单修订")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:query')")
    public CommonResult<DynamicFormRevisionRespVO> getRevision(@PathVariable Long revisionId) {
        return trusted(() -> success(DynamicFormRevisionRespVO.of(queryService.getRevision(actor(), revisionId))));
    }

    @PatchMapping("/dynamic-form-template-revisions/{revisionId}")
    @Operation(summary = "保存动态表单草稿设计")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:manage')")
    public CommonResult<DynamicFormRevisionPatchedRespVO> patchRevision(
            @PathVariable Long revisionId, @RequestHeader("If-Match") Integer version,
            @Valid @RequestBody DynamicFormRevisionPatchReqVO request) {
        return trusted(() -> success(DynamicFormRevisionPatchedRespVO.of(commandService.patchRevision(
                new DynamicFormCommands.PatchRevision(actor(), revisionId, version,
                        request.getFormConfJson(), request.getFormRulesJson(), request.getEngineCode(),
                        request.getDesignerVersion(), request.getRendererVersion(), operationId())))));
    }

    @PostMapping("/dynamic-form-template-revisions/{revisionId}/actions/publish")
    @Operation(summary = "发布动态表单修订")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:publish')")
    public CommonResult<DynamicFormPublishRespVO> publish(
            @PathVariable Long revisionId, @RequestHeader("If-Match") Integer version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return trusted(() -> success(DynamicFormPublishRespVO.of(commandService.publishRevision(
                new DynamicFormCommands.PublishRevision(actor(), revisionId, version, key)))));
    }

    @PostMapping("/dynamic-form-templates/{templateId}/actions/enable")
    @Operation(summary = "启用动态表单模板")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:publish')")
    public CommonResult<DynamicFormTemplateCommandRespVO> enable(
            @PathVariable Long templateId, @RequestHeader("If-Match") Integer version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return availability(templateId, version, key, "ENABLED");
    }

    @PostMapping("/dynamic-form-templates/{templateId}/actions/disable")
    @Operation(summary = "停用动态表单模板")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-template:publish')")
    public CommonResult<DynamicFormTemplateCommandRespVO> disable(
            @PathVariable Long templateId, @RequestHeader("If-Match") Integer version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return availability(templateId, version, key, "DISABLED");
    }

    @GetMapping("/dynamic-form-templates/selection")
    @Operation(summary = "查询可人工选择的动态表单模板")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-instance:query')")
    public CommonResult<PageResult<DynamicFormSelectionRespVO>> selection(
            @Valid DynamicFormTemplatePageReqVO request) {
        return trusted(() -> {
            var result = queryService.pageSelection(actor(), request.getPageNo(), request.getPageSize());
            return success(new PageResult<>(result.list().stream().map(DynamicFormSelectionRespVO::of).toList(),
                    result.total()));
        });
    }

    private CommonResult<DynamicFormTemplateCommandRespVO> availability(Long templateId, Integer version,
                                                                         String key, String target) {
        return trusted(() -> success(DynamicFormTemplateCommandRespVO.of(commandService.setAvailability(
                new DynamicFormCommands.SetAvailability(actor(), templateId, version, target, key)))));
    }

    private DynamicFormCommands.Actor actor() {
        return new DynamicFormCommands.Actor(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId());
    }

    private DynamicFormCommands.FieldPatch<String> field(boolean present, String value) {
        return present ? DynamicFormCommands.FieldPatch.present(value) : DynamicFormCommands.FieldPatch.absent();
    }

    private String operationId() {
        return java.util.UUID.randomUUID().toString();
    }

    private <T> T trusted(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
