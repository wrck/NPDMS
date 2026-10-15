package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsWarrantySourceRecordsApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;
import static cn.iocoder.yudao.module.pms.platform.support.migration.NativeFieldEnrichment.*;
@Service
@RequiredArgsConstructor
public class ProjectAssociatedDataService {
    private final ProjectManualCreationService access;
    private final ProjectMasterMapper projects;
    private final DppmsCommerceDetailImportApi commerce;
    private final ProjectSourceAttributesService sourceAttributes;
    private final DppmsAssetSourceHistoryApi sourceHistory;
    private final DppmsWarrantySourceRecordsApi warrantySources;
    private final DppmsInstallationSourceQueryApi installationSources;
    private final ProjectProductScopeImportService productImporter;
    private final cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi productScopes;
    private final ProjectRealProductMapper real;
    private final ProjectRealProductImportService realProducts;
    private final ProjectLeaseProductMapper lease;
    private final ProjectLeaseProductImportService leaseProducts;
    private final ProjectProductConfigurationMapper configurations;
    private final ProjectProductConfigurationImportService productConfigurations;
    public record Row(Long id,String sourceKey,Map<String,Object> values) {}
    public record ImportResult(List<NativeEntityMigrationRow> rows,Map<String,DppmsProjectProductScopeApi.Baseline> productBaselines,Map<String,String> realBaselines,Map<String,String> leaseBaselines,Map<String,String> configurationBaselines) {
        public ImportResult(List<NativeEntityMigrationRow> rows,Map<String,DppmsProjectProductScopeApi.Baseline> productBaselines,Map<String,String> realBaselines) {this(rows,productBaselines,realBaselines,Map.of(),Map.of());}
        public ImportResult(List<NativeEntityMigrationRow> rows,Map<String,DppmsProjectProductScopeApi.Baseline> productBaselines) {this(rows,productBaselines,Map.of());}
    }
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public ImportResult importRecords(Long projectId,Map<String,List<ProjectLegacyRecordDO>> records,ProjectAccessActor actor) {
        validate(actor); access.getProjectForManage(projectId,actor);
        var result=new ArrayList<NativeEntityMigrationRow>();
        Map<String,DppmsProjectProductScopeApi.Baseline> baselines=new LinkedHashMap<>();
        for (var source:records.get("project")) result.add(sourceAttributes.importRecord(projectId,source,actor));
        var productRows=records.get("products");
        if(!productRows.isEmpty()) {
            String sourceProjectKey=value(productRows.getFirst(),"projectId");
            var command=productImporter.command(projectId,sourceProjectKey,productRows,actor,false);
            for(var scope:productScopes.apply(command)) {
                if(scope.targetId()==null || !Set.of("CREATED","UNCHANGED").contains(scope.action()))
                    throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_OWNER_INVALID");
                result.add(NativeEntityMigrationRow.mapped("products",scope.sourceKey(),new ExternalTargetMapping(
                        "COM","DeliveryScope","com_delivery_scope",scope.targetId(),"BUSINESS_ENTITY",0)));
                var fields=scope.fields();
                var baseline=new DppmsProjectProductScopeApi.Baseline(scope.targetId(),(String)fields.get("creator"),
                        (java.time.LocalDateTime)fields.get("createTime"),(String)fields.get("updater"),(java.time.LocalDateTime)fields.get("updateTime"));
                if(baseline.createTime()==null || baseline.updateTime()==null || baselines.put(scope.sourceKey(),baseline)!=null)
                    throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_AUDIT_EVIDENCE_INVALID");
            }
            if(baselines.size()!=productRows.size() || !baselines.keySet().equals(productRows.stream().map(ProjectLegacyRecordDO::getSourceKey).collect(java.util.stream.Collectors.toSet())))
                throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_COVERAGE_INVALID");
        }
        var realRows=records.get("real-products");
        Map<String,String> realBaselines=Map.of();
        if(!realRows.isEmpty()) {
            String sourceProjectKey=value(realRows.getFirst(),"projectId");
            var plan=realProducts.prepare(projectId,sourceProjectKey,realRows,records.getOrDefault("real-product-upstream",List.of()),actor);
            var imported=realProducts.apply(plan,actor);result.addAll(imported.rows());realBaselines=imported.baselines();
            result.addAll(ProjectRealProductReconciliationService.upstreamOutcomes(plan,records.getOrDefault("real-product-upstream",List.of()),imported));
        }
        Map<String,String> leaseBaselines=Map.of(),configurationBaselines=Map.of();
        if(!records.get("lease-products").isEmpty()) {
            var headers=records.get("project");if(headers.size()!=1)throw new IllegalArgumentException("DPPMS_LEASE_PRODUCT_HEADER_INVALID");
            var plan=leaseProducts.prepare(projectId,headers.getFirst().getSourceKey(),headers,records.get("lease-products"),actor);
            var imported=leaseProducts.apply(plan,actor);result.addAll(imported.rows());leaseBaselines=imported.baselines();
        }
        if(!records.get("configured-products").isEmpty()) {
            var headers=records.get("project");if(headers.size()!=1)throw new IllegalArgumentException("DPPMS_PRODUCT_CONFIGURATION_HEADER_INVALID");
            var plan=productConfigurations.prepare(projectId,headers.getFirst().getSourceKey(),headers,records.get("configured-products"),actor);
            var imported=productConfigurations.apply(plan,actor);result.addAll(imported.rows());configurationBaselines=imported.baselines();
        }
        return new ImportResult(List.copyOf(result),Map.copyOf(baselines),realBaselines,leaseBaselines,configurationBaselines);
    }
    public PageResult<Row> page(Long projectId,String kind,int pageNo,int pageSize,ProjectAccessActor actor) {
        validate(actor);var project=access.getProject(projectId,actor);
        if (pageNo<1||pageSize<1||pageSize>100) throw new IllegalArgumentException("DPPMS_NATIVE_PAGE_INVALID");
        var query=new AssociatedPageQuery(actor.tenantId(),projectId,Math.multiplyExact(pageNo-1,pageSize),pageSize);
        String sourceCode=project.getProjectCode()==null?null:project.getProjectCode().split("-",2)[0];
        var cacheQuery=new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(
                actor.tenantId(),sourceCode,query.offset(),query.limit());
        return switch(kind) {
            case "project" -> {
                var root=projects.selectById(projectId);
                if (root==null||!Objects.equals(root.getTenantId(),actor.tenantId())) throw new IllegalArgumentException("DPPMS_NATIVE_PROJECT_FORBIDDEN");
                yield new PageResult<>(List.of(new Row(root.getId(),sourceAttributes.readSourceKey(projectId,actor),
                        sourceAttributes.readAttributes(projectId,actor))),1L);
            }
            case "warranty-services","warranty-registrations","warranty-changes" -> {
                var page=warrantySources.page(new DppmsWarrantySourceRecordsApi.Query(actor.tenantId(),actor.actorId(),projectId,kind,pageNo,pageSize));
                yield new PageResult<>(page.list().stream().map(r->new Row(r.id(),r.sourceKey(),r.values())).toList(),page.total());
            }
            case "installations" -> {
                var page=installationSources.pageSourceRecords(new DppmsInstallationSourceQueryApi.Query(actor.tenantId(),actor.actorId(),projectId,null,pageNo,pageSize));
                yield new PageResult<>(page.getList().stream().map(r-> {
                    Map<String,Object> fields=new LinkedHashMap<>();
                    fields.put("sourceId",r.sourceId());fields.put("sourceProjectId",r.sourceProjectId());
                    fields.put("sourceDeviceSn",r.sourceDeviceSn());fields.put("installLocation",r.installLocation());
                    if(r.values()!=null)fields.putAll(row(r.values(),r.id(),r.sourceRecordKey()).values());
                    fields.put("addressId",r.addressId());fields.put("addressVersion",r.addressVersion());
                    fields.put("locationResolutionStatus",r.locationResolutionStatus());
                    return new Row(r.id(),r.sourceRecordKey(),fields);
                }).toList(),page.getTotal());
            }
            case "factory-versions" -> {
                var page=sourceHistory.pageFactories(new DppmsAssetSourceHistoryApi.Query(actor.tenantId(),actor.actorId(),projectId,null,pageNo,pageSize));
                yield new PageResult<>(page.getList().stream().map(r->row(r.values(),r.id(),String.valueOf(r.id()))).toList(),page.getTotal());
            }
            case "software-versions" -> {
                var page=sourceHistory.pageObservations(new DppmsAssetSourceHistoryApi.Query(actor.tenantId(),actor.actorId(),projectId,null,pageNo,pageSize));
                yield new PageResult<>(page.getList().stream().map(r->row(r.values(),r.id(),String.valueOf(r.id()))).toList(),page.getTotal());
            }
            case "software-changes" -> {
                var page=sourceHistory.pageBatches(new DppmsAssetSourceHistoryApi.Query(actor.tenantId(),actor.actorId(),projectId,null,pageNo,pageSize));
                yield new PageResult<>(page.getList().stream().map(r->row(r.values(),r.id(),String.valueOf(r.id()))).toList(),page.getTotal());
            }
            case "products" -> {
                var page=productScopes.page(new cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.Query(actor.tenantId(),actor.actorId(),projectId,pageNo,pageSize));
                yield new PageResult<>(page.list().stream().map(r->new Row(r.targetId(),r.sourceKey(),r.fields())).toList(),page.total());
            }
            case "real-products" -> new PageResult<>(real.selectProjectPage(query).stream().map(r->row(r,r.getId(),String.valueOf(r.getId()))).toList(),real.countProject(query));
            case "lease-products" -> new PageResult<>(lease.selectProjectPage(cacheQuery).stream().map(r->row(r,r.getId(),String.valueOf(r.getId()))).toList(),lease.countProject(cacheQuery));
            case "configured-products" -> configurationPage(cacheQuery);
            case "plans","shipment-packages","orders","order-lines","executions" -> {
                var page=commerce.page(new DppmsCommerceDetailImportApi.Query(actor.tenantId(),actor.actorId(),projectId,kind,pageNo,pageSize));
                yield new PageResult<>(page.list().stream().map(r->new Row(r.id(),r.sourceKey(),r.values())).toList(),page.total());
            }
            default -> throw new IllegalArgumentException("DPPMS_NATIVE_KIND_INVALID");
        };
    }
    private PageResult<Row> configurationPage(cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery query) {
        var records=configurations.selectProjectPage(query);
        var paths=records.stream().filter(r->r.getLevel()!=null && r.getLevel()>1 && r.getBomPath()!=null)
                .map(r->configurationPath(r,parentBomPath(r.getBomPath(),r.getLevel()))).distinct().toList();
        var parents=paths.isEmpty()?Set.<cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectConfigurationPathQuery.PathIdentity>of()
                :configurations.selectPathNodes(new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectConfigurationPathQuery(query.tenantId(),query.sourceProjectCode(),paths))
                    .stream().map(r->configurationPath(r,r.getBomPath())).collect(java.util.stream.Collectors.toSet());
        var page=records.stream().map(r->{
            var record=row(r,r.getId(),String.valueOf(r.getId()));var fields=new LinkedHashMap<>(record.values());
            String parent=parentBomPath(r.getBomPath(),r.getLevel());
            fields.put("parentBomPaths",parent);fields.put("parentGroupPaths",r.getItemGroup()==null || parent==null?null:r.getItemGroup()+"_"+parent);
            fields.put("groupPaths",r.getItemGroup()==null || r.getBomPath()==null?null:r.getItemGroup()+"_"+r.getBomPath());
            fields.put("parentPathMissing",r.getLevel()!=null && r.getLevel()>1 && parent!=null && !parents.contains(configurationPath(r,parent)));
            return new Row(record.id(),record.sourceKey(),fields);
        }).toList();
        return new PageResult<>(page,configurations.countProject(query));
    }
    static String parentBomPath(String path,Integer level) {
        if(path==null || level==null)return null;
        long count=(long)level-1;if(count==0)return "";
        var parts=path.split(",",-1);int size=(int)Math.min(Math.abs(count),parts.length);
        return String.join(",",count>0?Arrays.copyOfRange(parts,0,size):Arrays.copyOfRange(parts,parts.length-size,parts.length));
    }
    private static cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectConfigurationPathQuery.PathIdentity configurationPath(ProjectProductConfigurationDO row,String path) {
        return new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectConfigurationPathQuery.PathIdentity(row.getProjectCode(),row.getExecutionNo(),row.getItemGroup(),path);
    }
    private static Row row(Object data,Long id,String key) {
        Map<String,Object> values=JsonUtils.parseObject(JsonUtils.toJsonString(data),new TypeReference<Map<String,Object>>(){});
        return new Row(id,key,values);
    }
    private static void validate(ProjectAccessActor actor) {
        if (actor==null||actor.actorId()==null||actor.actorId()<=0||!Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("DPPMS_NATIVE_ACTOR_INVALID");
    }
}
