package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/integration/device-ops/desktop")
@ConditionalOnProperty(prefix="pms.integration.device-ops",name="enabled",havingValue="true")
public class DacDesktopController {
    private final DacDesktopDispatchService desktop;
    private final DacCallbackService callbacks;

    @PostMapping("/tickets/resolve")
    @PermitAll // The short-lived signed ticket is verified by the service, with the current tenant.
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public DacDesktopDispatchService.Grant resolve(@RequestHeader("X-NPDMS-DAC-Ticket") String ticket) {
        return desktop.resolve(ticket);
    }

    @PostMapping("/tasks/{taskId}/claim")
    @PermitAll
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public Confirmation claim(@PathVariable("taskId") String taskId, @RequestHeader("X-NPDMS-DAC-Task-Token") String token,
            @RequestBody Claim input) {
        desktop.claim(new DacDesktopDispatchService.Claim(new DacDesktopDispatchService.Authorization(taskId, token), input.executionId()));
        return new Confirmation(true);
    }

    @PostMapping("/tasks/{taskId}/observations")
    @PermitAll
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public DacDesktopDispatchService.Control observe(@PathVariable("taskId") String taskId,
            @RequestHeader("X-NPDMS-DAC-Task-Token") String token, @RequestBody Observation input) {
        return desktop.observe(new DacDesktopDispatchService.Observation(new DacDesktopDispatchService.Authorization(taskId, token),
                input.executionId(), input.collectionId(), input.status(), input.redeliveryApplied()));
    }

    @PostMapping("/tasks/{taskId}/control")
    @PermitAll
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public DacDesktopDispatchService.Control control(@PathVariable("taskId") String taskId,
            @RequestHeader("X-NPDMS-DAC-Task-Token") String token, @RequestBody Claim input) {
        return desktop.control(new DacDesktopDispatchService.Claim(new DacDesktopDispatchService.Authorization(taskId, token), input.executionId()));
    }

    @PostMapping("/tasks/{taskId}/cancelled-before-submission")
    @PermitAll
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public Confirmation cancelledBeforeSubmission(@PathVariable("taskId") String taskId,
            @RequestHeader("X-NPDMS-DAC-Task-Token") String token, @RequestBody Claim input) {
        desktop.cancelledBeforeSubmission(new DacDesktopDispatchService.Claim(
                new DacDesktopDispatchService.Authorization(taskId, token), input.executionId()));
        return new Confirmation(true);
    }

    @PostMapping(value="/callbacks",consumes="multipart/form-data")
    @PermitAll
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public DacCallbackService.Ack callback(@RequestPart("metadata") String raw, @RequestPart("log") MultipartFile file,
            @RequestHeader("X-NPDMS-DAC-Task-Token") String token, @RequestHeader("X-DAC-Timestamp") String timestamp,
            @RequestHeader("X-DAC-Nonce") String nonce, @RequestHeader("X-DAC-Signature") String signature) throws Exception {
        if (raw.length() > 8192 || token.length() > 128) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var metadata = JsonUtils.parseObjectQuietly(raw, DacCallbackMetadata.class);
        if (metadata == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        desktop.authenticateCallback(new DacDesktopDispatchService.CallbackAuthorization(
                new DacDesktopDispatchService.Authorization(metadata.platformTaskId(), token), metadata.externalTaskId()));
        // Reuse byte count, digest, replay, state-machine and receipt checks. The signing key is scoped to this task.
        return new DacCallbackController(callbacks, token).receive(raw, file, timestamp, nonce, signature);
    }

    public record Claim(String executionId) { }
    public record Confirmation(boolean accepted) { }
    public record Observation(String executionId, String collectionId, String status, boolean redeliveryApplied) { }
}
