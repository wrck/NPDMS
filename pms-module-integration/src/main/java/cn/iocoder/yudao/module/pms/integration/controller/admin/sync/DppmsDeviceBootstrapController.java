package cn.iocoder.yudao.module.pms.integration.controller.admin.sync;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/integration") @RequiredArgsConstructor
public class DppmsDeviceBootstrapController {
    private final SyncConnectionService connections;
    private final DppmsDeviceAssignmentService assignments;
    // The public template route remains on DataSyncController; do not register it twice.
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<SyncDefinition> template(@RequestParam Long connectionId) {
        connections.required(connectionId);return success(DppmsDeviceBootstrapSyncTemplate.create(connectionId));
    }
    @PostMapping("/device-assignments/preview")
    @PreAuthorize("@ss.hasPermission('pms:integration:execute')")
    public CommonResult<DppmsDeviceAssignmentService.Result> preview(@RequestBody DppmsDeviceAssignmentService.Request request) {
        return success(assignments.preview(request));
    }
    @PostMapping("/device-assignments/apply")
    @PreAuthorize("@ss.hasPermission('pms:integration:execute')")
    public CommonResult<DppmsDeviceAssignmentService.Result> apply(@RequestBody DppmsDeviceAssignmentService.Request request) {
        return success(assignments.apply(request));
    }
}
