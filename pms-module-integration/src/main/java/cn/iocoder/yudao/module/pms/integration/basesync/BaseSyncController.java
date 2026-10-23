package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncCounts;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/integration/base-sync")
@RequiredArgsConstructor
public class BaseSyncController {
    private final BaseSyncService service;
    @GetMapping("/tasks")
    @PreAuthorize("@ss.hasPermission('pms:integration:view')")
    public CommonResult<List<BaseSyncService.Task>> tasks() { return success(service.tasks()); }
    @GetMapping("/registrations")
    @PreAuthorize("@ss.hasPermission('pms:integration:view')")
    public CommonResult<List<BaseSyncMetadataMapper.TaskRow>> registrations() { return success(service.registrations()); }
    @PostMapping("/registrations")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<Long> register(@RequestBody BaseSyncConfiguration request) { return success(service.register(request)); }
    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermission('pms:integration:execute')")
    public CommonResult<SyncCounts> preview(@RequestBody BaseSyncService.Run request) { return success(service.preview(request)); }
    @PostMapping("/previews/recover")
    @PreAuthorize("@ss.hasPermission('pms:integration:execute')")
    public CommonResult<Boolean> recoverPreview(@RequestBody BaseSyncService.PreviewRecovery request) { service.recoverPreview(request);return success(true); }
    @PostMapping("/execute")
    @PreAuthorize("@ss.hasPermission('pms:integration:execute')")
    public CommonResult<SyncCounts> execute(@RequestBody BaseSyncService.Run request) { return success(service.execute(request)); }
}
