package cn.iocoder.yudao.module.pms.integration.extsystem.controller;

import cn.iocoder.yudao.module.pms.integration.extsystem.annotation.OperLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.result.Result;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.D365HealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.d365.PurchaseReceiptHeader;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.D365IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * D365 integration management controller: health check, sync triggers and
 * manual push.
 */
@Tag(name = "D365集成", description = "D365 (Microsoft Dynamics 365) integration APIs")
@RestController
@RequestMapping("/api/integration/d365")
@RequiredArgsConstructor
public class D365IntegrationController {

    private final D365IntegrationService d365IntegrationService;

    @Operation(summary = "D365 health check")
    @GetMapping("/health")
    public Result<D365HealthDto> health() {
        return Result.ok(d365IntegrationService.healthCheck());
    }

    @Operation(summary = "Manually push a purchase receipt to D365")
    @PostMapping("/push-receipt")
    @PreAuthorize("@ss.hasPermission('integration:d365:push')")
    @OperLog(title = "D365集成", businessType = 2)
    public Result<String> pushReceipt(@Valid @RequestBody PurchaseReceiptHeader header) {
        return Result.ok(d365IntegrationService.pushPurchaseReceipt(header));
    }

    @Operation(summary = "Trigger D365 purchase order sync")
    @PostMapping("/sync/purchase-orders")
    @PreAuthorize("@ss.hasPermission('integration:d365:sync')")
    @OperLog(title = "D365集成", businessType = 2)
    public Result<Integer> syncPurchaseOrders() {
        return Result.ok(d365IntegrationService.syncPurchaseOrders());
    }

    @Operation(summary = "Trigger D365 purchase receipt sync")
    @PostMapping("/sync/purchase-receipts")
    @PreAuthorize("@ss.hasPermission('integration:d365:sync')")
    @OperLog(title = "D365集成", businessType = 2)
    public Result<Integer> syncPurchaseReceipts() {
        return Result.ok(d365IntegrationService.syncPurchaseReceipts());
    }

    @Operation(summary = "Trigger D365 asset serial number sync")
    @PostMapping("/sync/asset-serial-numbers")
    @PreAuthorize("@ss.hasPermission('integration:d365:sync')")
    @OperLog(title = "D365集成", businessType = 2)
    public Result<Integer> syncAssetSerialNumbers() {
        return Result.ok(d365IntegrationService.syncAssetSerialNumbers());
    }

    @Operation(summary = "Trigger D365 invoice sync")
    @PostMapping("/sync/invoices")
    @PreAuthorize("@ss.hasPermission('integration:d365:sync')")
    @OperLog(title = "D365集成", businessType = 2)
    public Result<Integer> syncInvoices() {
        return Result.ok(d365IntegrationService.syncInvoices());
    }
}
