package cn.iocoder.yudao.module.pms.integration.controller.admin.sync;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/integration/templates")
public class DirectMemberHistoryTemplateController {
    private final SyncConnectionService connections;
    @GetMapping("/dppms-member-history-direct")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<SyncDefinition> template(@RequestParam Long connectionId) {
        connections.required(connectionId);return CommonResult.success(DppmsDirectMemberHistoryTemplate.create(connectionId));
    }
}
