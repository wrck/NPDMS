package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.customer.api.masterdata.CustomerMasterDataApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataCommand;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataResult;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
final class DirectCustomerPersistence implements CustomerMasterDataApi {
    private static final String CRM = "CRM";
    private final DirectCustomerMapper mapper;
    private final DataSource dataSource;

    @Override
    public CustomerMasterDataResult apply(CustomerMasterDataCommand command) {
        requireTransaction();return execute(command,true);
    }
    CustomerMasterDataResult preview(CustomerMasterDataCommand command){return execute(command,false);}
    private CustomerMasterDataResult execute(CustomerMasterDataCommand command,boolean apply) {
        validate(command);
        var mapping=mapper.selectCurrentMapping(new DirectCustomerMapper.SourceQuery(command.tenantId(),command.sourceKey()));
        if(mapping==null){
            if(command.customerId()!=null)throw new IllegalStateException("CRM客户既有INT绑定缺少正式来源映射");
            return create(command,apply);
        }
        if(command.customerId()!=null && !command.customerId().equals(mapping.getCustomerId()))throw new IllegalStateException("CRM客户来源映射与INT绑定目标不同");
        var customer=mapper.selectCustomerForUpdate(new DirectCustomerMapper.CustomerQuery(command.tenantId(),mapping.getCustomerId()));
        if(customer==null)throw new IllegalStateException("CRM客户映射目标不存在");
        int version=compareVersion(command.sourceVersion(),mapping.getSourceVersion());
        if(version<0)return new CustomerMasterDataResult(customer.getId(),customer.getVersion().longValue(),true);
        if(version==0) {
            if(!same(customer,command))throw new IllegalStateException("CRM客户同版本载荷冲突");
            return new CustomerMasterDataResult(customer.getId(),customer.getVersion().longValue(),true);
        }
        if(Boolean.TRUE.equals(customer.getDeleted()))throw new IllegalStateException("CRM客户正式目标已删除，禁止覆盖");
        long expected=customer.getVersion(),nextVersion=expected+1;
        copy(customer,command);
        if(apply) {
            if(mapper.updateCustomer(new DirectCustomerMapper.CustomerWrite(customer,expected))!=1)throw new IllegalStateException("CRM客户版本变化，当前分块已回滚");
            if(mapper.updateMapping(new DirectCustomerMapper.MappingWrite(command.tenantId(),mapping.getId(),mapping.getSourceVersion(),command.sourceVersion()))!=1)
                throw new IllegalStateException("CRM客户来源版本变化，当前分块已回滚");
        }
        return new CustomerMasterDataResult(customer.getId(),nextVersion,false);
    }

    private CustomerMasterDataResult create(CustomerMasterDataCommand c,boolean apply) {
        var occupied=mapper.selectCodeForUpdate(new DirectCustomerMapper.CodeQuery(c.tenantId(),c.customerCode()));
        if(occupied!=null)throw new IllegalStateException("客户编码已存在且未建立CRM来源映射");
        var customer=new DirectCustomerRow();customer.setTenantId(c.tenantId());copy(customer,c);
        customer.setSourceType("CRM_SYNC");customer.setSyncStatus("SYNCED");customer.setReconciliationPending(false);
        customer.setVersion(0L);customer.setDeleted(false);
        if(apply && mapper.insertCustomer(customer)!=1)throw new IllegalStateException("CRM客户主档写入失败");
        var mapping=new DirectCustomerMappingRow();mapping.setTenantId(c.tenantId());mapping.setCustomerId(customer.getId());
        mapping.setSourceSystem(CRM);mapping.setSourceKey(c.sourceKey());mapping.setSourceVersion(c.sourceVersion());
        mapping.setEffectiveFrom(c.dataAsOf());
        if(apply && mapper.insertMapping(mapping)!=1)throw new IllegalStateException("CRM客户来源映射写入失败");
        return new CustomerMasterDataResult(customer.getId(),0L,false);
    }

    private static void copy(DirectCustomerRow d,CustomerMasterDataCommand c) {
        d.setCode(c.customerCode());d.setErpCustomerCode(c.erpCustomerCode());d.setName(c.customerName());d.setShortName(c.shortName());
        d.setContactPhone(c.contactPhone());d.setContactEmail(c.contactEmail());d.setAddress(c.address());
        d.setDepartmentCode(c.departmentCode());d.setDepartmentName(c.departmentName());
        d.setMarketCode(c.marketCode());d.setMarketName(c.marketName());d.setSystemCode(c.systemCode());d.setSystemName(c.systemName());
        d.setExpendCode(c.expendCode());d.setExpendName(c.expendName());d.setIndustryCode(c.industryCode());d.setIndustryName(c.industryName());
        d.setLifecycleStatus(c.lifecycleStatus());d.setSourceKey(c.sourceKey());d.setSourceVersion(c.sourceVersion());d.setDataAsOf(c.dataAsOf());
    }
    private static boolean same(DirectCustomerRow d,CustomerMasterDataCommand c) {
        var copy=new DirectCustomerRow();copy(copy,c);
        return Objects.equals(d.getCode(),copy.getCode())&&Objects.equals(d.getName(),copy.getName())
                &&Objects.equals(d.getErpCustomerCode(),copy.getErpCustomerCode())
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
    private void requireTransaction() {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("客户直写必须与运行回执处于同一事务");
        var connection=DataSourceUtils.getConnection(dataSource);
        try{if(connection.getTransactionIsolation()!=Connection.TRANSACTION_REPEATABLE_READ)throw new IllegalStateException("客户直写需要REPEATABLE_READ");}
        catch(SQLException error){throw new IllegalStateException("无法确认客户直写事务隔离",error);}
        finally{DataSourceUtils.releaseConnection(connection,dataSource);}
    }
    private static boolean blank(String value){return value==null||value.isBlank();}
}
