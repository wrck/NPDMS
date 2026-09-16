package cn.iocoder.yudao.module.pms.integration.extsystem.health;

import cn.iocoder.yudao.module.pms.integration.extsystem.result.Result;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.D365HealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.FpHealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.IntegrationHealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.OaHealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.D365IntegrationService;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.FpIntegrationService;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.OaIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Aggregated integration health dashboard. Calls each subsystem's
 * {@code healthCheck} and rolls the results up into a single
 * {@link IntegrationHealthDto} with an overall status.
 */
@Slf4j
@Tag(name = "集成健康", description = "Aggregated integration health dashboard")
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationHealthController {

    private final D365IntegrationService d365IntegrationService;
    private final FpIntegrationService fpIntegrationService;
    private final OaIntegrationService oaIntegrationService;

    @Operation(summary = "Aggregated integration health (D365 + FP + OA)")
    @GetMapping("/health")
    public Result<IntegrationHealthDto> health() {
        D365HealthDto d365 = safeD365();
        FpHealthDto fp = safeFp();
        OaHealthDto oa = safeOa();
        int connected = 0;
        if (d365 != null && d365.isConnected()) {
            connected++;
        }
        if (fp != null && fp.isConnected()) {
            connected++;
        }
        if (oa != null && oa.isConnected()) {
            connected++;
        }
        String overall;
        if (connected == 3) {
            overall = "HEALTHY";
        } else if (connected == 0) {
            overall = "DOWN";
        } else {
            overall = "DEGRADED";
        }
        IntegrationHealthDto dto = IntegrationHealthDto.builder()
                .d365Health(d365)
                .fpHealth(fp)
                .oaHealth(oa)
                .overallStatus(overall)
                .lastCheckTime(LocalDateTime.now())
                .build();
        return Result.ok(dto);
    }

    private D365HealthDto safeD365() {
        try {
            return d365IntegrationService.healthCheck();
        } catch (Exception e) {
            log.debug("D365 health check threw: {}", e.getMessage());
            return D365HealthDto.builder().connected(false).tokenValid(false)
                    .recentPushCount(0).recentFailCount(0).build();
        }
    }

    private FpHealthDto safeFp() {
        try {
            return fpIntegrationService.healthCheck();
        } catch (Exception e) {
            log.debug("FP health check threw: {}", e.getMessage());
            return FpHealthDto.builder().connected(false).tokenValid(false)
                    .recentPushCount(0).recentFailCount(0).build();
        }
    }

    private OaHealthDto safeOa() {
        try {
            return oaIntegrationService.healthCheck();
        } catch (Exception e) {
            log.debug("OA health check threw: {}", e.getMessage());
            return OaHealthDto.builder().connected(false).tokenValid(false)
                    .recentPushCount(0).recentFailCount(0).build();
        }
    }
}
