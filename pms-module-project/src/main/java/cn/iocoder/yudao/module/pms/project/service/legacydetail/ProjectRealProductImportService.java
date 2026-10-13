package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.ProjectRealProductDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectRealProductMigrationApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Formal additional project list; technical identity and full replay baselines remain with PLT. */
@Service @RequiredArgsConstructor
public class ProjectRealProductImportService {
    public static final List<String> SHARED_COLUMNS=List.of("projectCode","orderExecNumber","productFirstName","productName",
            "productSubCode","productSubModel","productSubName","num","memo");
    public static final Set<String> ORIGINAL_COLUMNS=Set.of("id","projectId","contractNo","projectCode","orderExecNumber",
            "productFirstName","productName","productSubCode","productSubModel","productSubName","num","memo");
    public static final Set<String> UPSTREAM_COLUMNS=Set.of("projectCode","orderExecNumber","productFirstName","productName",
            "productSubCode","productSubModel","productSubName","num","memo","dataSource");
    private final ProjectManualCreationService access;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final ProjectRealProductMapper mapper;
    public record Entry(String sourceKey,ProjectRealProductDO incoming,Long targetId,String baseline) {}
    public record Plan(Long projectId,List<Entry> entries) { public Plan { entries=List.copyOf(entries); } }
    public record Result(List<NativeEntityMigrationRow> rows,Map<String,String> baselines) {}
    public record DirectPreparation(Long projectId,String sourceProjectKey,List<ProjectRealProductMigrationApi.Source> rows,ProjectAccessActor actor) {}
    public record SourceExecution(Plan plan,ProjectAccessActor actor,boolean apply) {}
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Plan prepareSources(DirectPreparation request) {
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
        validate(request.actor());
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
    public Plan prepare(Long projectId,String sourceProjectKey,List<ProjectLegacyRecordDO> rows,List<ProjectLegacyRecordDO> upstream,ProjectAccessActor actor) {
        validate(actor);access.getProjectForManage(projectId,actor);
        if(rows==null || upstream==null || rows.size()>10000 || upstream.size()>10000)
            throw invalid("INPUT_INVALID");
        if(rows.isEmpty()) { if(!upstream.isEmpty())throw invalid("UPSTREAM_UNRELATED");return new Plan(projectId,List.of()); }
        Long snapshotId=rows.getFirst().getSnapshotId();
        if(snapshotId==null || sourceProjectKey==null || !sourceProjectKey.matches("[1-9][0-9]*"))throw invalid("SOURCE_INVALID");
        var originals=new LinkedHashMap<String,Map<String,String>>();
        for(var row:rows) {
            var raw=read(row,actor,snapshotId,"real-products","pm_project_product_line_real",ORIGINAL_COLUMNS);
            if(!Objects.equals(raw.get("id"),row.getSourceKey()) || !row.getSourceKey().matches("[1-9][0-9]*")
                    || !Objects.equals(raw.get("projectId"),sourceProjectKey) || originals.putIfAbsent(row.getSourceKey(),raw)!=null)
                throw invalid("SOURCE_INVALID");
            if(List.of("contractNo","projectCode","orderExecNumber","productSubCode","productSubModel","productSubName")
                    .stream().anyMatch(field->raw.get(field)==null))throw invalid("SOURCE_REQUIRED_FIELD_NULL");
        }
        var companions=new LinkedHashMap<String,Map<String,String>>();
        for(var row:upstream) {
            var raw=read(row,actor,snapshotId,"real-product-upstream","pm_project_real_product_line_from_sms",UPSTREAM_COLUMNS);
            if(companions.putIfAbsent(row.getSourceKey(),raw)!=null)throw invalid("UPSTREAM_INVALID");
        }
        var statuses=evidence.statuses(actor.tenantId(),sourceProjectKey,snapshotId,rows);
        var entries=new ArrayList<Entry>();Set<Long> ids=new HashSet<>();Set<String> used=new HashSet<>();Set<String> businessKeys=new HashSet<>();
        for(var original:originals.entrySet()) {
            String key=original.getKey();var raw=original.getValue();
            var matches=companions.entrySet().stream().filter(c->SHARED_COLUMNS.stream()
                    .allMatch(field->Objects.equals(raw.get(field),c.getValue().get(field)))).toList();
            if(matches.size()!=1)throw invalid("UPSTREAM_NOT_UNIQUE");used.add(matches.getFirst().getKey());
            String dataSource=matches.getFirst().getValue().get("dataSource");
            if(dataSource!=null && dataSource.length()>25)throw invalid("DATA_SOURCE_TOO_LONG");
            var incoming=incoming(new ProductInput(projectId,raw,dataSource,actor));
            if(!businessKeys.add(business(incoming)))throw invalid("SOURCE_BUSINESS_DUPLICATE");
            var status=statuses.get(key);
            if(status==null || !Set.of("NOT_MIGRATED","MAPPED").contains(status.classification()))throw invalid("EVIDENCE_INVALID");
            Long id=null;String baseline=null;
            if("MAPPED".equals(status.classification())) {
                if(status.targets().size()!=1)throw invalid("MAPPING_INVALID");var target=status.targets().getFirst();
                if(!"PROJ".equals(target.targetContext()) || !"ProjectRealProduct".equals(target.targetObjectType())
                        || !"proj_project_real_product".equals(target.targetTable()) || !"PROJECT_ENTITY".equals(target.targetRole())
                        || target.targetSequence()!=0 || target.targetId()==null || target.targetId()<=0 || !ids.add(target.targetId())
                        || status.realProductBaseline()==null)throw invalid("MAPPING_INVALID");
                id=target.targetId();baseline=status.realProductBaseline();
            } else if(!status.targets().isEmpty() || status.realProductBaseline()!=null)throw invalid("MAPPING_INVALID");
            var entry=new Entry(key,incoming,id,baseline);validateCurrent(projectId,entry,actor);entries.add(entry);
        }
        if(!used.equals(companions.keySet()))throw invalid("UPSTREAM_UNRELATED");
        // A rebuilt legacy ID and changed quantity cannot establish a new identity by themselves.
        // Once this project's first list is carried, only its proven bindings can replay.
        if(entries.stream().anyMatch(e->e.targetId()==null)
                && mapper.countAllProjectRecords(new MigrationProjectQuery(actor.tenantId(),projectId))>0)
            throw invalid("CONTINUOUS_REFRESH_NOT_ADJUDICATED");
        return new Plan(projectId,entries);
    }
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Result apply(Plan plan,ProjectAccessActor actor) {
        validate(actor);access.getProjectForManage(plan.projectId(),actor);
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
    private ProjectRealProductDO validateCurrent(Long projectId,Entry entry,ProjectAccessActor actor) {
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
    private record ProductInput(Long projectId,Map<String,String> raw,String dataSource,ProjectAccessActor actor) {}
    private static ProjectRealProductDO incoming(ProductInput input) {
        var raw=input.raw();
        if(List.of("contractNo","projectCode","orderExecNumber","productSubCode","productSubModel","productSubName")
                .stream().anyMatch(field->raw.get(field)==null))throw invalid("SOURCE_REQUIRED_FIELD_NULL");
        if(input.dataSource()!=null && input.dataSource().length()>25)throw invalid("DATA_SOURCE_TOO_LONG");
        var row=new ProjectRealProductDO();row.setTenantId(input.actor().tenantId());row.setProjectId(input.projectId());
        row.setContractNo(raw.get("contractNo"));row.setProjectCode(raw.get("projectCode"));row.setExecutionNo(raw.get("orderExecNumber"));
        row.setProductCategoryName(raw.get("productFirstName"));row.setProductName(raw.get("productName"));
        row.setProductSubCode(raw.get("productSubCode"));row.setProductSubModel(raw.get("productSubModel"));row.setProductSubName(raw.get("productSubName"));
        row.setQuantity(raw.get("num")==null?null:new BigDecimal(new BigDecimal(raw.get("num")).intValueExact()));
        row.setMemo(raw.get("memo"));row.setDataSource(input.dataSource());
        if(row.getMemo()!=null && row.getMemo().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)throw invalid("MEMO_TOO_LONG");
        return row;
    }
    private static Map<String,String> read(ProjectLegacyRecordDO row,ProjectAccessActor actor,Long snapshotId,String domain,String table,Set<String> columns) {
        if(row==null || !Objects.equals(row.getTenantId(),actor.tenantId()) || !Objects.equals(row.getSnapshotId(),snapshotId)
                || !domain.equals(row.getDomainCode()) || !table.equals(row.getSourceTable()) || row.getSourceKey()==null || row.getSourceKey().isBlank()
                || row.getRedactedFieldsJson()!=null && !JsonUtils.parseArray(row.getRedactedFieldsJson(),String.class).isEmpty())throw invalid("SOURCE_INVALID");
        var raw=JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){});
        if(raw==null || !raw.keySet().equals(columns))throw invalid("FIELD_COVERAGE_INVALID");return raw;
    }
    static String business(ProjectRealProductDO row) {
        return JsonUtils.toJsonString(Arrays.asList(row.getTenantId(),row.getProjectId(),row.getContractNo(),row.getProjectCode(),row.getExecutionNo(),
                row.getProductCategoryName(),row.getProductName(),row.getProductSubCode(),row.getProductSubModel(),row.getProductSubName(),
                row.getQuantity()==null?null:row.getQuantity().stripTrailingZeros().toPlainString(),row.getMemo(),row.getDataSource()));
    }
    static String baseline(ProjectRealProductDO row) {
        return JsonUtils.toJsonString(Arrays.asList(row.getId(),business(row),row.getCreator(),row.getCreateTime()==null?null:row.getCreateTime().toString(),
                row.getUpdater(),row.getUpdateTime()==null?null:row.getUpdateTime().toString(),row.getDeleted()));
    }
    static BusinessIdentityQuery businessQuery(Long tenantId,Long projectId,ProjectRealProductDO row) {
        return BusinessIdentityQuery.builder().tenantId(tenantId).projectId(projectId).contractNo(row.getContractNo()).projectCode(row.getProjectCode()).executionNo(row.getExecutionNo()).productCategoryName(row.getProductCategoryName()).productName(row.getProductName()).productSubCode(row.getProductSubCode()).productSubModel(row.getProductSubModel()).productSubName(row.getProductSubName()).quantity(row.getQuantity()).memo(row.getMemo()).build();
    }
    private static void validate(ProjectAccessActor actor) {
        if(actor==null || actor.actorId()==null || actor.actorId()<=0 || !Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId()))throw invalid("ACTOR_INVALID");
    }
    private static IllegalArgumentException invalid(String code) {return new IllegalArgumentException("DPPMS_REAL_PRODUCT_"+code);}
}
