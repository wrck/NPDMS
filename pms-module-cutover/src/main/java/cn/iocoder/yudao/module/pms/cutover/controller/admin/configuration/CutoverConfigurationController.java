package cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationValidationRespVO;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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

@Tag(name = "管理后台 - CUT-07割接统一配置")
@RestController
@RequestMapping("/api/v1/pms/cutover-config")
@Validated
public class CutoverConfigurationController {

    @Resource
    private CutoverConfigurationService service;

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
    public CommonResult<Long> create(@Valid @RequestBody CutoverConfigurationSaveReqVO request) {
        return success(service.create(request));
    }

    @PutMapping("/revisions/{revisionId}")
    @Operation(summary = "保存完整配置草稿")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:manage')")
    public CommonResult<Boolean> update(@PathVariable Long revisionId,
                                        @RequestHeader("If-Match") Integer expectedVersion,
                                        @Valid @RequestBody CutoverConfigurationSaveReqVO request) {
        service.update(revisionId, expectedVersion, request);
        return success(true);
    }

    @PostMapping("/revisions/{revisionId}/actions/copy")
    @Operation(summary = "复制配置为新草稿修订")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:manage')")
    public CommonResult<Long> copy(@PathVariable Long revisionId,
                                   @RequestHeader("If-Match") Integer expectedVersion) {
        return success(service.copyRevision(revisionId, expectedVersion));
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
                                                            @RequestHeader("If-Match") Integer expectedVersion) {
        return success(service.publish(revisionId, expectedVersion));
    }

    @PostMapping("/revisions/{revisionId}/actions/disable")
    @Operation(summary = "停用已发布配置修订")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:disable')")
    public CommonResult<CutoverConfigurationRespVO> disable(@PathVariable Long revisionId,
                                                            @RequestHeader("If-Match") Integer expectedVersion) {
        return success(service.disable(revisionId, expectedVersion));
    }
}
