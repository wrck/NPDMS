package com.dp.deviceops.core.port;

import java.util.List;

/** Read-only integration boundary; it intentionally has no credential or mutation methods. */
public interface MasterDataQueryPort {
    List<ProjectProjection> findProjects(String query);
    List<DeviceProjection> findDevices(String projectKey, String query);
    record ProjectProjection(String namespace, String projectKey, String projectName, String projectCode) { }
    record DeviceProjection(String deviceKey, String deviceName, String vendor, String model) { }
}
