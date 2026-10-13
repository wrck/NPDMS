package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractReceivableMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.history.ContractReceivableDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractReceivableMigrationMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class ContractReceivableMigrationService implements ContractReceivableMigrationApi {
    private final ContractReceivableMigrationMapper mapper;
    private final AdminUserApi users;
    private final PermissionApi permissions;
    // All 52 existing physical fields participate in concurrency protection; no source mirrors are written.
    static final List<String> FIELDS = List.of("id","tenantId","contractId","contractNo","companyId","companyCode",
            "companyName","companyResolutionSource","mappingStatus","projectName","sourceOrderNo","customerCode",
            "customerName","contractAmount","deliveredAmount","collectedAmount","collectedRatio","receivableAmount",
            "overdueAmount","currencyName","marketCode","marketName","departmentId","departmentCode","departmentName",
            "industryName","marketingRepresentativeName","sourceBatchCode","importBatchNo","projectCode","systemSourceKey",
            "systemCode","systemName","industryCode","expendSourceKey","expendCode","expendName","marketingRepresentativeCode",
            "secondaryRepresentativeCode","originalDivisionValues","sourceEffectiveFrom","sourceEffectiveTo","contractCreateTime",
            "latestShipTime","sourceSystem","sourceRecordKey","sourceSyncTime","creator","createTime","updater","updateTime","deleted");
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Result> preview(Request request) { return execute(request,false); }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Request request) { return execute(request,true); }
    private List<Result> execute(Request request,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(request==null || !Objects.equals(request.tenantId(),tenant) || request.actorId()==null
                || request.owner()==null || !request.owner().matches("integration:"+tenant+":[1-9][0-9]*")
                || request.entries()==null) fail("运行范围无效");
        users.validateUser(request.actorId());
        if(!permissions.hasAnyPermissions(request.actorId(),"pms:integration:execute")
                || !permissions.hasAnyPermissions(request.actorId(),"pms:commerce:contract:query")) fail("缺少迁移或合同权限");
        var result=new ArrayList<Result>();var keys=new HashSet<String>();
        for(var entry:request.entries()) {
            if(entry==null || !keys.add(entry.sourceKey()) || entry.originalFields()==null
                    || !entry.originalFields().keySet().equals(new HashSet<>(ORIGINAL_COLUMNS))
                    || !Objects.equals(entry.sourceKey(),entry.originalFields().get("id"))
                    || !entry.sourceKey().matches("[1-9][0-9]*")) fail("原39字段或来源身份缺失/重复");
            var source=entry.originalFields();
            // Explicit user decision: a null contract number does not form a migrated collection-information row.
            if(source.get("contract_num")==null) { result.add(new Result(entry.sourceKey(),null,"SKIPPED",Map.of(),null));continue; }
            var incoming=entity(entry,tenant);
            var found=mapper.selectSourceForUpdate(new ContractReceivableMigrationMapper.SourceQuery(tenant,incoming.getSourceSystem(),entry.sourceKey()));
            if(found.size()>1) fail("来源身份多义");
            var old=found.isEmpty()?null:found.getFirst();
            if(entry.targetId()!=null && (old==null || !Objects.equals(entry.targetId(),old.getId()))
                    || old!=null && (Boolean.TRUE.equals(old.getDeleted()) || entry.expectedFingerprint()==null
                    || !entry.expectedFingerprint().equals(fingerprint(old)))) fail("正式数据或绑定变化，禁止覆盖");
            if(old==null && entry.expectedFingerprint()!=null) fail("绑定目标不存在");
            if(old!=null) {
                // Keep established current master/directory associations; source facts have independent formal fields.
                var from=new BeanWrapperImpl(old);var to=new BeanWrapperImpl(incoming);
                for(String name:List.of("id","contractId","companyId","companyCode","companyName","companyResolutionSource",
                        "mappingStatus","departmentId","systemCode","systemName","expendCode","expendName"))
                    to.setPropertyValue(name,from.getPropertyValue(name));
            } else {
                var contracts=mapper.selectExactContractIds(new ContractReceivableMigrationMapper.ContractQuery(tenant,incoming.getContractNo()));
                incoming.setContractId(contracts.size()==1?contracts.getFirst():null);
                incoming.setMappingStatus("PENDING_COMPANY");
            }
            String action=old==null?"CREATED":fields(old).equals(fields(incoming))?"UNCHANGED":"UPDATED";
            if(apply && !"UNCHANGED".equals(action)) {
                if(old==null)incoming.setId(IdWorker.getId());
                if((old==null?mapper.insertReceipt(incoming):mapper.updateReceipt(incoming))!=1)fail("正式写入冲突");
            }
            result.add(new Result(entry.sourceKey(),incoming.getId(),action,fields(incoming),fingerprint(incoming)));
        }
        return List.copyOf(result);
    }
    static ContractReceivableDO entity(Entry entry,long tenant) {
        var f=entry.originalFields();var row=new ContractReceivableDO();row.setTenantId(tenant);row.setDeleted(false);
        row.setContractNo(f.get("contract_num"));row.setSourceBatchCode(f.get("batch_code"));row.setProjectName(f.get("project_name"));
        row.setSourceOrderNo(f.get("order_num"));row.setCustomerCode(f.get("client_supplier_code"));row.setCustomerName(f.get("client_supplier_name"));
        row.setContractAmount(decimal(f.get("contract_money_amount"),2,20));row.setDeliveredAmount(decimal(f.get("delivered_money_amount"),2,20));
        row.setCollectedAmount(decimal(f.get("collected_money_amount"),2,20));row.setCollectedRatio(decimal(f.get("collected_money_ratio"),6,18));
        row.setReceivableAmount(decimal(f.get("receivables_money_amount"),2,20));row.setOverdueAmount(decimal(f.get("over_due_money_amount"),2,20));
        row.setMarketName(f.get("maketing_department_name"));row.setDepartmentName(f.get("office_name"));row.setIndustryName(f.get("industry_name"));
        row.setMarketingRepresentativeName(f.get("marketing_representative_name"));row.setCurrencyName(f.get("currency_name"));
        row.setCreator(entry.creator());row.setCreateTime(date(f.get("create_time")));row.setUpdater(entry.updater());
        row.setUpdateTime(date(f.get("update_time"))==null?row.getCreateTime():date(f.get("update_time")));
        row.setSourceEffectiveFrom(date(f.get("effective_from")));row.setSourceEffectiveTo(date(f.get("effective_to")));
        row.setImportBatchNo(f.get("import_batch_num"));row.setContractCreateTime(date(f.get("contract_create_date")));
        row.setProjectCode(f.get("projectCode"));row.setMarketCode(f.get("marketCode"));row.setSystemSourceKey(f.get("systemId"));
        row.setIndustryCode(f.get("industryId"));row.setDepartmentCode(f.get("officeCode"));row.setExpendSourceKey(f.get("expendId"));
        row.setMarketingRepresentativeCode(f.get("usernamec"));row.setLatestShipTime(date(f.get("latest_ship_date")));
        row.setSecondaryRepresentativeCode(f.get("usernamec2"));
        var original=new LinkedHashMap<String,Object>();original.put("systemSourceKey",f.get("systemid_o")==null?null:Integer.valueOf(f.get("systemid_o")));
        original.put("expendSourceKey",f.get("expendid_o")==null?null:Integer.valueOf(f.get("expendid_o")));original.put("industryName",f.get("industry_name_o"));
        original.replaceAll((name,value)->value==null?tools.jackson.databind.node.NullNode.getInstance():value);
        row.setOriginalDivisionValues(JsonUtils.toJsonString(original));row.setSourceSystem(f.get("dataSource"));
        row.setSourceRecordKey(entry.sourceKey());row.setSourceSyncTime(date(f.get("update_time")));
        if(row.getCreateTime()==null || row.getUpdateTime()==null || row.getCreator()==null || row.getUpdater()==null
                || row.getSourceSystem()==null || row.getSourceSystem().isBlank())fail("原审计或来源类别缺失");
        return row;
    }
    static BigDecimal decimal(String value,int scale,int precision) {
        if(value==null)return null;var number=new BigDecimal(value).setScale(scale,RoundingMode.UNNECESSARY);
        if(number.precision()>precision)fail("金额/比例超出正式精度");return number;
    }
    static LocalDateTime date(String value) {
        if(value==null)return null;String text=value.replace(' ','T');return LocalDateTime.parse(text.length()==10?text+"T00:00:00":text);
    }
    static Map<String,Object> fields(ContractReceivableDO row) {
        var bean=new BeanWrapperImpl(row);var values=new LinkedHashMap<String,Object>();
        for(String name:FIELDS) {Object value=bean.getPropertyValue(name);
            if("originalDivisionValues".equals(name) && value!=null) {
                var json=new TreeMap<>(JsonUtils.parseObject(value.toString(),Map.class));
                json.replaceAll((key,item)->item==null?tools.jackson.databind.node.NullNode.getInstance():item);
                value=JsonUtils.toJsonString(json);
            }
            values.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value instanceof BigDecimal n?n.stripTrailingZeros().toPlainString():value instanceof LocalDateTime time?time.toString():value);
        }return Collections.unmodifiableMap(values);
    }
    static String fingerprint(ContractReceivableDO row) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(fields(row)).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static void fail(String message){throw new IllegalArgumentException("DPPMS合同回款信息："+message);}
}
