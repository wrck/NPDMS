package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Device base initialization and continuous project projection; no legacy task call. */
@Component @RequiredArgsConstructor
public class P30DeviceBaseSyncTask extends AbstractSyncTask<P30DeviceBaseSyncTask.DeviceSource,P30DeviceBaseSyncTask.Parameters> {
    private final TargetMapper devices;
    private final ContractDeviceVisibilityApi contracts;
 private final cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects projectAssignments;
    public static final class Parameters {Map<String,List<Product>> products;}
    @Override protected Parameters createParameters(SyncContext<?> runtime){return new Parameters();}
    @Override public String taskCode(){return "BASE_P30";}
    @Override public String taskName(){return "正式发货设备基础承接";}
    @Override public void work(SyncContext<Parameters> context){
        syncData(context.forLocalData("DeviceBase",false,true));
        projectAssignments.refresh(context);
        if(context.runState().counts().read()==0)context.runState().warning("P30_FORMAL_DPPMS_SHIPMENTS_MISSING:0");
    }
    @Override protected void syncDataInsertBefore(SyncBatch<DeviceSource,Parameters> batch){
        var context=batch.context();var serials=batch.rows().stream().map(DeviceSource::getDeviceSn).toList();
        var serialSet=new HashSet<>(serials);
        var query=new TargetMapper.SerialsQuery(context.tenantId(),serials,context.preview());
        // The one streaming source query supplies the latest complete formal shipment snapshot.
        for(var shipment:batch.rows()){
            if(shipment.getTenantId()==null || shipment.getTenantId()!=context.tenantId() || !"DPPMS".equals(shipment.getSourceSystem()))
                throw new IllegalStateException("P30正式最新发货来源、租户或SN身份冲突");
        }
        if(context.parameters().products==null){
            context.parameters().products=new HashMap<>();
            for(var product:devices.selectProducts(new TargetMapper.ProductCatalogueQuery(context.tenantId())))
                context.parameters().products.computeIfAbsent(product.productCode(),ignored->new ArrayList<>()).add(product);
        }
        var existing=new HashMap<String,DeviceRow>();for(var old:devices.selectExisting(query))
            if(old.getTenantId()!=context.tenantId() || !serialSet.contains(old.getRequestedSn()) || existing.putIfAbsent(old.getRequestedSn(),old)!=null)
                throw new IllegalStateException("P30正式设备SN身份多义、跨租户或超出请求范围");
        for(var source:batch.rows()){
            var old=existing.get(source.getDeviceSn());
            // A later case variant must still verify the original formal SN's independent binding.
            source.setIdentitySn(old==null?source.getDeviceSn():old.getSn());
        }
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("DEVICE_BASE",batch.rows().stream().map(DeviceSource::getIdentitySn).toList()));
        var manual = projectAssignments.manualDeviceIds(new cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects.DeviceQuery(
                context.tenantId(), existing.values().stream().map(DeviceRow::getId).toList()));
        for(var source:batch.rows()){
            if(source.getDeviceSn()==null || source.getDeviceSn().isBlank())throw new IllegalArgumentException("P30正式发货SN缺失");
            checkTextLength(source.getDeviceSn(),100);
            var shipment=source;
            var matches=context.parameters().products.getOrDefault(shipment.getProductCode(),List.of());
            if(matches.size()>1)throw new IllegalStateException("P30产品正式编码身份多义");
            var product=matches.isEmpty()?null:matches.getFirst();
            var old=existing.get(source.getDeviceSn());var binding=bindings.get(source.getIdentitySn());
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            if(old!=null){
                if(manual.contains(old.getId()) && !Boolean.TRUE.equals(old.getDeleted()) && "DPPMS".equals(old.getSourceSystem())) {
                    source.setTarget(old);source.setOwned(false);continue;
                }
                if(Boolean.TRUE.equals(old.getDeleted()) || !"DPPMS".equals(old.getSourceSystem())
                        || binding!=null && (binding.targetId()!=old.getId() || !Objects.equals(binding.fingerprint(),fingerprint(old))))
                    throw new IllegalStateException("P30设备正式来源或独立保护基线已变化");
                source.setTarget(old);source.setOwned(binding!=null);
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
            source.setOwned(true);row.setOrganizationSource("UNRESOLVED");
        }
        completeOrganizations(batch);
    }
    private void completeOrganizations(SyncBatch<DeviceSource,Parameters> batch){
        var context=batch.context();
        var eligible=batch.rows().stream().filter(row->row.isOwned()&&row.getTarget().getProjectId()==null
                &&row.getTarget().getContractNo()!=null&&!row.getTarget().getContractNo().isBlank()).toList();
        if(eligible.isEmpty())return;
        var numbers=new HashSet<String>();eligible.forEach(row->numbers.add(row.getTarget().getContractNo()));
        var facts=new HashMap<String,ContractDeviceVisibilityApi.Organization>();
        for(var fact:contracts.getOrganizations(context.tenantId(),numbers))
            if(facts.putIfAbsent(fact.contractNo(),fact)!=null)throw new IllegalStateException("P30合同组织事实多义");
        for(var source:eligible){
            var old=source.getTarget();var fact=facts.get(old.getContractNo());
            // Same exact Java key and Owner fact as the device organization projection. No inference from history.
            if(fact==null){context.runState().warning("P30_DEVICE_ORGANIZATION_UNRESOLVED:RAW_CONTRACT_PRESERVED");continue;}
            if(old.getOrganizationUpdatedAt()!=null&&Objects.equals(old.getCompanyId(),fact.companyId())
                    &&Objects.equals(old.getCompanyName(),fact.companyName())&&Objects.equals(old.getDepartmentId(),fact.departmentId())
                    &&Objects.equals(old.getDepartmentCode(),fact.departmentCode())&&Objects.equals(old.getDepartmentName(),fact.departmentName())
                    &&"CONTRACT".equals(old.getOrganizationSource()))continue;
            var next=new DeviceRow();org.springframework.beans.BeanUtils.copyProperties(old,next);
            next.setCompanyId(fact.companyId());next.setCompanyName(fact.companyName());next.setDepartmentId(fact.departmentId());
            next.setDepartmentCode(fact.departmentCode());next.setDepartmentName(fact.departmentName());
            next.setOrganizationSource("CONTRACT");next.setOrganizationUpdatedAt(context.startedAt());source.setTarget(next);
            if(source.getSyncAction()!=SyncWriteAction.INSERT)source.setSyncAction(SyncWriteAction.UPDATE);
        }
    }
    @Override protected SyncCounts syncDataCount(SyncBatch<DeviceSource,Parameters> batch){return SyncCounts.fromActions(batch.rows(),DeviceSource::getSyncAction);}
    @Override protected void syncDataInsert(SyncBatch<DeviceSource,Parameters> batch){
        super.syncDataInsert(batch.matching(row->row.getSyncAction()==SyncWriteAction.INSERT));
        var changed=batch.matching(row->row.getSyncAction()==SyncWriteAction.UPDATE);
        if(!changed.rows().isEmpty()&&devices.updateDeviceOrganization(changed)!=changed.rows().size())
            throw new IllegalStateException("P30设备归属回填时正式投影已变化");
    }
    @Override protected void syncDataInsertAfter(SyncBatch<DeviceSource,Parameters> batch){
        batch.context().identities().saveAll(batch.rows().stream().filter(row->row.getSyncAction()==SyncWriteAction.INSERT)
                .map(row->new SyncIdentityStore.Save("DEVICE_BASE",row.getIdentitySn(),row.getTarget().getId(),fingerprint(row.getTarget()),null)).toList());
    }
    public static String fingerprint(DeviceRow row){return SyncFingerprint.of(Arrays.asList(row.getId(),row.getTenantId(),row.getVersion(),row.getShipmentRecordId(),row.getProjectAssignmentVersion(),row.getCustomerAssignmentVersion(),row.getSn(),row.getName(),row.getProductName(),row.getProductModel(),row.getProductCode(),row.getPackageNo(),row.getContractNo(),row.getStatus(),row.getSourceSystem(),row.getSourceKey(),row.getSourceVersion(),row.getSyncStatus(),row.getRemark(),row.getCreator(),row.getUpdater(),row.getShipmentTime(),row.getSyncedAt(),row.getCreateTime(),row.getUpdateTime(),row.getDeleted()));}
    @lombok.Data @lombok.EqualsAndHashCode(callSuper=true)
    public static class DeviceSource extends Shipment {private String identitySn;private DeviceRow target;private SyncWriteAction syncAction;private boolean owned;}
    @lombok.Data public static class Shipment {private Long id;private Long tenantId;private String deviceSn;private String sourceSystem;private String sourceKey;private String contractNo;private String packageNo;private LocalDateTime shipmentTime;private String productCode;}
    public record Product(String productCode,String productModel,String productName){}
    @lombok.Data public static class DeviceRow {
        private Long id;
        private Long tenantId;
        private Long version;
        private Long shipmentRecordId;
        private Long projectAssignmentVersion;
        private Long customerAssignmentVersion;
        private Long projectId;
        private Long companyId;
        private String companyName;
        private Long departmentId;
        private String departmentCode;
        private String departmentName;
        private String organizationSource;
        private LocalDateTime organizationUpdatedAt;
        private String sn;
        private String requestedSn;
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
        List<Product> selectProducts(@Param("query") ProductCatalogueQuery query);
        List<DeviceRow> selectExisting(@Param("query") SerialsQuery query);
        int insertDeviceBase(SyncBatch<DeviceSource,Parameters> batch);
        int updateDeviceOrganization(SyncBatch<DeviceSource,Parameters> batch);
    }
}
