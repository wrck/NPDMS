package cn.iocoder.yudao.module.pms.asset.api.migration;

import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Project's verified, immutable DPPMS snapshot enters the AST Owner, never AST tables from PROJ. */
public interface DppmsAssetEntityImportApi {
    List<NativeEntityMigrationRow> importEntities(Command command);

    record Command(Long tenantId, Long actorId, Long projectId, String contractNo, Long snapshotId,
                   String sourceVersion, LocalDateTime extractedAt, List<Shipment> shipments,
                   List<Software> software, List<Warranty> warranties, List<FactoryVersion> factoryVersions) {
        public Command(Long tenantId, Long actorId, Long projectId, String contractNo, Long snapshotId,
                String sourceVersion, LocalDateTime extractedAt, List<Shipment> shipments,
                List<Software> software, List<Warranty> warranties) {
            this(tenantId,actorId,projectId,contractNo,snapshotId,sourceVersion,extractedAt,shipments,software,warranties,List.of());
        }
    }

    record Shipment(String sourceKey, String deviceSn, String packageKey, String productCode,
                    String productModel, String productName, ShipmentDetails details, ShipmentProductDetails productDetails) {
        public Shipment(String key,String sn,String pack,String code,String model,String name,ShipmentDetails details) {
            this(key,sn,pack,code,model,name,details,null);
        }
        public Shipment(String key,String sn,String pack,String code,String model,String name) {
            this(key,sn,pack,code,model,name,null,null);
        }
    }

    record Software(String sourceKey, String deviceSn, String logKey, LocalDateTime eventTime,
                    String conp, String conpType, String conpSeries, String conpMark,
                    String boot, String cpld, String pcb, boolean current, String customInfo, Integer sourceDataState) {
        public Software(String key,String sn,String log,LocalDateTime at,String conp,String type,String series,
                String mark,String boot,String cpld,String pcb,boolean current) {
            this(key,sn,log,at,conp,type,series,mark,boot,cpld,pcb,current,null,null);
        }
    }

    record Warranty(String sourceKey, String deviceSn, LocalDate startDate, LocalDate endDate,
                    Integer months, String grade, String contractNo, WarrantyDetails details) {
        public Warranty(String key,String sn,LocalDate start,LocalDate end,Integer months,String grade,String contract) {
            this(key,sn,start,end,months,grade,contract,null);
        }
    }
    record ShipmentDetails(String legacyPackageKey,
            String rmaRelatedSn,
            Integer rmaMarked,
            String orderNo,
            String lineNo,
            String productCode,
            String secondarySn,
            String secondaryItem,
            String internalSerialNo,
            Integer isRma,
            String profitCenter,
            String soleAgentSuffix,
            LocalDateTime syncTime,
            String uuid,
            LocalDate warrantyStartDate,
            Integer warrantyMonths,
            String rmaNo,
            String relatedDeviceSn) {}
    record ShipmentProductDetails(String productModel,String productName,String secondaryProductModel,
            String secondaryProductName,LocalDateTime sourceUpdatedTime) {}
    record WarrantyDetails(String combinedDeviceSn,
            String productCode,
            String productName,
            String productDescription,
            String gradeName,
            LocalDateTime shipmentTime,
            String warrantyFlag,
            LocalDateTime sourceWarrantyStartTime,
            LocalDateTime sourceWarrantyEndTime,
            LocalDateTime originalWarrantyStartTime,
            LocalDateTime originalWarrantyEndTime,
            Integer remainingDaysAtSource,
            Integer originalRemainingDaysAtSource,
            String contractType,
            String contractTypeName,
            String projectName,
            String customerName,
            String finalCustomerName,
            String agentName,
            String officeCode,
            String officeName,
            String marketCode,
            String marketName,
            String systemCode,
            String systemName,
            String secondarySn,
            String secondaryItem,
            String secondaryProductName,
            String secondaryProductDescription,
            LocalDateTime sourceSyncTime,
            String sourceShipmentKey) {}
    record FactoryVersion(String sourceKey,
            String deviceSn,
            String conpVersion,
            String bootVersion,
            String cpldVersion,
            String pcbVersion) {}
}
