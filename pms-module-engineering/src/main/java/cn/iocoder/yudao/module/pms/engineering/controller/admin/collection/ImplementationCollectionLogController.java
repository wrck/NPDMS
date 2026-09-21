package cn.iocoder.yudao.module.pms.engineering.controller.admin.collection;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.engineering.service.collection.ImplementationCollectionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pms/implementation/{entry}/{objectId}/collection-logs")
@RequiredArgsConstructor
public class ImplementationCollectionLogController {
    private final ImplementationCollectionLogService service;

    @GetMapping
    public CommonResult<PageResult<ImplementationCollectionLogService.View>> page(@PathVariable String entry, @PathVariable Long objectId,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "10") int pageSize) {
        return CommonResult.success(service.page(entry, objectId, SecurityFrameworkUtils.getLoginUserId(), pageNo, pageSize));
    }
}
