package cn.iocoder.yudao.module.pms.integration.extsystem.controller;

import cn.iocoder.yudao.module.pms.integration.extsystem.annotation.OperLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.result.Result;
import cn.iocoder.yudao.module.pms.integration.extsystem.d365.entity.D365Invoice;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.FpHealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.PaymentCallbackDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.fp.FpResponse;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.fp.SettlementPushRequest;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.FpIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * FP (Financial Platform) integration controller: health check, settlement
 * push, invoice OCR and payment callback.
 */
@Tag(name = "FP集成", description = "FP (Financial Platform) integration APIs")
@RestController
@RequestMapping("/api/integration/fp")
@RequiredArgsConstructor
public class FpIntegrationController {

    private final FpIntegrationService fpIntegrationService;

    @Operation(summary = "FP health check")
    @GetMapping("/health")
    public Result<FpHealthDto> health() {
        return Result.ok(fpIntegrationService.healthCheck());
    }

    @Operation(summary = "Manually push a settlement to FP")
    @PostMapping("/push-settlement")
    @PreAuthorize("@ss.hasPermission('integration:fp:push')")
    @OperLog(title = "FP集成", businessType = 2)
    public Result<FpResponse<String>> pushSettlement(@Valid @RequestBody SettlementPushRequest request) {
        return Result.ok(fpIntegrationService.pushSettlement(request));
    }

    @Operation(summary = "Push an invoice image to FP OCR")
    @PostMapping(value = "/ocr-invoice", consumes = "multipart/form-data")
    @PreAuthorize("@ss.hasPermission('integration:fp:ocr')")
    @OperLog(title = "FP集成", businessType = 2)
    public Result<D365Invoice> ocrInvoice(@RequestParam("file") MultipartFile file) {
        return Result.ok(fpIntegrationService.ocrInvoice(file));
    }

    @Operation(summary = "Receive an FP payment callback")
    @PostMapping("/payment-callback")
    @OperLog(title = "FP集成-支付回调", businessType = 2, isSaveResponseData = false)
    public Result<Void> paymentCallback(@Valid @RequestBody PaymentCallbackDto callback) {
        fpIntegrationService.handlePaymentCallback(callback);
        return Result.ok();
    }
}
