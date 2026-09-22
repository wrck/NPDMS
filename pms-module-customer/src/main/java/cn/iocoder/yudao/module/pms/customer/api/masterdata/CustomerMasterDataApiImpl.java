package cn.iocoder.yudao.module.pms.customer.api.masterdata;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataCommand;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataResult;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerExternalMappingDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerExternalMappingMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.CurrentCustomerMappingQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustomerMasterDataApiImpl implements CustomerMasterDataApi {
    private static final String CRM = "CRM";
    private final CustomerMasterMapper customers;
    private final CustomerExternalMappingMapper mappings;

    @Override
    @Transactional
    public CustomerMasterDataResult apply(CustomerMasterDataCommand command) {
        validate(command);
        var mapping=mappings.selectCurrent(CurrentCustomerMappingQuery.bySource(command.tenantId(),CRM,command.sourceKey()));
        if(mapping==null)return create(command);
        var customer=customers.selectIncludingDeletedForUpdate(command.tenantId(),mapping.getCustomerId());
        if(customer==null)throw new IllegalStateException("CRM客户映射目标不存在");
        int version=compareVersion(command.sourceVersion(),mapping.getSourceVersion());
        if(version<0)return new CustomerMasterDataResult(customer.getId(),customer.getVersion().longValue(),true);
        if(version==0) {
            if(!same(customer,command))throw new IllegalStateException("CRM客户同版本载荷冲突");
            return new CustomerMasterDataResult(customer.getId(),customer.getVersion().longValue(),true);
        }
        long nextVersion=customer.getVersion().longValue()+1;
        copy(customer,command);
        if(customers.updateById(customer)!=1)throw new IllegalStateException("CRM客户版本变化，当前分块已回滚");
        mapping.setSourceVersion(command.sourceVersion());mappings.updateById(mapping);
        return new CustomerMasterDataResult(customer.getId(),nextVersion,false);
    }

    private CustomerMasterDataResult create(CustomerMasterDataCommand c) {
        var occupied=customers.selectByTenantIdAndCode(c.tenantId(),c.customerCode());
        if(occupied!=null)throw new IllegalStateException("客户编码已存在且未建立CRM来源映射");
        var customer=new CustomerMasterDO();customer.setTenantId(c.tenantId());copy(customer,c);
        customer.setSourceType("CRM_SYNC");customer.setSyncStatus("SYNCED");customer.setReconciliationPending(false);
        customers.insert(customer);
        var mapping=new CustomerExternalMappingDO();mapping.setTenantId(c.tenantId());mapping.setCustomerId(customer.getId());
        mapping.setSourceSystem(CRM);mapping.setSourceKey(c.sourceKey());mapping.setSourceVersion(c.sourceVersion());
        mapping.setEffectiveFrom(c.dataAsOf());mappings.insert(mapping);
        return new CustomerMasterDataResult(customer.getId(),0L,false);
    }

    private static void copy(CustomerMasterDO d,CustomerMasterDataCommand c) {
        d.setCode(c.customerCode());d.setName(c.customerName());d.setShortName(c.shortName());
        d.setContactPhone(c.contactPhone());d.setContactEmail(c.contactEmail());d.setAddress(c.address());
        d.setDepartmentCode(c.departmentCode());d.setDepartmentName(c.departmentName());
        d.setMarketCode(c.marketCode());d.setMarketName(c.marketName());d.setSystemCode(c.systemCode());d.setSystemName(c.systemName());
        d.setExpendCode(c.expendCode());d.setExpendName(c.expendName());d.setIndustryCode(c.industryCode());d.setIndustryName(c.industryName());
        d.setLifecycleStatus(c.lifecycleStatus());d.setSourceKey(c.sourceKey());d.setSourceVersion(c.sourceVersion());d.setDataAsOf(c.dataAsOf());
    }
    private static boolean same(CustomerMasterDO d,CustomerMasterDataCommand c) {
        var copy=new CustomerMasterDO();copy(copy,c);
        return Objects.equals(d.getCode(),copy.getCode())&&Objects.equals(d.getName(),copy.getName())
                &&Objects.equals(d.getShortName(),copy.getShortName())
                &&Objects.equals(d.getContactPhone(),copy.getContactPhone())&&Objects.equals(d.getContactEmail(),copy.getContactEmail())
                &&Objects.equals(d.getAddress(),copy.getAddress())&&Objects.equals(d.getDepartmentCode(),copy.getDepartmentCode())
                &&Objects.equals(d.getDepartmentName(),copy.getDepartmentName())&&Objects.equals(d.getMarketCode(),copy.getMarketCode())
                &&Objects.equals(d.getMarketName(),copy.getMarketName())&&Objects.equals(d.getSystemCode(),copy.getSystemCode())
                &&Objects.equals(d.getSystemName(),copy.getSystemName())&&Objects.equals(d.getExpendCode(),copy.getExpendCode())
                &&Objects.equals(d.getExpendName(),copy.getExpendName())&&Objects.equals(d.getIndustryCode(),copy.getIndustryCode())
                &&Objects.equals(d.getIndustryName(),copy.getIndustryName())
                &&Objects.equals(d.getLifecycleStatus(),copy.getLifecycleStatus());
    }
    private static int compareVersion(String incoming,String current) {return incoming.compareTo(current);}
    private static void validate(CustomerMasterDataCommand c) {
        if(c==null||!Objects.equals(TenantContextHolder.getRequiredTenantId(),c.tenantId())||blank(c.customerCode())||blank(c.customerName())
                ||blank(c.sourceKey())||blank(c.sourceVersion())||c.dataAsOf()==null||blank(c.operationId()))
            throw new IllegalArgumentException("CRM客户主数据命令不完整");
        if(!java.util.Set.of("ENABLED","DISABLED").contains(c.lifecycleStatus()))throw new IllegalArgumentException("CRM客户状态无效");
    }
    private static boolean blank(String value){return value==null||value.isBlank();}
}
