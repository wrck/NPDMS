package cn.iocoder.yudao.module.pms.cutover.controller.business.configuration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationValidationRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverExternalCatalogRespVO;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationService;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationCommands;
import cn.iocoder.yudao.module.pms.cutover.controller.business.support.CutoverRequestHeaders;
import cn.iocoder.yudao.module.pms.cutover.controller.business.support.CutoverErrorData;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.springframework.http.HttpHeaders;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverNavigationRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.CUTOVER_NAVIGATION_RULE_INVALID;

@Tag(name = "管理后台 - CUT-07割接统一配置")
@RestController
@RequestMapping("/api/v1/pms/cutover-configurations")
@Validated
public class CutoverConfigurationController {

    @Resource
    private CutoverConfigurationService service;
    @Resource
    private cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverExternalDataDirectory externalDataDirectory;

    @GetMapping("/external-interfaces")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:query')")
    public CommonResult<CutoverExternalCatalogRespVO> externalInterfaces() {
        return success(CutoverExternalCatalogRespVO.from(externalDataDirectory.catalog()));
    }

    @GetMapping("/revisions")
    @Operation(summary = "分页查询配置修订")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:query')")
    public CommonResult<PageResult<CutoverConfigurationRespVO>> page(@Valid CutoverConfigurationPageReqVO request) {
        return success(service.getPage(request));
    }

    @GetMapping("/revisions/{revisionId}")
    @Operation(summary = "查询配置修订详情")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:query')")
    public CommonResult<CutoverConfigurationRespVO> get(@PathVariable Long revisionId) {
        return success(service.get(revisionId));
    }

    @PostMapping("/revisions")
    @Operation(summary = "创建配置草稿")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:manage')")
    public CommonResult<Long> create(@RequestHeader HttpHeaders headers,
                                     @Valid @RequestBody CutoverConfigurationSaveReqVO request) {
        return success(service.create(new CutoverConfigurationCommands.Create(request, CutoverRequestHeaders.intent(headers))));
    }

    @PutMapping("/revisions/{revisionId}")
    @Operation(summary = "保存完整配置草稿")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:manage')")
    public CommonResult<Boolean> update(@PathVariable Long revisionId,
                                        @RequestHeader HttpHeaders headers,
                                        @Valid @RequestBody CutoverConfigurationSaveReqVO request) {
        service.update(new CutoverConfigurationCommands.Update(revisionId,
                CutoverRequestHeaders.version(headers, "If-Match"), request, CutoverRequestHeaders.intent(headers)));
        return success(true);
    }

    @PostMapping("/revisions/{revisionId}/actions/copy")
    @Operation(summary = "复制配置为新草稿修订")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:manage')")
    public CommonResult<Long> copy(@PathVariable Long revisionId,
                                   @RequestHeader HttpHeaders headers) {
        return success(service.copyRevision(new CutoverConfigurationCommands.RevisionAction(revisionId,
                CutoverRequestHeaders.version(headers, "If-Match"), CutoverRequestHeaders.intent(headers))));
    }

    @PostMapping("/revisions/{revisionId}/actions/validate")
    @Operation(summary = "执行发布预检")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:manage')")
    public CommonResult<CutoverConfigurationValidationRespVO> validate(@PathVariable Long revisionId) {
        return success(service.validate(revisionId));
    }

    @PostMapping("/revisions/{revisionId}/actions/publish")
    @Operation(summary = "发布配置修订")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:publish')")
    public CommonResult<CutoverConfigurationRespVO> publish(@PathVariable Long revisionId,
                                                            @RequestHeader HttpHeaders headers) {
        return success(service.publish(new CutoverConfigurationCommands.RevisionAction(revisionId,
                CutoverRequestHeaders.version(headers, "If-Match"), CutoverRequestHeaders.intent(headers))));
    }

    @PostMapping("/revisions/{revisionId}/actions/disable")
    @Operation(summary = "停用已发布配置修订")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:disable')")
    public CommonResult<CutoverConfigurationRespVO> disable(@PathVariable Long revisionId,
                                                            @RequestHeader HttpHeaders headers) {
        return success(service.disable(new CutoverConfigurationCommands.RevisionAction(revisionId,
                CutoverRequestHeaders.version(headers, "If-Match"), CutoverRequestHeaders.intent(headers))));
    }

    @ExceptionHandler(CutoverNavigationRuleException.class)
    public ResponseEntity<CommonResult<CutoverErrorData>> handleNavigationRule(CutoverNavigationRuleException exception) {
        var body = CommonResult.<CutoverErrorData>error(CUTOVER_NAVIGATION_RULE_INVALID.getCode(), exception.getMessage());
        body.setData(new CutoverErrorData("INVALID_REQUEST", "NAVIGATION_RULE_INVALID", "FIX_REQUEST"));
        return ResponseEntity.unprocessableEntity().body(body);
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<CommonResult<CutoverErrorData>> handleConfiguration(ServiceException exception) {
        int offset = exception.getCode() - 1_012_004_000;
        int status = switch (offset) {
            case 0 -> 404;
            case 2, 3, 5 -> 409;
            case 1, 4, 6 -> 422;
            default -> 400;
        };
        String reason = switch (offset) {
            case 0 -> "CONFIGURATION_NOT_FOUND";
            case 1 -> "CONFIGURATION_CODE_CHANGED";
            case 2 -> "CONFIGURATION_NOT_EDITABLE";
            case 3 -> "CONFIGURATION_VERSION_CONFLICT";
            case 4 -> "CONFIGURATION_VALIDATION_FAILED";
            case 5 -> "CONFIGURATION_STATUS_INVALID";
            case 6 -> "NAVIGATION_RULE_INVALID";
            default -> "CONFIGURATION_REFERENCE_INVALID";
        };
        var body = CommonResult.<CutoverErrorData>error(exception.getCode(), exception.getMessage());
        body.setData(new CutoverErrorData(status == 409 ? "CONFLICT" : "INVALID_REQUEST", reason,
                status == 409 ? "REFRESH_AGGREGATE" : status == 404 ? "RETURN_TO_LIST" : "FIX_REQUEST"));
        return ResponseEntity.status(status).body(body);
    }
}
