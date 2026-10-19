package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Independent CRM customer entry; formal CRM mapping and this suite's baseline must both agree. */
@Component
@RequiredArgsConstructor
public class P10CrmCustomerSyncTask extends AbstractSyncTask<P10CrmCustomerSyncTask.CustomerSource,Void> {
    private final TargetMapper customers;
    @Override public String taskCode(){return "BASE_P10";}
    @Override public String taskName(){return "CRM客户主档";}
    @Override public String sourceSystem(){return "CRM";}
    @Override public Set<String> requiredPermissions(){return Set.of("pms:customer:query");}
    @Override public void work(SyncContext<Void> context){syncData(context.forData("Customer",false,true));}
    @Override protected void syncDataBefore(SyncContext<Void> context){
        super.syncDataBefore(context);
        if(customers.erpColumnReady()!=1)throw new IllegalStateException("P10目标缺少已审核列cus_customer_master.erp_customer_code；不执行迁移");
    }
    @Override protected void syncDataInsertBefore(SyncBatch<CustomerSource,Void> batch){
        var context=batch.context();
        var keys=batch.rows().stream().map(CustomerSource::getSourceKey).toList();
        var codes=batch.rows().stream().map(CustomerSource::getCode).toList();
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("CUSTOMER",keys));
        var mappings=new HashMap<String,CustomerMapping>();
        for(var row:customers.selectMappings(new TargetMapper.SourceKeysQuery(context.tenantId(),keys,context.preview())))
            if(!keys.contains(row.getSourceKey()) || mappings.putIfAbsent(row.getSourceKey(),row)!=null)
                throw new IllegalStateException("CRM客户来源映射不唯一或原身份不同");
        var ids=mappings.values().stream().map(CustomerMapping::getCustomerId).toList();
        var targets=customers.selectCustomers(new TargetMapper.CustomersQuery(context.tenantId(),ids,codes,context.preview()));
        var byId=new HashMap<Long,CustomerRow>();var byCode=new HashMap<String,List<CustomerRow>>();
        for(var old:targets){
            if(old.getTenantId()!=context.tenantId() || byId.putIfAbsent(old.getId(),old)!=null)throw new IllegalStateException("CRM客户目标身份重复或跨租户");
            byCode.computeIfAbsent(old.getCode().toLowerCase(Locale.ROOT),ignored->new ArrayList<>()).add(old);
        }
        for(var source:batch.rows()){
            validate(source);source.setSyncAction(SyncWriteAction.UNCHANGED);
            var mapping=mappings.get(source.getSourceKey());var binding=bindings.get(source.getSourceKey());
            var old=mapping==null?null:byId.get(mapping.getCustomerId());
            if(mapping!=null && (old==null || binding==null || binding.targetId()!=old.getId() || Boolean.TRUE.equals(old.getDeleted())
                    || !"CRM_SYNC".equals(old.getSourceType()) || !Objects.equals(old.getSourceKey(),source.getSourceKey())
                    || !Objects.equals(binding.fingerprint(),fingerprint(old,mapping))))
                throw new IllegalStateException("CRM客户正式映射或独立来源基线缺失、变化；禁止接管");
            if(mapping==null && binding!=null)throw new IllegalStateException("CRM客户独立绑定的正式映射缺失");
            var occupied=byCode.getOrDefault(source.getCode().toLowerCase(Locale.ROOT),List.of());
            if(occupied.stream().anyMatch(row->old==null || !Objects.equals(row.getId(),old.getId())))
                throw new IllegalStateException("客户编码已占用；禁止接管既有客户");
            int version=old==null?1:source.getDataAsOf().compareTo(LocalDateTime.parse(mapping.getSourceVersion().replace(' ','T')));
            if(version<0)continue;
            var row=new CustomerRow();if(old!=null)org.springframework.beans.BeanUtils.copyProperties(old,row);
            copySource(source,row);
            if(old!=null && version==0){
                if(!business(old).equals(business(row)))throw new IllegalStateException("CRM客户同版本载荷冲突");
                continue;
            }
            row.setId(old==null?(context.preview()?null:IdWorker.getId()):old.getId());row.setTenantId(context.tenantId());
            row.setSourceType("CRM_SYNC");row.setSyncStatus("SYNCED");row.setReconciliationPending(false);row.setDeleted(false);
            row.setVersion(old==null?0L:Math.addExact(old.getVersion(),1));
            row.setCreator(old==null?Long.toString(context.actorId()):old.getCreator());row.setCreateTime(old==null?context.startedAt().withNano(0):old.getCreateTime());
            row.setUpdater(Long.toString(context.actorId()));row.setUpdateTime(context.startedAt().withNano(0));
            var next=new CustomerMapping();if(mapping!=null)org.springframework.beans.BeanUtils.copyProperties(mapping,next);
            next.setId(mapping==null?(context.preview()?null:IdWorker.getId()):mapping.getId());next.setTenantId(context.tenantId());next.setCustomerId(row.getId());
            next.setSourceSystem("CRM");next.setSourceKey(source.getSourceKey());next.setSourceVersion(source.getSourceVersion());
            if(mapping==null){next.setEffectiveFrom(source.getDataAsOf());next.setCreator(row.getCreator());next.setCreateTime(row.getCreateTime());}
            next.setUpdater(row.getUpdater());next.setUpdateTime(row.getUpdateTime());next.setDeleted(false);
            source.setTarget(row);source.setMapping(next);source.setExpectedVersion(old==null?null:old.getVersion());
            source.setExpectedSourceVersion(mapping==null?null:mapping.getSourceVersion());source.setPreviousFingerprint(binding==null?null:binding.fingerprint());
            source.setSyncAction(old==null?SyncWriteAction.INSERT:SyncWriteAction.UPDATE);
        }
    }
    @Override protected SyncCounts syncDataCount(SyncBatch<CustomerSource,Void> batch){return SyncCounts.fromActions(batch.rows(),CustomerSource::getSyncAction);}
    @Override protected void syncDataInsert(SyncBatch<CustomerSource,Void> batch){
        var fresh=batch.matching(row->row.getSyncAction()==SyncWriteAction.INSERT);
        super.syncDataInsert(fresh);super.syncDataUpdate(batch.matching(row->row.getSyncAction()==SyncWriteAction.UPDATE));
        if(!fresh.rows().isEmpty() && customers.insertMappings(fresh)!=fresh.rows().size())throw new IllegalStateException("CRM客户正式来源映射写入冲突");
        for(var source:batch.rows())if(source.getSyncAction()==SyncWriteAction.UPDATE && customers.updateMapping(source)!=1)
            throw new IllegalStateException("CRM客户来源版本发生并发变化");
    }
    @Override protected void syncDataInsertAfter(SyncBatch<CustomerSource,Void> batch){
        batch.context().identities().saveAll(batch.rows().stream().filter(row->row.getSyncAction()!=SyncWriteAction.UNCHANGED)
                .map(row->new SyncIdentityStore.Save("CUSTOMER",row.getSourceKey(),row.getTarget().getId(),fingerprint(row.getTarget(),row.getMapping()),row.getPreviousFingerprint())).toList());
    }
    static void validate(CustomerSource source){
        if(source.getMigrationIssue()!=null)throw new IllegalArgumentException("CRM客户原记录存在未决问题："+source.getMigrationIssue());
        if(source.getSourceKey()==null || source.getSourceKey().isBlank() || source.getCode()==null || source.getCode().isBlank()
                || source.getName()==null || source.getName().isBlank() || source.getSourceVersion()==null || source.getDataAsOf()==null)
            throw new IllegalArgumentException("CRM客户来源身份、名称、编码或版本缺失");
        if(!Set.of("ENABLED","DISABLED").contains(Objects.toString(source.getLifecycleStatus(),"")))throw new IllegalArgumentException("CRM客户来源状态未定义");
        checkTextLength(source.getSourceKey(),128);checkTextLength(source.getCode(),64);checkTextLength(source.getName(),128);
        checkTextLength(source.getErpCustomerCode(),50);checkTextLength(source.getContactPhone(),64);checkTextLength(source.getContactEmail(),128);checkTextLength(source.getAddress(),255);
        for(var value:Arrays.asList(source.getDepartmentCode(),source.getMarketCode(),source.getSystemCode(),source.getExpendCode(),source.getIndustryCode()))checkTextLength(value,64);
        for(var value:Arrays.asList(source.getDepartmentName(),source.getMarketName(),source.getSystemName(),source.getExpendName(),source.getIndustryName()))checkTextLength(value,128);
        if(source.getDataAsOf().getNano()!=0)throw new IllegalArgumentException("P10源版本时间不能无损写入目标data_as_of秒精度");
    }
    static void copySource(CustomerSource source,CustomerRow row){
        // Preserve the approved optional-field update semantics; ERP code explicitly carries NULL.
        if(source.getCode()!=null)row.setCode(source.getCode());
        if(source.getName()!=null)row.setName(source.getName());
        if(source.getShortName()!=null)row.setShortName(source.getShortName());
        if(source.getContactPhone()!=null)row.setContactPhone(source.getContactPhone());
        if(source.getContactEmail()!=null)row.setContactEmail(source.getContactEmail());
        if(source.getAddress()!=null)row.setAddress(source.getAddress());
        if(source.getDepartmentCode()!=null)row.setDepartmentCode(source.getDepartmentCode());
        if(source.getDepartmentName()!=null)row.setDepartmentName(source.getDepartmentName());
        if(source.getMarketCode()!=null)row.setMarketCode(source.getMarketCode());
        if(source.getMarketName()!=null)row.setMarketName(source.getMarketName());
        if(source.getSystemCode()!=null)row.setSystemCode(source.getSystemCode());
        if(source.getSystemName()!=null)row.setSystemName(source.getSystemName());
        if(source.getExpendCode()!=null)row.setExpendCode(source.getExpendCode());
        if(source.getExpendName()!=null)row.setExpendName(source.getExpendName());
        if(source.getIndustryCode()!=null)row.setIndustryCode(source.getIndustryCode());
        if(source.getIndustryName()!=null)row.setIndustryName(source.getIndustryName());
        if(source.getLifecycleStatus()!=null)row.setLifecycleStatus(source.getLifecycleStatus());
        row.setErpCustomerCode(source.getErpCustomerCode());row.setSourceKey(source.getSourceKey());row.setSourceVersion(source.getSourceVersion());row.setDataAsOf(source.getDataAsOf());
    }
    static List<Object> business(CustomerRow row){return Arrays.asList(row.getCode(),row.getErpCustomerCode(),row.getName(),row.getShortName(),row.getContactPhone(),row.getContactEmail(),row.getAddress(),row.getDepartmentCode(),row.getDepartmentName(),row.getMarketCode(),row.getMarketName(),row.getSystemCode(),row.getSystemName(),row.getExpendCode(),row.getExpendName(),row.getIndustryCode(),row.getIndustryName(),row.getLifecycleStatus());}
    static String fingerprint(CustomerRow row,CustomerMapping mapping){return SyncFingerprint.of(Arrays.asList(row.getId(),row.getTenantId(),row.getVersion(),row.getCode(),row.getErpCustomerCode(),row.getName(),row.getShortName(),row.getCustomerLevel(),row.getLifecycleStatus(),row.getSourceType(),row.getSourceKey(),row.getSourceVersion(),row.getSyncStatus(),row.getTemporaryReason(),row.getContactPhone(),row.getContactEmail(),row.getDepartmentCode(),row.getDepartmentName(),row.getMarketCode(),row.getMarketName(),row.getSystemCode(),row.getSystemName(),row.getExpendCode(),row.getExpendName(),row.getIndustryCode(),row.getIndustryName(),row.getAddress(),row.getRemark(),row.getCreator(),row.getUpdater(),row.getDataAsOf(),row.getCreateTime(),row.getUpdateTime(),row.getReconciliationPending(),row.getDeleted(),mapping.getId(),mapping.getTenantId(),mapping.getCustomerId(),mapping.getSourceSystem(),mapping.getSourceKey(),mapping.getSourceVersion(),mapping.getCreator(),mapping.getUpdater(),mapping.getEffectiveFrom(),mapping.getEffectiveTo(),mapping.getCreateTime(),mapping.getUpdateTime(),mapping.getDeleted()));}
    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper=true)
    public static class CustomerSource extends CustomerRow {
        private String migrationIssue;private CustomerRow target;private CustomerMapping mapping;
        private Long expectedVersion;private String expectedSourceVersion;private String previousFingerprint;
        private SyncWriteAction syncAction=SyncWriteAction.UNCHANGED;
    }
    @lombok.Data
    public static class CustomerRow {
        private Long id;
        private Long tenantId;
        private Long version;
        private String code;
        private String erpCustomerCode;
        private String name;
        private String shortName;
        private String customerLevel;
        private String lifecycleStatus;
        private String sourceType;
        private String sourceKey;
        private String sourceVersion;
        private String syncStatus;
        private String temporaryReason;
        private String contactPhone;
        private String contactEmail;
        private String departmentCode;
        private String departmentName;
        private String marketCode;
        private String marketName;
        private String systemCode;
        private String systemName;
        private String expendCode;
        private String expendName;
        private String industryCode;
        private String industryName;
        private String address;
        private String remark;
        private String creator;
        private String updater;
        private LocalDateTime dataAsOf;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Boolean reconciliationPending;
        private Boolean deleted;
    }
    @lombok.Data
    public static class CustomerMapping {
        private Long id;
        private Long tenantId;
        private Long customerId;
        private String sourceSystem;
        private String sourceKey;
        private String sourceVersion;
        private String creator;
        private String updater;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Boolean deleted;
    }
    public interface SourceMapper {void selectCustomer(SourceQuery<Void> query,ResultHandler<? super CustomerSource> handler);}
    @Mapper public interface TargetMapper {
        @InterceptorIgnore(tenantLine="true",dataPermission="true")
        int erpColumnReady();
        record SourceKeysQuery(long tenantId,List<String> keys,boolean preview){}
        record CustomersQuery(long tenantId,List<Long> ids,List<String> codes,boolean preview){}
        List<CustomerMapping> selectMappings(@Param("query") SourceKeysQuery query);
        List<CustomerRow> selectCustomers(@Param("query") CustomersQuery query);
        int insertCustomer(SyncBatch<CustomerSource,Void> batch);
        int updateCustomer(@Param("row") CustomerSource row);
        int insertMappings(SyncBatch<CustomerSource,Void> batch);
        int updateMapping(@Param("row") CustomerSource row);
    }
}
