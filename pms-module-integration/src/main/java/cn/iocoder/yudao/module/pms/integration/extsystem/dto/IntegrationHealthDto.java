package cn.iocoder.yudao.module.pms.integration.extsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Aggregated health snapshot for all integration adapters (D365, FP, OA).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationHealthDto {

    /** D365 adapter health. */
    private D365HealthDto d365Health;

    /** FP adapter health. */
    private FpHealthDto fpHealth;

    /** OA adapter health. */
    private OaHealthDto oaHealth;

    /**
     * Overall status: HEALTHY (all connected), DEGRADED (some connected),
     * DOWN (none connected).
     */
    private String overallStatus;

    /** Timestamp of this health check. */
    private LocalDateTime lastCheckTime;
}
