package cn.iocoder.yudao.module.pms.integration.extsystem.controller;

import cn.iocoder.yudao.module.pms.integration.extsystem.annotation.OperLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.result.Result;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.OaHealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.oa.OaTodoRequest;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.OaIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA (致远 OA) integration controller: health check and manual todo
 * operations (push / complete / transfer).
 */
@Tag(name = "OA集成", description = "Seeyon OA integration APIs")
@RestController
@RequestMapping("/api/integration/oa")
@RequiredArgsConstructor
public class OaIntegrationController {

    private final OaIntegrationService oaIntegrationService;

    @Operation(summary = "OA health check")
    @GetMapping("/health")
    public Result<OaHealthDto> health() {
        return Result.ok(oaIntegrationService.healthCheck());
    }

    @Operation(summary = "Manually push a todo to OA")
    @PostMapping("/todo/push")
    @PreAuthorize("@ss.hasPermission('integration:oa:push')")
    @OperLog(title = "OA集成", businessType = 2)
    public Result<Boolean> pushTodo(@Valid @RequestBody OaTodoRequest request) {
        return Result.ok(oaIntegrationService.pushTodo(request));
    }

    @Operation(summary = "Manually complete an OA todo")
    @PutMapping("/todo/complete")
    @PreAuthorize("@ss.hasPermission('integration:oa:process')")
    @OperLog(title = "OA集成", businessType = 2)
    public Result<Boolean> completeTodo(@RequestParam String businessKey) {
        return Result.ok(oaIntegrationService.completeTodo(businessKey));
    }

    @Operation(summary = "Manually transfer an OA todo to a new handler")
    @PutMapping("/todo/transfer")
    @PreAuthorize("@ss.hasPermission('integration:oa:process')")
    @OperLog(title = "OA集成", businessType = 2)
    public Result<Boolean> transferTask(@RequestParam String businessKey,
                                        @RequestParam String newHandlerUserId) {
        return Result.ok(oaIntegrationService.transferTask(businessKey, newHandlerUserId));
    }
}
