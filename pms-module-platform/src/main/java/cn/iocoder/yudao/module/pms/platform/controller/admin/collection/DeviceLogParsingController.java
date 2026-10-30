package cn.iocoder.yudao.module.pms.platform.controller.admin.collection;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.platform.service.collection.DeviceLogParsingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/device-collection/log-parses")
@RequiredArgsConstructor
public class DeviceLogParsingController {
    private final DeviceLogParsingService service;

    @PostMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<DeviceLogParsingService.View> submit(@RequestBody DeviceLogParsingService.Input request) {
        return success(service.submit(actor(), request));
    }

    @GetMapping
    public CommonResult<PageResult<DeviceLogParsingService.View>> page(@RequestParam Long projectId,
            @RequestParam(required = false) String entry, @RequestParam(required = false) Long objectId,
            @RequestParam(required = false) Long executionId,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "10") int pageSize) {
        return success(service.page(actor(), projectId, entry, objectId, executionId, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    @ApiAccessLog(responseEnable = false)
    public CommonResult<DeviceLogParsingService.View> get(@PathVariable Long id) { return success(service.get(actor(), id)); }

    private Long actor() {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null) throw new AccessDeniedException("请先登录");
        return actor;
    }
}
