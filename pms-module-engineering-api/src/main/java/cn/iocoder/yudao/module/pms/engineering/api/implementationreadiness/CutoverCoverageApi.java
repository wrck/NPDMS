package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness;

import java.util.List;

/** IMP 拥有当前实施范围的成功覆盖判断，PROJ 消费事实并决定实际阶段推进。 */
public interface CutoverCoverageApi {
    Coverage inspect(Query query);
    Coverage lockAndInspect(Query query);
    record Query(Long tenantId, Long projectId) { }
    record Device(Long deviceId, String serialNumber, Long assignmentVersion) { }
    record ResultReference(Long taskId, Long closureId, Long closureVersion) { }
    record Coverage(String factVersion, List<Device> requiredDevices, List<Long> coveredDeviceIds,
                    List<Long> missingDeviceIds, List<ResultReference> successfulResults, boolean complete) {
        public Coverage {
            requiredDevices = List.copyOf(requiredDevices); coveredDeviceIds = List.copyOf(coveredDeviceIds);
            missingDeviceIds = List.copyOf(missingDeviceIds); successfulResults = List.copyOf(successfulResults);
        }
    }
}
