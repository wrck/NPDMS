package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.ProjectProductConfigurationDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
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
public class ProjectProductConfigurationImportService {
    public static final Set<String> ORIGINAL_COLUMNS=Set.of("projectCode","orderExecNumber","itemGroup","itemCode","parentCode","quantity","bomPaths","itemModel","itemDesc","level","dataSource");
    private final ProjectManualCreationService access;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final ProjectProductConfigurationMapper mapper;
    public record Entry(String sourceKey,ProjectProductConfigurationDO incoming,Long targetId,String baseline) {}
    public record Plan(Long projectId,List<Entry> entries) { public Plan { entries=List.copyOf(entries); } }
    public record Result(List<NativeEntityMigrationRow> rows,Map<String,String> baselines) {}
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Plan prepare(Long projectId,String sourceProjectKey,List<ProjectLegacyRecordDO> headers,List<ProjectLegacyRecordDO> rows,ProjectAccessActor actor) {
        validate(actor);access.getProjectForManage(projectId,actor);
        if(rows==null || rows.size()>10000)throw invalid("INPUT_INVALID");
        if(rows.isEmpty())return new Plan(projectId,List.of());
        Long snapshotId=rows.getFirst().getSnapshotId();
        if(snapshotId==null || sourceProjectKey==null || !sourceProjectKey.matches("[1-9][0-9]*") || headers==null || headers.size()!=1)throw invalid("SOURCE_INVALID");
        var header=headers.getFirst();
        if(!Objects.equals(header.getTenantId(),actor.tenantId()) || !Objects.equals(header.getSnapshotId(),snapshotId)
                || !"project".equals(header.getDomainCode()) || !"pm_project_header".equals(header.getSourceTable())
                || !Objects.equals(header.getSourceKey(),sourceProjectKey))throw invalid("HEADER_INVALID");
        var headerValues=JsonUtils.parseObject(header.getPayloadJson(),new TypeReference<Map<String,String>>(){});
        if(headerValues==null || !sourceProjectKey.equals(headerValues.get("projectId"))
                || headerValues.get("projectCode")==null || headerValues.get("projectCode").isBlank())throw invalid("HEADER_INVALID");
        String originalCode=headerValues.get("projectCode");String queryCode=originalCode.split("-",2)[0];
        var statuses=evidence.statuses(actor.tenantId(),sourceProjectKey,snapshotId,rows);
        var entries=new ArrayList<Entry>();Set<String> keys=new HashSet<>();Set<Long> ids=new HashSet<>();
        for(var source:rows) {
            var raw=read(source,actor,snapshotId,"configured-products","pm_project_product_config_level_info_from_crm",ORIGINAL_COLUMNS);
            if(!keys.add(source.getSourceKey()) || !Objects.equals(raw.get("projectCode"),queryCode))throw invalid("SOURCE_PROJECT_INVALID");
            var incoming=new ProjectProductConfigurationDO();incoming.setTenantId(actor.tenantId());incoming.setProjectId(projectId);
            incoming.setProjectCode(raw.get("projectCode"));
            incoming.setExecutionNo(raw.get("orderExecNumber"));
            incoming.setItemGroup((raw.get("itemGroup")==null?null:new BigDecimal(raw.get("itemGroup")).intValueExact()));
            incoming.setProductCode(raw.get("itemCode"));
            incoming.setParentCode(raw.get("parentCode"));
            incoming.setQuantity((raw.get("quantity")==null?null:new BigDecimal(new BigDecimal(raw.get("quantity")).intValueExact())));
            incoming.setBomPath(raw.get("bomPaths"));
            incoming.setProductModel(raw.get("itemModel"));
            incoming.setProductDescription(raw.get("itemDesc"));
            incoming.setLevel((raw.get("level")==null?null:new BigDecimal(raw.get("level")).intValueExact()));
            incoming.setDataSource(raw.get("dataSource"));
            if(incoming.getDataSource()!=null && incoming.getDataSource().length()>25)throw invalid("DATA_SOURCE_TOO_LONG");
            if(incoming.getProjectCode()!=null && incoming.getProjectCode().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)throw invalid("TEXT_TOO_LONG");
            if(incoming.getExecutionNo()!=null && incoming.getExecutionNo().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)throw invalid("TEXT_TOO_LONG");
            if(incoming.getProductCode()!=null && incoming.getProductCode().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)throw invalid("TEXT_TOO_LONG");
            if(incoming.getParentCode()!=null && incoming.getParentCode().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)throw invalid("TEXT_TOO_LONG");
            if(incoming.getBomPath()!=null && incoming.getBomPath().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)throw invalid("TEXT_TOO_LONG");
            if(incoming.getProductModel()!=null && incoming.getProductModel().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)throw invalid("TEXT_TOO_LONG");
            if(incoming.getProductDescription()!=null && incoming.getProductDescription().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)throw invalid("TEXT_TOO_LONG");
            var status=statuses.get(source.getSourceKey());
            if(status==null || !Set.of("NOT_MIGRATED","MAPPED").contains(status.classification()))throw invalid("EVIDENCE_INVALID");
            Long id=null;String baseline=null;
            if("MAPPED".equals(status.classification())) {
                if(status.targets().size()!=1)throw invalid("MAPPING_INVALID");var target=status.targets().getFirst();
                if(!"PROJ".equals(target.targetContext()) || !"ProjectProductConfiguration".equals(target.targetObjectType())
                        || !"proj_project_product_configuration".equals(target.targetTable()) || !"PROJECT_ENTITY".equals(target.targetRole())
                        || target.targetSequence()!=0 || target.targetId()==null || target.targetId()<=0 || !ids.add(target.targetId())
                        || status.associatedListBaseline()==null)throw invalid("MAPPING_INVALID");
                id=target.targetId();baseline=status.associatedListBaseline();
            } else if(!status.targets().isEmpty() || status.associatedListBaseline()!=null)throw invalid("MAPPING_INVALID");
            var entry=new Entry(source.getSourceKey(),incoming,id,baseline);validateCurrent(projectId,entry,actor);entries.add(entry);
        }
        if(entries.stream().anyMatch(e->e.targetId()==null) && mapper.countAllProjectRecords(new ProjectProductConfigurationProjectQuery(actor.tenantId(),projectId))>0)
            throw invalid("CONTINUOUS_REFRESH_NOT_ADJUDICATED");
        return new Plan(projectId,entries);
    }
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Result apply(Plan plan,ProjectAccessActor actor) {
        validate(actor);access.getProjectForManage(plan.projectId(),actor);
        var outcomes=new ArrayList<NativeEntityMigrationRow>();Map<String,String> baselines=new LinkedHashMap<>();Set<Long> created=new HashSet<>();
        if(plan.entries().stream().anyMatch(e->e.targetId()==null) && mapper.countAllProjectRecords(new ProjectProductConfigurationProjectQuery(actor.tenantId(),plan.projectId()))>0)throw invalid("CONTINUOUS_REFRESH_NOT_ADJUDICATED");
        for(var entry:plan.entries()) {
            var row=validateCurrent(plan.projectId(),entry,actor,created);
            if(row==null) {
                row=entry.incoming();row.setId(IdWorker.getId());row.setDeleted(false);
                var now=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);row.setCreator(actor.actorId().toString());row.setUpdater(actor.actorId().toString());
                row.setCreateTime(now);row.setUpdateTime(now);
                if(mapper.insert(row)!=1)throw invalid("CREATE_FAILED");
                // Re-read the persisted audit precision; a handler or trigger must not silently alter approved fields.
                var persisted=mapper.selectRecordForUpdate(new ProjectProductConfigurationRecordQuery(actor.tenantId(),plan.projectId(),row.getId()));
                if(persisted==null || !baseline(row).equals(baseline(persisted)))throw invalid("PERSISTED_FIELDS_CHANGED");
                row=persisted;created.add(row.getId());
            }
            outcomes.add(NativeEntityMigrationRow.mapped("configured-products",entry.sourceKey(),new ExternalTargetMapping(
                    "PROJ","ProjectProductConfiguration","proj_project_product_configuration",row.getId(),"PROJECT_ENTITY",0)));
            baselines.put(entry.sourceKey(),baseline(row));
        }
        return new Result(List.copyOf(outcomes),Map.copyOf(baselines));
    }
    private ProjectProductConfigurationDO validateCurrent(Long projectId,Entry entry,ProjectAccessActor actor) {
        return validateCurrent(projectId,entry,actor,Set.of());
    }
    private ProjectProductConfigurationDO validateCurrent(Long projectId,Entry entry,ProjectAccessActor actor,Set<Long> created) {
        if(!Objects.equals(entry.incoming().getTenantId(),actor.tenantId()) || !Objects.equals(entry.incoming().getProjectId(),projectId))throw invalid("PROJECT_INVALID");
        if(entry.targetId()==null) {
            if(mapper.selectBusinessRecordsForUpdate(businessQuery(actor.tenantId(),projectId,entry.incoming())).stream().anyMatch(row->!created.contains(row.getId())))
                throw invalid("UNBOUND_EXISTING_BUSINESS_RECORD");
            return null;
        }
        var row=mapper.selectRecordForUpdate(new ProjectProductConfigurationRecordQuery(actor.tenantId(),projectId,entry.targetId()));
        if(row==null || !Objects.equals(row.getTenantId(),actor.tenantId()) || !Objects.equals(row.getProjectId(),projectId)
                || Boolean.TRUE.equals(row.getDeleted()) || !business(row).equals(business(entry.incoming()))
                || !baseline(row).equals(entry.baseline()))throw invalid("TARGET_CHANGED");
        return row;
    }
    private static Map<String,String> read(ProjectLegacyRecordDO row,ProjectAccessActor actor,Long snapshotId,String domain,String table,Set<String> columns) {
        if(row==null || !Objects.equals(row.getTenantId(),actor.tenantId()) || !Objects.equals(row.getSnapshotId(),snapshotId)
                || !domain.equals(row.getDomainCode()) || !table.equals(row.getSourceTable()) || row.getSourceKey()==null || row.getSourceKey().isBlank()
                || row.getRedactedFieldsJson()!=null && !JsonUtils.parseArray(row.getRedactedFieldsJson(),String.class).isEmpty())throw invalid("SOURCE_INVALID");
        var raw=JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){});
        if(raw==null || !raw.keySet().equals(columns))throw invalid("FIELD_COVERAGE_INVALID");return raw;
    }
    static String business(ProjectProductConfigurationDO row) {
        return JsonUtils.toJsonString(Arrays.asList(row.getTenantId(),row.getProjectId(),row.getProjectCode(),row.getExecutionNo(),row.getItemGroup(),row.getProductCode(),row.getParentCode(),(row.getQuantity()==null?null:row.getQuantity().stripTrailingZeros().toPlainString()),row.getBomPath(),row.getProductModel(),row.getProductDescription(),row.getLevel(),row.getDataSource()));
    }
    static String baseline(ProjectProductConfigurationDO row) {
        return JsonUtils.toJsonString(Arrays.asList(row.getId(),business(row),row.getCreator(),row.getCreateTime()==null?null:row.getCreateTime().toString(),
                row.getUpdater(),row.getUpdateTime()==null?null:row.getUpdateTime().toString(),row.getDeleted()));
    }
    static ProjectProductConfigurationBusinessQuery businessQuery(Long tenantId,Long projectId,ProjectProductConfigurationDO row) {
        return ProjectProductConfigurationBusinessQuery.builder().tenantId(tenantId).projectId(projectId).projectCode(row.getProjectCode()).executionNo(row.getExecutionNo()).itemGroup(row.getItemGroup()).productCode(row.getProductCode()).parentCode(row.getParentCode()).quantity(row.getQuantity()).bomPath(row.getBomPath()).productModel(row.getProductModel()).productDescription(row.getProductDescription()).level(row.getLevel()).dataSource(row.getDataSource()).build();
    }
    private static void validate(ProjectAccessActor actor) {
        if(actor==null || actor.actorId()==null || actor.actorId()<=0 || !Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId()))throw invalid("ACTOR_INVALID");
    }
    private static IllegalArgumentException invalid(String code) {return new IllegalArgumentException("DPPMS_PRODUCT_CONFIGURATION_"+code);}
}
