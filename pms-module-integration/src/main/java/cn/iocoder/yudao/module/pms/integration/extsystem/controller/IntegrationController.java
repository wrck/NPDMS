package cn.iocoder.yudao.module.pms.integration.extsystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.yudao.module.pms.integration.extsystem.annotation.OperLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.result.Result;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.IIntegrationLogService;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.RetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration management controller: query logs and manual retry.
 *
 * <p>D365/FP/OA push and health endpoints live in their own dedicated
 * controllers (D365IntegrationController, FpIntegrationController,
 * OaIntegrationController).</p>
 */
@Tag(name = "集成管理", description = "External system integration management APIs")
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final IIntegrationLogService integrationLogService;
    private final RetryService retryService;

    @Operation(summary = "Paginated integration log query")
    @GetMapping("/log/list")
    public Result<Page<IntegrationLog>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             IntegrationLog filters) {
        return Result.ok(integrationLogService.list(page, size, filters));
    }

    @Operation(summary = "Get integration log detail by id")
    @GetMapping("/log/{id}")
    public Result<IntegrationLog> get(@PathVariable Long id) {
        return Result.ok(integrationLogService.getById(id));
    }

    @Operation(summary = "Manually retry a failed integration by log id")
    @PostMapping("/log/{id}/retry")
    @PreAuthorize("@ss.hasPermission('integration:log:retry')")
    @OperLog(title = "集成管理", businessType = 2)
    public Result<IntegrationLog> retry(@PathVariable Long id) {
        return Result.ok(retryService.retryLog(id));
    }
}
