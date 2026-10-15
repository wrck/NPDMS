package cn.iocoder.yudao.module.pms.asset.api.device;

import java.util.Set;

/** Asset-owned shipment facts for server-resolved, visible project candidates. */
public interface ProjectDeviceShipmentApi {
    /** Returns candidates with a current, non-deleted serial-number device and recorded shipment time.
     * No candidates returns empty; an invalid tenant/session is rejected, never interpreted as no shipments.
     */
    Set<Long> resolveShippedProjectIds(Query query);

    /** 仅当前直接归属项目的已发货设备，不扩大到关联合同或历史归属。 */
    DevicePage pageDevices(DeviceQuery query);

    record Query(Long tenantId, Set<Long> visibleProjectIds) {}

    @lombok.Builder
    record DeviceQuery(Long tenantId, Long actorId, Long projectId, String keyword, int pageNo, int pageSize) {}
    record Device(Long deviceId, String serialNumber, String productCode, String productModel) {}
    record DevicePage(java.util.List<Device> list, long total) {
        public DevicePage { list = java.util.List.copyOf(list); }
    }
}
