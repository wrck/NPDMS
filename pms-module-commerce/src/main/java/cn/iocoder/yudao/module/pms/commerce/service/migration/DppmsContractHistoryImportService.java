package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsContractHistoryImportApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsContractHistoryImportApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.history.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.history.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.history.query.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DppmsContractHistoryImportService implements DppmsContractHistoryImportApi {
    private final ProjectScopeApi scopes;
    private final ProjectContractHistoryMapper relations;
    private final ContractReceivableMapper receivables;
    private static final List<String> RECEIVABLE_FIELDS=List.of("contractNo","sourceBatchCode","projectName","sourceOrderNo","customerCode","customerName","contractAmount","deliveredAmount","collectedAmount","sourceCollectedMoneyRatio","receivableAmount","overdueAmount","marketName","departmentName","industryName","marketingRepresentativeName","currencyName","sourceCreatedBy","sourceCreatedAt","sourceUpdatedBy","sourceUpdatedAt","sourceEffectiveFrom","sourceEffectiveTo","importBatchNo","contractCreateTime","projectCode","marketCode","departmentCode","marketingRepresentativeCode","latestShipTime","secondaryRepresentativeCode","sourceDataSource","systemSourceKey","industryCode","expendSourceKey","originalDivisionValues","sourceRowId");
    private static final List<String> RELATION_FIELDS=List.of("sourceRowId","sourceProjectKey","sourceProjectGroupId",
            "contractNo","projectGroupCode","sourceCreatedAt","sourceCreatedBy","sourceUpdatedAt","sourceUpdatedBy");

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<NativeEntityMigrationRow> importHistory(Command command) {
        if(command==null || command.relations()==null || command.receivables()==null || command.sourceProjectKey()==null
                || command.sourceProjectKey().isBlank())throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_ARGUMENT_INVALID");
        requireScope(command.tenantId(),command.actorId(),command.projectId(),ProjectScopeApi.ACTION_MANAGE);
        // Validate the complete source chain before any business insert.
        for(var source:command.relations()) {
            sourceId(source.sourceKey());revision(source.sourceChecksum());
            if(source.sourceProjectGroupId()==null || source.contractNo()==null || source.projectGroupCode()==null)
                throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_SOURCE_RELATION_INVALID");
        }
        for(var source:command.receivables()) {
            sourceId(source.sourceKey());revision(source.sourceChecksum());
            if(source.contractNo()==null || command.relations().stream().noneMatch(r->Objects.equals(r.contractNo(),source.contractNo())))
                throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_RECEIVABLE_NOT_ASSOCIATED");
            if(source.contractAmount()==null || source.deliveredAmount()==null || source.collectedAmount()==null)
                throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_REQUIRED_AMOUNT_MISSING");
            if(source.sourceCollectedMoneyRatio()!=null && !Double.isFinite(source.sourceCollectedMoneyRatio()))
                throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_RATIO_INVALID");
        }
        var result=new ArrayList<NativeEntityMigrationRow>();
        for(var source:command.relations()) {
            var incoming=relation(command,source);
            var old=relations.selectSourceRevisionForUpdate(new ContractHistoryRevisionQuery(command.tenantId(),command.projectId(),incoming.getSourceRowId(),incoming.getSourceRevisionHash()));
            if(old==null) {
                incoming.setId(IdWorker.getId());
                var contracts=relations.selectExactCurrentContractIds(new ContractHistoryContractQuery(command.tenantId(),command.projectId(),source.contractNo(),LocalDateTime.now()));
                if(contracts.size()==1)incoming.setContractId(contracts.getFirst());
                relations.insert(incoming);old=incoming;
            } else requireOriginal(old,incoming,RELATION_FIELDS);
            result.add(mapped("contracts",source.sourceKey(),old.getId(),"ProjectContractHistory","com_project_contract_history"));
        }
        for(var source:command.receivables()) {
            var incoming=receivable(command.tenantId(),source);
            var old=receivables.selectSourceRevisionForUpdate(new ContractReceivableRevisionQuery(command.tenantId(),incoming.getSourceRowId(),incoming.getSourceRevisionHash()));
            if(old==null){incoming.setId(IdWorker.getId());receivables.insert(incoming);old=incoming;}
            else requireOriginal(old,incoming,RECEIVABLE_FIELDS);
            result.add(mapped("contract-receivable-history",source.sourceKey(),old.getId(),"ContractReceivable","com_contract_receivable"));
        }
        return List.copyOf(result);
    }
    private static ProjectContractHistoryDO relation(Command command,Relation source) {
        var row=new ProjectContractHistoryDO();row.setTenantId(command.tenantId());row.setProjectId(command.projectId());
        row.setSourceProjectKey(command.sourceProjectKey());row.setSourceProjectGroupId(source.sourceProjectGroupId());
        row.setContractNo(source.contractNo());row.setProjectGroupCode(source.projectGroupCode());
        row.setSourceCreatedAt(source.sourceCreatedAt());row.setSourceCreatedBy(source.sourceCreatedBy());
        row.setSourceUpdatedAt(source.sourceUpdatedAt());row.setSourceUpdatedBy(source.sourceUpdatedBy());
        row.setSourceRowId(sourceId(source.sourceKey()));row.setSourceRevisionHash(revision(source.sourceChecksum()));
        row.setSourceSystem("DPPMS");row.setSourceRecordKey(source.sourceKey()+":"+row.getSourceRevisionHash());
        return row;
    }
    private static ContractReceivableDO receivable(Long tenant,Receivable source) {
        var row=new ContractReceivableDO();row.setTenantId(tenant);
        row.setContractNo(source.contractNo());
        row.setSourceBatchCode(source.sourceBatchCode());
        row.setProjectName(source.projectName());
        row.setSourceOrderNo(source.sourceOrderNo());
        row.setCustomerCode(source.customerCode());
        row.setCustomerName(source.customerName());
        row.setContractAmount(source.contractAmount());
        row.setDeliveredAmount(source.deliveredAmount());
        row.setCollectedAmount(source.collectedAmount());
        row.setSourceCollectedMoneyRatio(source.sourceCollectedMoneyRatio());
        row.setReceivableAmount(source.receivableAmount());
        row.setOverdueAmount(source.overdueAmount());
        row.setMarketName(source.marketName());
        row.setDepartmentName(source.departmentName());
        row.setIndustryName(source.industryName());
        row.setMarketingRepresentativeName(source.marketingRepresentativeName());
        row.setCurrencyName(source.currencyName());
        row.setSourceCreatedBy(source.sourceCreatedBy());
        row.setSourceCreatedAt(source.sourceCreatedAt());
        row.setSourceUpdatedBy(source.sourceUpdatedBy());
        row.setSourceUpdatedAt(source.sourceUpdatedAt());
        row.setSourceEffectiveFrom(source.sourceEffectiveFrom());
        row.setSourceEffectiveTo(source.sourceEffectiveTo());
        row.setImportBatchNo(source.importBatchNo());
        row.setContractCreateTime(source.contractCreateTime());
        row.setProjectCode(source.projectCode());
        row.setMarketCode(source.marketCode());
        row.setDepartmentCode(source.departmentCode());
        row.setMarketingRepresentativeCode(source.marketingRepresentativeCode());
        row.setLatestShipTime(source.latestShipTime());
        row.setSecondaryRepresentativeCode(source.secondaryRepresentativeCode());
        row.setSourceDataSource(source.sourceDataSource());
        row.setSystemSourceKey(source.systemSourceKey()==null?null:source.systemSourceKey().toString());
        row.setIndustryCode(source.industryCode()==null?null:source.industryCode().toString());
        row.setExpendSourceKey(source.expendSourceKey()==null?null:source.expendSourceKey().toString());
        var original=JsonNodeFactory.instance.objectNode();
        original.put("systemSourceKey",source.originalSystemId());
        original.put("expendSourceKey",source.originalExpendId());
        original.put("industryName",source.originalIndustryName());
        row.setOriginalDivisionValues(JsonUtils.toJsonString(original));
        // The source DOUBLE is authoritative history. Populate the decimal projection only when exact.
        if(source.sourceCollectedMoneyRatio()!=null) {
            try {var ratio=BigDecimal.valueOf(source.sourceCollectedMoneyRatio()).setScale(6,java.math.RoundingMode.UNNECESSARY);
                if(ratio.precision()<=18)row.setCollectedRatio(ratio);
            } catch(ArithmeticException ignored) { /* The original is retained in source_collected_money_ratio. */ }
        }
        row.setSourceRowId(sourceId(source.sourceKey()));row.setSourceRevisionHash(revision(source.sourceChecksum()));
        row.setSourceSystem("DPPMS");row.setSourceRecordKey(source.sourceKey()+":"+row.getSourceRevisionHash());
        row.setSourceSyncTime(source.sourceUpdatedAt());row.setMappingStatus("PENDING_COMPANY");
        return row;
    }
    private static void requireOriginal(Object previous,Object incoming,List<String> fields) {
        var old=new BeanWrapperImpl(previous);var next=new BeanWrapperImpl(incoming);
        if(Boolean.TRUE.equals(old.getPropertyValue("deleted")))throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_DELETED_CONFLICT");
        for(var field:fields) {
            Object a=old.getPropertyValue(field),b=next.getPropertyValue(field);
            boolean same;
            if(a instanceof BigDecimal x && b instanceof BigDecimal y)same=x.compareTo(y)==0;
            else if("originalDivisionValues".equals(field) && a!=null && b!=null)
                same=JsonUtils.parseObject(a.toString(),tools.jackson.databind.JsonNode.class).equals(JsonUtils.parseObject(b.toString(),tools.jackson.databind.JsonNode.class));
            else same=Objects.equals(a,b);
            if(!same)throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_IMMUTABLE_CONFLICT:"+field);
        }
    }
    private static Long sourceId(String value) {
        if(value==null || !value.matches("-?[0-9]+"))throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_SOURCE_ID_INVALID");
        try{return Long.valueOf(value);}catch(NumberFormatException error){throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_SOURCE_ID_INVALID");}
    }
    private static String revision(String value) {
        if(value==null || !value.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_SOURCE_CHECKSUM_INVALID");
        return value;
    }
    private static NativeEntityMigrationRow mapped(String domain,String key,Long id,String type,String table) {
        return NativeEntityMigrationRow.mapped(domain,key,new ExternalTargetMapping("COM",type,table,id,"BUSINESS_ENTITY",0));
    }
    @Override public Page page(Query query) {
        if(query==null || query.pageNo()<1 || query.pageSize()<1 || query.pageSize()>100)throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_PAGE_INVALID");
        requireScope(query.tenantId(),query.actorId(),query.projectId(),ProjectScopeApi.ACTION_VIEW);
        var scope=new ContractHistoryPageQuery(query.tenantId(),query.projectId(),Math.multiplyExact(query.pageNo()-1,query.pageSize()),query.pageSize());
        return switch(query.kind()) {
            case "contracts" -> new Page(relations.selectProjectPage(scope).stream().map(r->row(r,r.getId(),r.getSourceRowId(),RELATION_FIELDS)).toList(),relations.countProject(scope));
            case "contract-receivable-history" -> new Page(receivables.selectProjectPage(scope).stream().map(r->row(r,r.getId(),r.getSourceRowId(),RECEIVABLE_FIELDS)).toList(),receivables.countProject(scope));
            default -> throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_KIND_INVALID");
        };
    }
    private static Row row(Object value,Long id,Long sourceId,List<String> sourceFields) {
        var fields=new LinkedHashMap<String,Object>();var bean=new BeanWrapperImpl(value);
        for(var field:sourceFields)fields.put(field,bean.getPropertyValue(field));
        for(var field:List.of("sourceSystem","sourceRecordKey","sourceRevisionHash","contractId"))fields.put(field,bean.getPropertyValue(field));
        return new Row(id,sourceId==null?null:sourceId.toString(),Collections.unmodifiableMap(fields));
    }
    private void requireScope(Long tenant,Long actor,Long project,String action) {
        if(!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()))throw new IllegalArgumentException("DPPMS_NATIVE_TENANT_MISMATCH");
        if(actor==null || actor<=0 || project==null || project<=0)throw new IllegalArgumentException("DPPMS_CONTRACT_HISTORY_ARGUMENT_INVALID");
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,project,action));
        if(scope==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(project))
            throw new org.springframework.security.access.AccessDeniedException("DPPMS_CONTRACT_HISTORY_PROJECT_FORBIDDEN");
    }
}
