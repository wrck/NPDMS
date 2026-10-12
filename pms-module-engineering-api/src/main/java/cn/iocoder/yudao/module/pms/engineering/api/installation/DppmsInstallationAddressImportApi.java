package cn.iocoder.yudao.module.pms.engineering.api.installation;

import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import java.util.List;
import java.time.LocalDateTime;

/** Import complete immutable source address facts. Current installation completion is never inferred. */
public interface DppmsInstallationAddressImportApi {
    List<NativeEntityMigrationRow> importAddresses(Command command);
    record Command(Long tenantId, Long actorId, Long projectId, List<Address> addresses,
                   String sourceVersion, LocalDateTime sourceReadAt) {
        public Command(Long tenantId,Long actorId,Long projectId,List<Address> addresses) {
            this(tenantId,actorId,projectId,addresses,null,null);
        }
    }
    record Address(String sourceKey, Long deviceId, String address, Details details,
                   String sourceProjectKey, String sourceDeviceSn, Long sourceId, Long sourceProjectId) {
        public Address(String key,Long deviceId,String address,Details details,String projectKey,String sn) {
            this(key,deviceId,address,details,projectKey,sn,Long.valueOf(key),projectKey==null?null:Long.valueOf(projectKey));
        }
        public Address(String key,Long deviceId,String address,Details details) { this(key,deviceId,address,details,null,null,Long.valueOf(key),null); }
        public Address(String key,Long deviceId,String address) { this(key,deviceId,address,null,null,null,Long.valueOf(key),null); }
    }
    record Details(String sourceProductCode, String sourceProductModel, String sourceProductName, String sourceReceiverName, String sourceExpressNo, String sourceCarrierName, LocalDateTime sourceShipmentTime, String sourceContractNo, String changedProjectKey, String changedContractNo, String transferProjectKey, String transferContractNo, Integer sourceTransferFlag, LocalDateTime sourceCreatedAt, String sourceCreatedBy, LocalDateTime sourceUpdatedAt, String sourceUpdatedBy) {}
}
