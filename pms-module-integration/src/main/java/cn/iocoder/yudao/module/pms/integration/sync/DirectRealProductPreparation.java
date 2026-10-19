package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectRealProductMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectRealProductMapper.*;
import cn.iocoder.yudao.module.pms.integration.sync.DirectProjectAccess.Actor;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectRealProductMigrationApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectRealProductRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Formal additional project list; technical identity and full replay baselines remain with PLT. */
@RequiredArgsConstructor
final class DirectRealProductPreparation {
    public static final List<String> SHARED_COLUMNS=List.of("projectCode","orderExecNumber","productFirstName","productName",
            "productSubCode","productSubModel","productSubName","num","memo");
    public static final Set<String> ORIGINAL_COLUMNS=Set.of("id","projectId","contractNo","projectCode","orderExecNumber",
            "productFirstName","productName","productSubCode","productSubModel","productSubName","num","memo");
    public static final Set<String> UPSTREAM_COLUMNS=Set.of("projectCode","orderExecNumber","productFirstName","productName",
            "productSubCode","productSubModel","productSubName","num","memo","dataSource");
    private final javax.sql.DataSource dataSource;
    private final DirectProjectAccess access;
    private final DirectRealProductMapper mapper;
    public record Entry(String sourceKey,DirectRealProductRow incoming,Long targetId,String baseline) {}
    public record Plan(Long projectId,List<Entry> entries) { public Plan { entries=List.copyOf(entries); } }
    public record Result(List<NativeEntityMigrationRow> rows,Map<String,String> baselines) {}
    public record DirectPreparation(Long projectId,String sourceProjectKey,List<ProjectRealProductMigrationApi.Source> rows,Actor actor) {}
    public record SourceExecution(Plan plan,Actor actor,boolean apply) {}
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Plan prepareSources(DirectPreparation request) {
        requireTransaction();
        if(request==null)throw invalid("INPUT_INVALID");
        var actor=request.actor();validate(actor);access.getProjectForManage(request.projectId(),actor);
        if(request.rows()==null || request.rows().size()>10000 || request.sourceProjectKey()==null)throw invalid("INPUT_INVALID");
        var entries=new ArrayList<Entry>();var keys=new HashSet<String>();var ids=new HashSet<Long>();var businessKeys=new HashSet<String>();
        for(var source:request.rows()) {
            var raw=source.originalFields();var upstream=source.upstreamFields();
            if(raw==null || !raw.keySet().equals(ORIGINAL_COLUMNS) || upstream==null || !upstream.keySet().equals(UPSTREAM_COLUMNS)
                    || source.sourceKey()==null || !source.sourceKey().matches("[1-9][0-9]*") || !keys.add(source.sourceKey())
                    || !source.sourceKey().equals(raw.get("id")) || !request.sourceProjectKey().equals(raw.get("projectId")))throw invalid("SOURCE_INVALID");
            if(source.upstreamMatchCount()!=1 || !SHARED_COLUMNS.stream().allMatch(field->Objects.equals(raw.get(field),upstream.get(field))))
                throw invalid("UPSTREAM_NOT_UNIQUE");
            var incoming=incoming(new ProductInput(request.projectId(),raw,upstream.get("dataSource"),actor));
            if(!businessKeys.add(business(incoming)))throw invalid("SOURCE_BUSINESS_DUPLICATE");
            if(source.targetId()==null?source.baseline()!=null:source.targetId()<=0 || source.baseline()==null || !ids.add(source.targetId()))
                throw invalid("MAPPING_INVALID");
            var entry=new Entry(source.sourceKey(),incoming,source.targetId(),source.baseline());
            validateCurrent(request.projectId(),entry,actor);entries.add(entry);
        }
        if(entries.stream().anyMatch(entry->entry.targetId()==null)
                && mapper.countAllProjectRecords(new MigrationProjectQuery(actor.tenantId(),request.projectId()))>0)
            throw invalid("CONTINUOUS_REFRESH_NOT_ADJUDICATED");
        return new Plan(request.projectId(),entries);
    }
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<ProjectRealProductMigrationApi.Result> sourceResults(SourceExecution request) {
        requireTransaction();validate(request.actor());
        if(request.apply())apply(request.plan(),request.actor());
        var result=new ArrayList<ProjectRealProductMigrationApi.Result>();
        for(var entry:request.plan().entries()) {
            var row=entry.targetId()==null?entry.incoming():validateCurrent(request.plan().projectId(),entry,request.actor());
            if(!request.apply() && entry.targetId()==null) {
                var now=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);row.setCreator(request.actor().actorId().toString());row.setUpdater(row.getCreator());
                row.setCreateTime(now);row.setUpdateTime(now);row.setDeleted(false);
            }
            var fields=new LinkedHashMap<String,Object>();var bean=new org.springframework.beans.BeanWrapperImpl(row);
            for(String name:List.of("id","tenantId","projectId","contractNo","projectCode","executionNo","productCategoryName","productName",
                    "productSubCode","productSubModel","productSubName","quantity","memo","dataSource","creator","createTime","updater","updateTime","deleted"))
                fields.put(name,bean.getPropertyValue(name));
            result.add(new ProjectRealProductMigrationApi.Result(entry.sourceKey(),row.getId(),entry.targetId()==null?"CREATED":"UNCHANGED",
                    Collections.unmodifiableMap(fields),row.getId()==null?null:baseline(row)));
        }
        return List.copyOf(result);
    }
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Result apply(Plan plan,Actor actor) {
        requireTransaction();validate(actor);access.getProjectForManage(plan.projectId(),actor);
        var outcomes=new ArrayList<NativeEntityMigrationRow>();Map<String,String> baselines=new LinkedHashMap<>();
        for(var entry:plan.entries()) {
            var row=validateCurrent(plan.projectId(),entry,actor);
            if(row==null) {
                row=entry.incoming();row.setId(IdWorker.getId());row.setDeleted(false);
                var now=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);row.setCreator(actor.actorId().toString());row.setUpdater(actor.actorId().toString());
                row.setCreateTime(now);row.setUpdateTime(now);
                if(mapper.insert(row)!=1)throw invalid("CREATE_FAILED");
                // Re-read the persisted audit precision; a handler or trigger must not silently alter approved fields.
                var persisted=mapper.selectRecordForUpdate(new RecordIdentityQuery(actor.tenantId(),plan.projectId(),row.getId()));
                if(persisted==null || !baseline(row).equals(baseline(persisted)))throw invalid("PERSISTED_FIELDS_CHANGED");
                row=persisted;
            }
            outcomes.add(NativeEntityMigrationRow.mapped("real-products",entry.sourceKey(),new ExternalTargetMapping(
                    "PROJ","ProjectRealProduct","proj_project_real_product",row.getId(),"PROJECT_ENTITY",0)));
            baselines.put(entry.sourceKey(),baseline(row));
        }
        return new Result(List.copyOf(outcomes),Map.copyOf(baselines));
    }
    private DirectRealProductRow validateCurrent(Long projectId,Entry entry,Actor actor) {
        if(!Objects.equals(entry.incoming().getTenantId(),actor.tenantId()) || !Objects.equals(entry.incoming().getProjectId(),projectId))throw invalid("PROJECT_INVALID");
        if(entry.targetId()==null) {
            if(!mapper.selectBusinessRecordsForUpdate(businessQuery(actor.tenantId(),projectId,entry.incoming())).isEmpty())
                throw invalid("UNBOUND_EXISTING_BUSINESS_RECORD");
            return null;
        }
        var row=mapper.selectRecordForUpdate(new RecordIdentityQuery(actor.tenantId(),projectId,entry.targetId()));
        if(row==null || !Objects.equals(row.getTenantId(),actor.tenantId()) || !Objects.equals(row.getProjectId(),projectId)
                || Boolean.TRUE.equals(row.getDeleted()) || !business(row).equals(business(entry.incoming()))
                || !baseline(row).equals(entry.baseline()))throw invalid("TARGET_CHANGED");
        return row;
    }
    private record ProductInput(Long projectId,Map<String,String> raw,String dataSource,Actor actor) {}
    private static DirectRealProductRow incoming(ProductInput input) {
        var raw=input.raw();
        if(List.of("contractNo","projectCode","orderExecNumber","productSubCode","productSubModel","productSubName")
                .stream().anyMatch(field->raw.get(field)==null))throw invalid("SOURCE_REQUIRED_FIELD_NULL");
        if(input.dataSource()!=null && input.dataSource().length()>25)throw invalid("DATA_SOURCE_TOO_LONG");
        var row=new DirectRealProductRow();row.setTenantId(input.actor().tenantId());row.setProjectId(input.projectId());
        row.setContractNo(raw.get("contractNo"));row.setProjectCode(raw.get("projectCode"));row.setExecutionNo(raw.get("orderExecNumber"));
        row.setProductCategoryName(raw.get("productFirstName"));row.setProductName(raw.get("productName"));
        row.setProductSubCode(raw.get("productSubCode"));row.setProductSubModel(raw.get("productSubModel"));row.setProductSubName(raw.get("productSubName"));
        row.setQuantity(raw.get("num")==null?null:new BigDecimal(new BigDecimal(raw.get("num")).intValueExact()));
        row.setMemo(raw.get("memo"));row.setDataSource(input.dataSource());
        if(row.getMemo()!=null && row.getMemo().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)throw invalid("MEMO_TOO_LONG");
        return row;
    }
    static String business(DirectRealProductRow row) {
        return JsonUtils.toJsonString(Arrays.asList(row.getTenantId(),row.getProjectId(),row.getContractNo(),row.getProjectCode(),row.getExecutionNo(),
                row.getProductCategoryName(),row.getProductName(),row.getProductSubCode(),row.getProductSubModel(),row.getProductSubName(),
                row.getQuantity()==null?null:row.getQuantity().stripTrailingZeros().toPlainString(),row.getMemo(),row.getDataSource()));
    }
    static String baseline(DirectRealProductRow row) {
        return JsonUtils.toJsonString(Arrays.asList(row.getId(),business(row),row.getCreator(),row.getCreateTime()==null?null:row.getCreateTime().toString(),
                row.getUpdater(),row.getUpdateTime()==null?null:row.getUpdateTime().toString(),row.getDeleted()));
    }
    static BusinessIdentityQuery businessQuery(Long tenantId,Long projectId,DirectRealProductRow row) {
        return BusinessIdentityQuery.builder().tenantId(tenantId).projectId(projectId).contractNo(row.getContractNo()).projectCode(row.getProjectCode()).executionNo(row.getExecutionNo()).productCategoryName(row.getProductCategoryName()).productName(row.getProductName()).productSubCode(row.getProductSubCode()).productSubModel(row.getProductSubModel()).productSubName(row.getProductSubName()).quantity(row.getQuantity()).memo(row.getMemo()).build();
    }
    private static void validate(Actor actor) {
        if(actor==null || actor.actorId()==null || actor.actorId()<=0 || !Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId()))throw invalid("ACTOR_INVALID");
    }
    private void requireTransaction() {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || !org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(dataSource))
            throw new IllegalStateException("DPPMS_REAL_PRODUCT_TRANSACTION_REQUIRED");
    }
    private static IllegalArgumentException invalid(String code) {return new IllegalArgumentException("DPPMS_REAL_PRODUCT_"+code);}
}
