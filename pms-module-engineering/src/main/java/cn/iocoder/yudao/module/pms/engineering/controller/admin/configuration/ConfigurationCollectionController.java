package cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionExecutionRequest;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionApplicationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController
@RequestMapping("/api/v1/pms/implementation/configurations/{configurationId}/collections")
@RequiredArgsConstructor
public class ConfigurationCollectionController {
    private final CollectionApplicationApi service;
    @PostMapping @PreAuthorize("@ss.hasPermission('pms:imp-configuration:update')")
    @ApiAccessLog(requestEnable = false)
    public CommonResult<CollectionApplicationApi.Execution> submit(@PathVariable Long configurationId,
            @RequestBody CollectionExecutionRequest request) {
        return success(service.submit("configuration", configurationId, actor(), request));
    }
    @GetMapping @PreAuthorize("@ss.hasPermission('pms:imp-configuration:query')")
    public CommonResult<PageResult<CollectionApplicationApi.Execution>> page(@PathVariable Long configurationId,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "10") int pageSize) {
        return success(service.page("configuration", configurationId, actor(), pageNo, pageSize));
    }
    @GetMapping("/by-request-key") @PreAuthorize("@ss.hasPermission('pms:imp-configuration:query')")
    public CommonResult<CollectionApplicationApi.Execution> findByRequestKey(@PathVariable Long configurationId,
            @RequestParam String requestKey) {
        return success(service.findByRequestKey("configuration", configurationId, actor(), requestKey));
    }
    @PostMapping("/{id}/consume") @PreAuthorize("@ss.hasPermission('pms:imp-configuration:update')")
    public CommonResult<CollectionApplicationApi.Execution> consume(@PathVariable Long configurationId, @PathVariable Long id) {
        return success(service.consume("configuration", configurationId, actor(), id));
    }
    @PostMapping("/{id}/cancel") @PreAuthorize("@ss.hasPermission('pms:imp-configuration:update')")
    public CommonResult<Boolean> cancel(@PathVariable Long configurationId, @PathVariable Long id) {
        service.cancel("configuration", configurationId, actor(), id); return success(true);
    }
    @PostMapping("/{id}/download") @PreAuthorize("@ss.hasPermission('pms:imp-configuration:query')")
    public CommonResult<String> download(@PathVariable Long configurationId, @PathVariable Long id) {
        return success(service.download("configuration", configurationId, actor(), id));
    }
    private static Long actor() { return SecurityFrameworkUtils.getLoginUserId(); }
}
