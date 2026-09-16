package cn.iocoder.yudao.module.pms.integration.extsystem.dto;

import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Health snapshot for the D365 integration adapter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class D365HealthDto {

    /** Whether the D365 endpoint is reachable. */
    private boolean connected;

    /** Whether a valid OAuth2 token could be obtained. */
    private boolean tokenValid;

    /** Count of recent successful pushes (last 24h). */
    private int recentPushCount;

    /** Count of recent failed pushes (last 24h). */
    private int recentFailCount;

    /** Last 10 integration logs for D365. */
    private List<IntegrationLog> recentLogs;
}
