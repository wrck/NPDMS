package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Device base initialization from formal shipment facts; no project/customer assignment or legacy task call. */
@Component @RequiredArgsConstructor
public class P30DeviceBaseSyncTask extends AbstractSyncTask<P30DeviceBaseSyncTask.DeviceSource,P30DeviceBaseSyncTask.Parameters> {
    private final TargetMapper devices;
    public static final class Parameters {Map<String,List<Product>> products;}
    @Override protected Parameters createParameters(SyncContext<?> runtime){return new Parameters();}
    @Override public String taskCode(){return "BASE_P30";}
    @Override public String taskName(){return "正式发货设备基础承接";}
    @Override public void work(SyncContext<Parameters> context){
        syncData(context.forLocalData("DeviceBase",false,true));
        if(context.runState().counts().read()==0)context.runState().warning("P30_FORMAL_DPPMS_SHIPMENTS_MISSING:0");
    }
    @Override protected void syncDataInsertBefore(SyncBatch<DeviceSource,Parameters> batch){
        var context=batch.context();var serials=batch.rows().stream().map(DeviceSource::getDeviceSn).toList();
        var query=new TargetMapper.SerialsQuery(context.tenantId(),serials,context.preview());
        var shipments=devices.selectLatestShipments(query);var latest=new HashMap<String,Shipment>();
        for(var shipment:shipments){
            if(!serials.contains(shipment.getDeviceSn()) || shipment.getTenantId()!=context.tenantId() || !"DPPMS".equals(shipment.getSourceSystem())
                    || latest.putIfAbsent(shipment.getDeviceSn(),shipment)!=null)throw new IllegalStateException("P30正式最新发货来源、租户或SN身份冲突");
        }
        if(!latest.keySet().equals(new HashSet<>(serials)))throw new IllegalStateException("P30正式发货事实缺失");
        if(context.parameters().products==null){
            context.parameters().products=new HashMap<>();
            for(var product:devices.selectProducts(new TargetMapper.ProductCatalogueQuery(context.tenantId())))
                context.parameters().products.computeIfAbsent(product.productCode(),ignored->new ArrayList<>()).add(product);
        }
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("DEVICE_BASE",serials));
        var existing=new HashMap<String,DeviceRow>();for(var old:devices.selectExisting(query))
            if(old.getTenantId()!=context.tenantId() || !serials.contains(old.getSn()) || existing.putIfAbsent(old.getSn(),old)!=null)
                throw new IllegalStateException("P30正式设备SN身份多义、跨租户或原文不同");
        for(var source:batch.rows()){
            if(source.getDeviceSn()==null || source.getDeviceSn().isBlank())throw new IllegalArgumentException("P30正式发货SN缺失");
            checkTextLength(source.getDeviceSn(),100);
            var shipment=latest.get(source.getDeviceSn());
            var matches=context.parameters().products.getOrDefault(shipment.getProductCode(),List.of());
            if(matches.size()>1)throw new IllegalStateException("P30产品正式编码身份多义");
            var product=matches.isEmpty()?null:matches.getFirst();
            var old=existing.get(source.getDeviceSn());var binding=bindings.get(source.getDeviceSn());
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            if(old!=null){
                if(Boolean.TRUE.equals(old.getDeleted()) || !"DPPMS".equals(old.getSourceSystem())
                        || binding!=null && (binding.targetId()!=old.getId() || !Objects.equals(binding.fingerprint(),fingerprint(old))))
                    throw new IllegalStateException("P30设备正式来源或独立保护基线已变化");
                continue;
            }
            if(binding!=null)throw new IllegalStateException("P30独立设备绑定目标缺失");
            var row=new DeviceRow();row.setId(context.preview()?null:IdWorker.getId());row.setTenantId(context.tenantId());row.setSn(source.getDeviceSn());
            row.setProductCode(shipment.getProductCode());row.setProductName(product==null?null:product.productName());row.setName(row.getProductName());row.setProductModel(product==null?null:product.productModel());
            row.setShipmentRecordId(shipment.getId());row.setShipmentTime(shipment.getShipmentTime());row.setPackageNo(shipment.getPackageNo());row.setContractNo(shipment.getContractNo());
            row.setProjectAssignmentVersion(0L);row.setCustomerAssignmentVersion(0L);row.setStatus("IN_STOCK");row.setVersion(0L);
            row.setSourceSystem("DPPMS");row.setSourceKey("SN:"+row.getSn());row.setSourceVersion("BASE_P30:"+context.startedAt());row.setSyncedAt(context.startedAt());row.setSyncStatus("SUCCESS");row.setRemark("DPPMS设备基础承接");
            row.setCreator(Long.toString(context.actorId()));row.setUpdater(row.getCreator());row.setCreateTime(context.startedAt());row.setUpdateTime(context.startedAt());row.setDeleted(false);
            checkTextLength(row.getProductCode(),64);checkTextLength(row.getProductName(),128);checkTextLength(row.getProductModel(),128);checkTextLength(row.getPackageNo(),128);checkTextLength(row.getContractNo(),64);checkMillisecond(row.getShipmentTime());
            source.setTarget(row);source.setSyncAction(SyncWriteAction.INSERT);
        }
    }
    @Override protected SyncCounts syncDataCount(SyncBatch<DeviceSource,Parameters> batch){return SyncCounts.fromActions(batch.rows(),DeviceSource::getSyncAction);}
    @Override protected void syncDataInsert(SyncBatch<DeviceSource,Parameters> batch){super.syncDataInsert(batch.matching(row->row.getSyncAction()==SyncWriteAction.INSERT));}
    @Override protected void syncDataInsertAfter(SyncBatch<DeviceSource,Parameters> batch){
        batch.context().identities().saveAll(batch.rows().stream().filter(row->row.getSyncAction()==SyncWriteAction.INSERT)
                .map(row->new SyncIdentityStore.Save("DEVICE_BASE",row.getDeviceSn(),row.getTarget().getId(),fingerprint(row.getTarget()),null)).toList());
    }
    static String fingerprint(DeviceRow row){return SyncFingerprint.of(Arrays.asList(row.getId(),row.getTenantId(),row.getVersion(),row.getShipmentRecordId(),row.getProjectAssignmentVersion(),row.getCustomerAssignmentVersion(),row.getSn(),row.getName(),row.getProductName(),row.getProductModel(),row.getProductCode(),row.getPackageNo(),row.getContractNo(),row.getStatus(),row.getSourceSystem(),row.getSourceKey(),row.getSourceVersion(),row.getSyncStatus(),row.getRemark(),row.getCreator(),row.getUpdater(),row.getShipmentTime(),row.getSyncedAt(),row.getCreateTime(),row.getUpdateTime(),row.getDeleted()));}
    @lombok.Data public static class DeviceSource {private String deviceSn;private DeviceRow target;private SyncWriteAction syncAction;}
    @lombok.Data public static class Shipment {private Long id;private Long tenantId;private String deviceSn;private String sourceSystem;private String sourceKey;private String contractNo;private String packageNo;private LocalDateTime shipmentTime;private String productCode;}
    public record Product(String productCode,String productModel,String productName){}
    @lombok.Data public static class DeviceRow {
        private Long id;
        private Long tenantId;
        private Long version;
        private Long shipmentRecordId;
        private Long projectAssignmentVersion;
        private Long customerAssignmentVersion;
        private String sn;
        private String name;
        private String productName;
        private String productModel;
        private String productCode;
        private String packageNo;
        private String contractNo;
        private String status;
        private String sourceSystem;
        private String sourceKey;
        private String sourceVersion;
        private String syncStatus;
        private String remark;
        private String creator;
        private String updater;
        private LocalDateTime shipmentTime;
        private LocalDateTime syncedAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Boolean deleted;
    }
    @Mapper public interface TargetMapper {
        record SerialsQuery(long tenantId,List<String> serials,boolean preview){}
        record ProductCatalogueQuery(long tenantId){}
        void selectDeviceBase(SourceQuery<Parameters> query,ResultHandler<? super DeviceSource> handler);
        List<Shipment> selectLatestShipments(@Param("query") SerialsQuery query);
        List<Product> selectProducts(@Param("query") ProductCatalogueQuery query);
        List<DeviceRow> selectExisting(@Param("query") SerialsQuery query);
        int insertDeviceBase(SyncBatch<DeviceSource,Parameters> batch);
    }
}
