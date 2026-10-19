package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import static cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.*;

/** Formal historical project creation and protected baseline initialization; no current workflow completion. */
@RequiredArgsConstructor
final class DirectProjectPersistence {
    private final javax.sql.DataSource dataSource;
    private final DirectProjectMapper projects;
    private final DirectProjectEvidenceMapper sources;
    private final DirectProjectAccess access;
    private final CompanyApi companies;
    private final DeptApi departments;
    private final AdminUserApi users;
    private final SystemUserMigrationApi historicalUsers;
    private final SystemDepartmentMigrationApi historicalDepartments;
    private final DirectProjectCreationAuthorization creationAuthorization;
    private final OrganizationScopeApi organization;
    private final DirectProjectRootProjection treeProjection;
    private static final Set<String> DATES = Set.of("column009", "createTime", "updateTime", "effectiveFrom",
            "effectiveTo", "projectStartTime", "projectRefreshTime", "projectCloseTime");
    private static final JsonMapper ORIGINAL_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    public List<Result> preview(Request request) { return execute(request, false); }

    public List<Result> apply(Request request) { return execute(request, true); }

    public List<Facts> inspectBindings(IdentityInspection request) {
        requireTransaction();
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(request==null || !Objects.equals(request.tenantId(),tenant) || request.actorId()==null || request.actorId()<=0
                || request.projects()==null || request.projects().size()>500)
            throw new IllegalArgumentException("DPPMS_PROJECT_BINDING_INSPECTION_INVALID");
        var ids=new HashSet<Long>();
        for(var expected:request.projects()) {
            if(expected==null || expected.projectId()==null || expected.projectId()<=0 || expected.projectType()==null
                    || expected.projectCode()==null || !ids.add(expected.projectId()))
                throw new IllegalArgumentException("DPPMS_PROJECT_BINDING_INSPECTION_INVALID");
        }
        var current=new ArrayList<Facts>();
        for(var expected:request.projects().stream().sorted(Comparator.comparing(IdentityTarget::projectId)).toList()) {
            access.getProjectForManage(expected.projectId(),new DirectProjectAccess.Actor(tenant,request.actorId()));
            var row=projects.selectIdentity(new DirectProjectMapper.IdentityQuery(tenant,expected.projectId()));
            if(row==null || Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(),tenant)
                    || !Objects.equals(row.getProjectType(),expected.projectType()) || !Objects.equals(row.getProjectCode(),expected.projectCode()))
                throw new IllegalArgumentException("DPPMS_PROJECT_IDENTITY_TARGET_CHANGED");
            current.add(snapshot(row));
        }
        return List.copyOf(current);
    }

    public void verifyBindings(BindingVerification request) { verifyBindings(request,false); }

    public void lockBindings(BindingVerification request) { verifyBindings(request,true); }

    private void verifyBindings(BindingVerification request,boolean lock) {
        requireTransaction();
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(request==null || !Objects.equals(request.tenantId(),tenant) || request.actorId()==null || request.actorId()<=0
                || request.projects()==null || request.projects().size()>500)
            throw new IllegalArgumentException("DPPMS_PROJECT_BINDING_VERIFICATION_INVALID");
        var ids=new HashSet<Long>();
        for(var expected:request.projects()) {
            if(expected==null || expected.id()==null || expected.id()<=0 || !Objects.equals(expected.tenantId(),tenant)
                    || !ids.add(expected.id())) throw new IllegalArgumentException("DPPMS_PROJECT_BINDING_VERIFICATION_INVALID");
        }
        for(var expected:request.projects().stream().sorted(Comparator.comparing(Facts::id)).toList()) {
            access.getProjectForManage(expected.id(),new DirectProjectAccess.Actor(tenant,request.actorId()));
            var query=new DirectProjectMapper.IdentityQuery(tenant,expected.id());
            var current=lock?projects.selectIdentityForUpdate(query):projects.selectIdentity(query);
            if(current==null || Boolean.TRUE.equals(current.getDeleted()) || !Objects.equals(current.getTenantId(),tenant)
                    || !Objects.equals(expected,snapshot(current)))
                throw new IllegalArgumentException("DPPMS_PROJECT_IDENTITY_TARGET_CHANGED");
        }
    }

    private List<Result> execute(Request request, boolean apply) {
        requireTransaction();
        long tenant = TenantContextHolder.getRequiredTenantId();
        if (request == null || !Objects.equals(request.tenantId(), tenant) || request.actorId() == null
                || request.actorId() <= 0 || request.owner() == null
                || !request.owner().matches("integration:"+tenant+":[0-9]+") || request.entries() == null || request.entries().size()>500)
            throw new IllegalArgumentException("DPPMS_PROJECT_MIGRATION_SCOPE_INVALID");
        users.validateUser(request.actorId());
        Set<String> sourceKeys = new HashSet<>();
        for (var entry : request.entries()) {
            if (entry == null || entry.sourceKey() == null || !entry.sourceKey().matches("[1-9][0-9]*")
                    || !sourceKeys.add(entry.sourceKey()) || entry.originalFields() == null
                    || entry.originalFields().size() != ORIGINAL_COLUMNS.size()
                    || !entry.originalFields().keySet().containsAll(ORIGINAL_COLUMNS)
                    || !Objects.equals(entry.sourceKey(), entry.originalFields().get("projectId")))
                throw new IllegalArgumentException("DPPMS_PROJECT_ORIGINAL_HEADER_INVALID");
        }
        var ordered = request.entries().stream().sorted(Comparator.comparing(Entry::sourceKey)).toList();
        Set<Long> targetIds = new HashSet<>();
        List<SystemDepartmentMigrationApi.Department> departmentCatalogue=null;
        List<Result> results = new ArrayList<>();
        for (var entry : ordered) {
            var sourceQuery = new DirectProjectEvidenceMapper.SourceIdentityQuery(tenant, entry.sourceKey());
            var source = apply ? sources.selectSourceForUpdate(sourceQuery) : sources.selectSource(sourceQuery);
            boolean restoring=entry.restoration()!=null;
            boolean creating=!restoring && source==null && entry.targetId()==null && entry.lastApplied()==null;
            if (!creating && source!=null && (!Objects.equals(source.getTenantId(), tenant) || source.getProjectId() == null
                    || entry.targetId() != null && !Objects.equals(entry.targetId(), source.getProjectId())
                    ))
                throw new IllegalArgumentException("DPPMS_PROJECT_SOURCE_IDENTITY_UNRESOLVED");
            Long target=source==null?entry.targetId():source.getProjectId();
            if (!creating && (target==null || !targetIds.add(target))) throw new IllegalArgumentException("DPPMS_PROJECT_SOURCE_IDENTITY_UNRESOLVED");
            if (restoring) {
                creationAuthorization.assertCanImportAcrossOrganizations(request.actorId());
                if (entry.targetId()==null || entry.lastApplied()==null || request.adoptExisting()
                        || entry.restoration().expectedFingerprint()==null
                        || !entry.restoration().expectedFingerprint().matches("[a-f0-9]{64}"))
                    throw new IllegalArgumentException("DPPMS_PROJECT_RESTORATION_PROOF_INVALID");
            } else if (creating) {
                if (request.adoptExisting() || entry.expectedProjectVersion()!=null)
                    throw new IllegalArgumentException("DPPMS_PROJECT_NEW_SOURCE_CANNOT_ADOPT_EXISTING");
                creationAuthorization.assertCanCreate(request.actorId());
            } else access.getProjectForManage(target, new DirectProjectAccess.Actor(tenant, request.actorId()));
            var identity = new DirectProjectMapper.IdentityQuery(tenant,target);
            var row = creating?new DirectProjectRow():restoring?projects.selectRestorationIdentityForUpdate(identity)
                    :apply ? projects.selectIdentityForUpdate(identity) : projects.selectIdentity(identity);
            if (creating) {row.setTenantId(tenant);row.setVersion(0L);row.setDeleted(false);row.setLifecycleStatus("ACTIVE");row.setCurrentStage("S0");}
            if (row == null || !Objects.equals(row.getTenantId(), tenant) || !restoring && Boolean.TRUE.equals(row.getDeleted()))
                throw new IllegalArgumentException("DPPMS_PROJECT_TARGET_PROTECTED");
            Facts before = creating?null:snapshot(row);
            if (restoring) {
                if (!Boolean.TRUE.equals(row.getDeleted()) || !"MIGRATION".equals(row.getSourceType())
                        || row.getVersion()==null || entry.lastApplied().version()==null
                        || row.getVersion()!=entry.lastApplied().version()+1
                        || !Objects.equals(before,withVersion(entry.lastApplied(),row.getVersion()))
                        || !Objects.equals(entry.restoration().expectedFingerprint(),projects.selectRestorationFingerprint(identity))
                        || projects.selectRestorationReferenceCount(identity)!=0) {
                    results.add(issue(entry,before,"撤回项目或其业务关联已变化，禁止恢复或另建同码项目"));continue;
                }
            } else if (!creating && entry.lastApplied() == null) {
                if (!request.adoptExisting() || !Objects.equals(entry.expectedProjectVersion(), row.getVersion())) {
                    results.add(issue(entry,before,"初次承接必须明确接管并核对项目版本；禁止覆盖后续变化")); continue;
                }
                if (source==null) {results.add(issue(entry,before,"现存项目首次接管缺少已复核来源证据"));continue;}
                var originals = sources.selectRecords(new DirectProjectEvidenceMapper.RecordsPageQuery(tenant,
                        source.getCurrentSnapshotId(),"project",entry.sourceKey(),null,null,0,2));
                if (originals.size()!=1 || !"pm_project_header".equals(originals.getFirst().getSourceTable())
                        || !sameOriginal(entry.originalFields(),JsonUtils.parseObject(originals.getFirst().getPayloadJson(),
                        new TypeReference<Map<String,String>>() {}))) {
                    results.add(issue(entry,before,"原37列与项目既有来源证据不一致；必须先复核新的来源修订")); continue;
                }
            } else if (!creating && !Objects.equals(before, entry.lastApplied())) {
                results.add(issue(entry,before,"正式项目已偏离上次同步结果；不接管人工、运行时或其他业务修改")); continue;
            }
            Map<String,String> original = entry.originalFields();
            if (original.get("customConfig") != null) {
                results.add(issue(entry,before,"非空customConfig尚无获准正式承接点，等待裁决")); continue;
            }
            boolean missingCompany=original.get("compId")==null;
            boolean missingDepartment=original.get("column001")==null;
            if (missingCompany != (entry.companyCode()==null) || missingDepartment != (entry.departmentCode()==null))
                throw new IllegalArgumentException("DPPMS_PROJECT_ORIGINAL_ORGANIZATION_NULL_PROTECTED");
            if (!missingCompany && !Set.of("001","002","003","004").contains(entry.companyCode()))
                throw new IllegalArgumentException("DPPMS_PROJECT_COMPANY_CODE_UNADJUDICATED");
            var company = missingCompany?null:companies.getCompanyByCode(entry.companyCode());
            if (!missingCompany && company==null) {
                results.add(issue(entry,before,"原公司编码未关联正式公司；不得作为空公司迁入"));continue;
            }
            if (missingCompany || missingDepartment) {
                creationAuthorization.assertCanImportWithoutOrganization(request.actorId());
                if (!creating && (missingCompany && (row.getCompanyId()!=null || row.getCompanyCode()!=null || row.getCompanyName()!=null)
                        || missingDepartment && (row.getDepartmentId()!=null || row.getDepartmentCode()!=null || row.getDepartmentName()!=null))) {
                    results.add(issue(entry,before,"原空归属不能清空已有正式公司或办事处"));continue;
                }
            }
            if (creating && !missingDepartment) {
                if (departmentCatalogue==null) departmentCatalogue=historicalDepartments.getCatalogue();
                var matches=departmentCatalogue.stream().filter(d->Objects.equals(d.code(),entry.departmentCode())).toList();
                if (matches.size()!=1) {results.add(issue(entry,null,"原办事处未唯一关联正式部门；不按名称或前缀猜配"));continue;}
                var selected=matches.getFirst();row.setDepartmentId(selected.id());row.setDepartmentCode(selected.code());
                row.setDepartmentName(selected.name());
            }
            var department = row.getDepartmentId() == null ? null : departments.getDept(row.getDepartmentId());
            if (!missingDepartment && (department == null || !Objects.equals(department.getCode(),entry.departmentCode())
                    || !Objects.equals(row.getDepartmentCode(),entry.departmentCode()))) {
                results.add(issue(entry,before,"公司或项目正式办事处未精确关联；不得猜配或清空")); continue;
            }
            if (creating && !missingCompany && !missingDepartment && !organization.hasScope(request.actorId(),company.getId(),department.getId()))
                creationAuthorization.assertCanImportAcrossOrganizations(request.actorId());
            requireHistoricalActor(original.get("createBy"),entry.creator());
            requireHistoricalActor(original.get("updateBy"),entry.updater());
            LocalDateTime created = time(original.get("createTime"));
            LocalDateTime updated = time(original.get("updateTime"));
            if (created == null) throw new IllegalArgumentException("DPPMS_PROJECT_CREATE_TIME_REQUIRED");
            if (updated == null) updated = created;
            LocalDateTime effectiveFrom = time(original.get("effectiveFrom")), effectiveTo = time(original.get("effectiveTo"));
            if (effectiveFrom!=null && effectiveTo!=null && effectiveTo.isBefore(effectiveFrom))
                throw new IllegalArgumentException("DPPMS_PROJECT_VALIDITY_INTERVAL_INVALID");
            String code = formalCode(entry);
            String name = original.get("projectName");
            if (name == null || name.isBlank()) name = code;
            if (code == null || code.isEmpty() || code.length()>64 || name.length()>200) {
                results.add(issue(entry,before,"DPPMS_PROJECT_NAME_OR_CODE_INVALID：编码缺失或名称/编码超长；保留问题行"));
                continue;
            }
            if(entry.lastApplied()!=null && !Objects.equals(code,entry.lastApplied().projectCode())) {
                results.add(issue(entry,before,"已绑定项目重跑必须复用已承接编码，不得再次递增尾号"));continue;
            }
            if (projects.selectExactCodeIds(new DirectProjectMapper.CodeQuery(tenant,code)).stream()
                    .anyMatch(id -> !Objects.equals(id,row.getId()))) {
                results.add(issue(entry,before,"旧业务编码已被其他项目占用；禁止改名、合并或重绑")); continue;
            }
            if (!Set.of("10","afss","afxx").contains(original.get("projectType")==null?"":original.get("projectType")))
                throw new IllegalArgumentException("DPPMS_PROJECT_TYPE_UNADJUDICATED");
            validateOriginalJson(original.get("customInfo"));
            var state = DirectProjectHistoricalState.resolve(original.get("projectType"),original.get("projectState"),
                    time(original.get("projectCloseTime")),new DirectProjectHistoricalState.State(
                            row.getLifecycleStatus(),row.getCurrentStage(),row.getClosedAt()),entry.lastApplied()!=null);
            String reason = original.get("column008");
            if ("20".equals(original.get("projectState")) && reason==null) reason="不予跟踪";
            var after = new Facts(row.getId(),tenant,row.getVersion(),code,name,
                    company==null?null:company.getId(),company==null?null:company.getCode(),company==null?null:company.getName(),original.get("projectType"),
                    category(original.get("column010")),signing(original.get("column011")),implementation(original.get("column012"),original.get("columno12_readonly")),
                    original.get("customerProjectName"),original.get("salesType"),original.get("majorProjectLevel"),
                    original.get("column004"),original.get("column005"),original.get("column006"),original.get("column007"),reason,
                    time(original.get("projectStartTime")),time(original.get("projectRefreshTime")),time(original.get("projectCloseTime")),
                    time(original.get("column009")),canonicalJson(original.get("customInfo")),bool(original.get("disabled")),effectiveFrom,effectiveTo,
                    entry.creator(),created,entry.updater(),updated,state.lifecycleStatus(),state.currentStage(),state.closedAt());
            String action=creating?"CREATED":!restoring && Objects.equals(before,after)?"UNCHANGED":"UPDATED";
            if (creating && apply) {
                row.setId(IdWorker.getId());
                after=withIdentity(after,row.getId(),0L);
                if (projects.insertHistoricalRoot(new DirectProjectMapper.HistoricalRoot(after,
                        row.getDepartmentId(),row.getDepartmentCode(),row.getDepartmentName()))!=1)
                    throw new IllegalStateException("DPPMS_PROJECT_CREATION_WRITE_CONFLICT");
                var persisted=snapshot(projects.selectIdentity(new DirectProjectMapper.IdentityQuery(tenant,row.getId())));
                if (!Objects.equals(after,persisted)) throw new IllegalStateException("DPPMS_PROJECT_FORMAL_ROUND_TRIP_CHANGED");
                var projection=treeProjection.publish(new DirectProjectRootProjection.Root(tenant,row.getId(),request.actorId()));
                if(projection==null || !Objects.equals(projection.rootProjectId(),row.getId()) || projection.treeVersion()!=1L
                        || projection.nodeCount()!=1 || projection.pathCount()!=1)
                    throw new IllegalStateException("DPPMS_PROJECT_ROOT_PROJECTION_INVALID");
                after=persisted;
            }
            if ("UPDATED".equals(action)) {
                after=withVersion(after,row.getVersion()+1);
                if (restoring && apply && projects.restoreHistoricalIfMatch(new DirectProjectMapper.RestorationUpdate(
                        tenant,row.getId(),row.getVersion(),entry.restoration().expectedFingerprint()))!=1)
                    throw new IllegalStateException("DPPMS_PROJECT_RESTORATION_CONCURRENT_CHANGE");
                if (apply && projects.initializeFormalIfMatch(new DirectProjectMapper.InitializationUpdate(
                        tenant,row.getId(),row.getVersion(),row.getLifecycleStatus(),row.getCurrentStage(),after))!=1)
                    throw new IllegalStateException("DPPMS_PROJECT_INITIALIZATION_CONCURRENT_CHANGE");
                if (apply) {
                    Facts persisted=snapshot(projects.selectIdentity(identity));
                    if (!Objects.equals(after,persisted)) throw new IllegalStateException("DPPMS_PROJECT_FORMAL_ROUND_TRIP_CHANGED");
                    after=persisted;
                }
            }
            results.add(new Result(entry.sourceKey(),action,before,after,restoring
                    ?"按裁决恢复已撤回项目并复用原ID；原审计、运行回执及树历史保留"
                    :"正式字段承接历史；不生成当前任务、审批或分配完成事实"));
        }
        return List.copyOf(results);
    }

    private void requireHistoricalActor(String original, String resolved) {
        if (original == null || original.isEmpty()) {
            if (!Objects.equals(original,resolved)) throw new IllegalArgumentException("DPPMS_PROJECT_NULL_ACTOR_PROTECTED");
            return;
        }
        if(Set.of("sys","jiangyating").contains(original) && Objects.equals(original,resolved)) return;
        if (resolved == null || !resolved.matches("[1-9][0-9]*"))
            throw new IllegalArgumentException("DPPMS_PROJECT_ACTOR_USER_BINDING_REQUIRED");
        var identities = historicalUsers.getHistoricalIdentities(TenantContextHolder.getRequiredTenantId(),Set.of(Long.valueOf(resolved)));
        if (identities.size()!=1 || !Objects.equals(identities.getFirst().id(),Long.valueOf(resolved))
                || !Objects.equals(identities.getFirst().username(),original))
            throw new IllegalArgumentException("DPPMS_PROJECT_ACTOR_USER_IDENTITY_CHANGED");
    }
    static boolean sameOriginal(Map<String,String> live, Map<String,String> saved) {
        if (saved==null || !saved.keySet().containsAll(ORIGINAL_COLUMNS)) return false;
        for (String key:ORIGINAL_COLUMNS) {
            String left=live.get(key),right=saved.get(key);
            if (DATES.contains(key)) { if (!Objects.equals(time(left),time(right))) return false; }
            else if ("disabled".equals(key)) { if (!Objects.equals(bool(left),bool(right))) return false; }
            else if (!Objects.equals(left,right)) return false;
        }
        return true;
    }
    static LocalDateTime time(String value) { return value==null?null:LocalDateTime.parse(value.replace(' ','T')); }
    static Boolean bool(String value) {
        if (value==null) return null;
        return switch(value) { case "0","false"->false; case "1","true"->true;
            default->throw new IllegalArgumentException("DPPMS_PROJECT_DISABLED_INVALID"); };
    }
    static String category(String value) { if(value==null)return null;return switch(value) {
        case "10"->"GENERAL";case "20"->"ENGINEERING";
        default->throw new IllegalArgumentException("DPPMS_PROJECT_CATEGORY_UNADJUDICATED");}; }
    static String signing(String value) { if(value==null)return null;return switch(value) {
        case "10"->"DIRECT_SIGN";case "20"->"CHANNEL_SIGN";
        default->throw new IllegalArgumentException("DPPMS_PROJECT_SIGNING_UNADJUDICATED");}; }
    static String implementation(String value) { if(value==null)return null;return switch(value) {
        case "0"->"DIRECT_SERVICE";case "1"->"SUPERVISION";case "2"->"DIRECT_SUBCONTRACT";
        case "3"->"AGENT_SELF_SERVICE";case "4"->"FACTORY_INTEGRATION";
        default->throw new IllegalArgumentException("DPPMS_PROJECT_IMPLEMENTATION_UNADJUDICATED");}; }
    static String implementation(String original,String readonly) {
        String value=original;
        if((value==null || value.isEmpty()) && readonly!=null && !"-1".equals(readonly)) value=readonly;
        return implementation(value);
    }
    static void validateOriginalJson(String value) {
        if (value!=null) try { ORIGINAL_JSON.readValue(value,Object.class); }
        catch (RuntimeException error) { throw new IllegalArgumentException("DPPMS_PROJECT_CUSTOM_INFO_INVALID"); }
    }
    static String canonicalJson(String value) {
        if (value==null) return null;
        try { return ORIGINAL_JSON.writeValueAsString(ORIGINAL_JSON.readValue(value,Object.class)); }
        catch (RuntimeException error) { throw new IllegalArgumentException("DPPMS_PROJECT_CUSTOM_INFO_INVALID"); }
    }
    private static Result issue(Entry entry,Facts before,String message) { return new Result(entry.sourceKey(),"ISSUE",before,null,message); }
    static String formalCode(Entry entry) {
        String original=entry.originalFields().get("projectCode");var allocation=entry.codeAllocation();
        if(allocation==null) return original;
        if(!Objects.equals(original,allocation.originalProjectCode()) || allocation.orderedProjects()==null
                || allocation.orderedProjects().size()<2 || allocation.projectCode()==null || original==null)
            throw new IllegalArgumentException("DPPMS_PROJECT_COLLISION_PLAN_INVALID");
        long previous=0;Set<String> types=new HashSet<>();boolean selected=false;
        for(int i=0;i<allocation.orderedProjects().size();i++) {
            var row=allocation.orderedProjects().get(i);
            if(row==null || row.projectId()==null || row.projectId()<=previous || row.projectType()==null
                    || !Set.of("10","afss","afxx").contains(row.projectType()) || !types.add(row.projectType()))
                throw new IllegalArgumentException("DPPMS_PROJECT_COLLISION_PLAN_INVALID");
            previous=row.projectId();
            if(entry.sourceKey().equals(row.projectId().toString())) {
                if(i==0 || !Objects.equals(entry.originalFields().get("projectType"),row.projectType()))
                    throw new IllegalArgumentException("DPPMS_PROJECT_COLLISION_PLAN_INVALID");
                selected=true;
            }
        }
        int separator=original.lastIndexOf('-');String target=allocation.projectCode();
        if(!selected || separator<0 || !original.substring(separator+1).matches("[0-9]+")
                || !target.startsWith(original.substring(0,separator+1)) || !target.substring(separator+1).matches("[0-9]+")
                || new java.math.BigInteger(target.substring(separator+1)).compareTo(new java.math.BigInteger(original.substring(separator+1)))<=0)
            throw new IllegalArgumentException("DPPMS_PROJECT_COLLISION_PLAN_INVALID");
        return target;
    }
    static Facts snapshot(DirectProjectRow p) {
        return new Facts(p.getId(),p.getTenantId(),p.getVersion(),p.getProjectCode(),p.getProjectName(),
                p.getCompanyId(),p.getCompanyCode(),p.getCompanyName(),p.getProjectType(),p.getProjectCategory(),p.getSigningMethod(),
                p.getImplementationMode(),p.getCustomerProjectName(),p.getSalesType(),p.getMajorProjectLevel(),p.getMarketName(),
                p.getSystemName(),p.getExpendName(),p.getIndustryName(),p.getNotTrackReason(),p.getProjectStartTime(),
                p.getProjectRefreshTime(),p.getProjectCloseTime(),p.getOrderCreateTime(),canonicalJson(p.getCustomInfo()),p.getDisabled(),
                p.getEffectiveFrom(),p.getEffectiveTo(),p.getCreator(),p.getCreateTime(),p.getUpdater(),p.getUpdateTime(),
                p.getLifecycleStatus(),p.getCurrentStage(),p.getClosedAt());
    }
    private static Facts withVersion(Facts p,Long version) {
        return withIdentity(p,p.id(),version);
    }
    private static Facts withIdentity(Facts p,Long id,Long version) {
        return new Facts(id,p.tenantId(),version,p.projectCode(),p.projectName(),p.companyId(),p.companyCode(),p.companyName(),
                p.projectType(),p.projectCategory(),p.signingMethod(),p.implementationMode(),p.customerProjectName(),p.salesType(),
                p.majorProjectLevel(),p.marketName(),p.systemName(),p.expendName(),p.industryName(),p.notTrackReason(),
                p.projectStartTime(),p.projectRefreshTime(),p.projectCloseTime(),p.orderCreateTime(),p.customInfo(),p.disabled(),
                p.effectiveFrom(),p.effectiveTo(),p.creator(),p.createTime(),p.updater(),p.updateTime(),p.lifecycleStatus(),p.currentStage(),p.closedAt());
    }
    private void requireTransaction() {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("项目主档直写规划及写入须处于活动事务");
        var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        try{if(connection.getTransactionIsolation()!=java.sql.Connection.TRANSACTION_REPEATABLE_READ)throw new IllegalStateException("项目主档直写需要REPEATABLE_READ");}
        catch(java.sql.SQLException error){throw new IllegalStateException("无法确认项目主档直写事务隔离",error);}
        finally{org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection,dataSource);}
    }

}
