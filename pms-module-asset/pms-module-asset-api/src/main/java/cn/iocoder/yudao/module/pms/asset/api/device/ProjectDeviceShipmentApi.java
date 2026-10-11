package cn.iocoder.yudao.module.pms.asset.api.device;

import java.util.Set;

/** Asset-owned shipment facts for server-resolved, visible project candidates. */
public interface ProjectDeviceShipmentApi {
    /** Returns candidates with a current, non-deleted serial-number device and recorded shipment time.
     * No candidates returns empty; an invalid tenant/session is rejected, never interpreted as no shipments.
     */
    Set<Long> resolveShippedProjectIds(Query query);

    record Query(Long tenantId, Set<Long> visibleProjectIds) {}
}
