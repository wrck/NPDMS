package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetEntityImportApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsWarrantySourceRecordsApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationAddressImportApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectPartyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailCatalog;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyNativeEvidenceService.identity;

/** Promotes proven facts through their Owners. Historical lifecycle/people/template state stays historical. */
@Service
@RequiredArgsConstructor
public class ProjectLegacyEntityMigrationService {
    private final ProjectManualCreationService projects;
    private final ProjectLegacyDetailMapper mapper;
    private final LegacyDetailCatalog catalog;
    private final ProjectPartyMapper parties;
    private final DppmsCommerceEntityResolveApi commerce;
    private final DppmsAssetEntityImportApi assets;
    private final DppmsAssetSourceHistoryApi sourceHistory;
    private final DppmsWarrantySourceRecordsApi warrantySources;
    private final DppmsInstallationAddressImportApi installations;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;
    private final ProjectAssociatedDataService associated;
    private final DppmsCommerceDetailImportApi commerceDetails;
    private final cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi installationSources;
    private final DeviceQueryApi devices;
    private final ProjectDeviceSelectionApi deviceSelection;
    private final ProjectFactoryRecordImportService factoryRecords;

    public record Request(@NotNull @Positive Long sourceId, @NotNull @Positive Long snapshotId,
                          @NotBlank @Pattern(regexp="[0-9a-f]{64}") String checksum,
                          @Positive Long softwareHistoryDefinitionId) {
        public Request(Long sourceId,Long snapshotId,String checksum) { this(sourceId,snapshotId,checksum,null); }
    }
    public record DomainSummary(String domain, String label, int sourceCount, int mappedCount, int retainedCount,
                                int pendingCount, List<String> targetTables, Map<String, Integer> retentionReasons) {}
    public record Report(Long sourceId, Long snapshotId, int sourceCount, int mappedCount, int retainedCount,
                         int pendingCount, List<DomainSummary> domains) {}
    public record Result(String decision, Report report) {}
    public record FactoryCorrectionTarget(String sourceKey, ExternalTargetMapping target) {}
    public record FactoryCorrectionReport(Long sourceId, Long snapshotId, int sourceCount,
                                          List<FactoryCorrectionTarget> targets) {
        public FactoryCorrectionReport { targets = List.copyOf(targets); }
    }
    public record FactoryCorrectionResult(String decision, FactoryCorrectionReport report) {}

    public record InstallationCandidateCorrectionRequest(@NotNull @Positive Long sourceId,@NotNull @Positive Long snapshotId,
            @NotBlank @Pattern(regexp="[0-9a-f]{64}") String checksum,
            @NotNull @Size(min=1,max=100) List<@NotBlank @Pattern(regexp="[0-9]{1,32}") String> sourceKeys) {}
    public record CorrectionTarget(String sourceKey,List<ExternalTargetMapping> targets) {
        public CorrectionTarget { targets=List.copyOf(targets); }
    }
    public record CorrectionReport(Long sourceId,Long snapshotId,String domain,int sourceCount,List<CorrectionTarget> targets) {
        public CorrectionReport { targets=List.copyOf(targets); }
    }
    public record CorrectionResult(String decision,CorrectionReport report) {}

    record Loaded(ProjectLegacySourceDO source, ProjectLegacySnapshotDO snapshot,
                          Map<String, List<ProjectLegacyRecordDO>> domains) {}
    private record ShipmentIdentity(String deviceSn, String packageKey) {}
    private static final Set<String> REQUIRED_NATIVE_DOMAINS=Set.of("project","products","real-products","lease-products",
            "configured-products","plans","shipment-packages","orders","order-lines","executions","shipment-facts",
            "shipment-products","installations","factory-versions","software-versions","software-changes",
            "warranty-services","warranty-registrations","warranty-changes");

    @Transactional(rollbackFor=Exception.class)
    public Result migrate(Long projectId, Request request, ProjectAccessActor actor) {
        validateActor(actor);
        projects.getProjectForManage(projectId, actor);
        var loaded = load(projectId, request.sourceId(), request.snapshotId(), actor, true);
        if (!Objects.equals(request.checksum(), loaded.snapshot().getChecksum())) fail("DPPMS_NATIVE_CHECKSUM_CONFLICT");
        requireFullSource(loaded);
        String protocol = "V4";
        String key = "DPPMS-NATIVE:" + request.sourceId() + ":" + request.snapshotId() + ":" + protocol;
        String digest = DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId, request.sourceId(),
                request.snapshotId(), request.checksum(), protocol)));
        // A source snapshot is one migration across authorized operators; actual operator is recorded in audit detail.
        var execution = commands.execute(new IdempotencyScope(actor.tenantId(), "PROJ:DPPMS:NATIVE_ENTITIES", 0L, key),
                digest, Report.class, () -> importEntities(projectId, loaded, actor,request.softwareHistoryDefinitionId()), report -> new SuccessFacts(
                        "PROJ_DPPMS_NATIVE_MIGRATE", "Project", projectId.toString(), key,
                        JsonUtils.toJsonString(Map.of("operatorUserId", actor.actorId(), "report", report)), null, null));
        if (execution.decision() == Decision.CONFLICT || execution.decision() == Decision.IN_PROGRESS) {
            fail("DPPMS_NATIVE_" + execution.decision());
        }
        return new Result(execution.decision().name(), execution.response());
    }

    /** Reconcile only the approved header and its final-customer party after INT master carry. */
    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult correctProjectHeader(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=loadCorrectionSource(projectId,request,actor);
        var headers=loaded.domains().get("project");
        if (headers.size()!=1 || !Objects.equals(headers.getFirst().getSourceKey(),loaded.source().getSourceProjectKey()))
            fail("DPPMS_PROJECT_HEADER_CORRECTION_IDENTITY_INVALID");
        Map<String,List<ProjectLegacyRecordDO>> selected=new LinkedHashMap<>();
        for (String domain:List.of("project","products","real-products","lease-products","configured-products"))
            selected.put(domain,"project".equals(domain)?headers:List.of());
        // Verify the INT receipt and current formal project before every replay. Only the party Owner can write.
        var outcomes=associated.importRecords(projectId,selected,actor).rows();
        if (outcomes.size()!=1 || outcomes.getFirst().reasonCode()!=null
                || !"project".equals(outcomes.getFirst().domain())
                || !Objects.equals(outcomes.getFirst().sourceKey(),headers.getFirst().getSourceKey()))
            fail("DPPMS_PROJECT_HEADER_CORRECTION_OWNER_RESULT_INVALID");
        var targets=outcomes.getFirst().targets();
        boolean named=value(headers.getFirst(),"column013")!=null&&!value(headers.getFirst(),"column013").isBlank();
        if (targets.size()!=(named?2:1) || !"proj_project".equals(targets.getFirst().targetTable())
                || !Objects.equals(targets.getFirst().targetId(),projectId)
                || named && (!"proj_project_party".equals(targets.get(1).targetTable())
                    || targets.get(1).targetId()==null || targets.get(1).targetId()<=0))
            fail("DPPMS_PROJECT_HEADER_CORRECTION_OWNER_RESULT_INVALID");
        String key="DPPMS-PROJECT-HEADER-CORRECTION:"+request.sourceId()+":"+request.snapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request,targets)));
        var execution=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:PROJECT_HEADER_CORRECTION",0L,key),
                digest,CorrectionReport.class,()->{
                    evidence.reconcile(loaded.source(),loaded.snapshot(),"project",headers,
                            Map.of("project:"+headers.getFirst().getSourceKey(),outcomes.getFirst()),"PROJECT_HEADER_V1");
                    return new CorrectionReport(request.sourceId(),request.snapshotId(),"project",1,
                            List.of(new CorrectionTarget(headers.getFirst().getSourceKey(),targets)));
                },report->new SuccessFacts("PROJ_DPPMS_PROJECT_HEADER_RECONCILE","Project",projectId.toString(),key,
                        JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if (execution.decision()==Decision.CONFLICT || execution.decision()==Decision.IN_PROGRESS)
            fail("DPPMS_PROJECT_HEADER_CORRECTION_"+execution.decision());
        return new CorrectionResult(execution.decision().name(),execution.response());
    }

    /** A separate correction receipt cannot replay or rewrite the completed native-migration receipt. */
    @Transactional(rollbackFor=Exception.class)
    public FactoryCorrectionResult correctFactoryVersions(Long projectId, Request request, ProjectAccessActor actor) {
        validateActor(actor);
        projects.getProjectForManage(projectId, actor);
        var loaded = load(projectId, request.sourceId(), request.snapshotId(), actor, true);
        if (!Objects.equals(loaded.source().getId(), request.sourceId())
                || !Objects.equals(loaded.source().getTenantId(), actor.tenantId())
                || !Objects.equals(loaded.source().getProjectId(), projectId)
                || !Objects.equals(loaded.snapshot().getId(), request.snapshotId())
                || !Objects.equals(loaded.snapshot().getTenantId(), actor.tenantId())) {
            fail("DPPMS_FACTORY_CORRECTION_SOURCE_INACCESSIBLE");
        }
        if (!Objects.equals(request.checksum(), loaded.snapshot().getChecksum())) fail("DPPMS_NATIVE_CHECKSUM_CONFLICT");
        requireFullSource(loaded);
        var factories = loaded.domains().get("factory-versions");
        if (factories.isEmpty()) fail("DPPMS_FACTORY_CORRECTION_EMPTY_SOURCE");
        var sourceKeys = new HashSet<String>();
        var serialNumbers = new HashSet<String>();
        for (var row : factories) {
            String serialNumber = value(row, "serial_number");
            if (!Objects.equals(row.getTenantId(), actor.tenantId())
                    || !Objects.equals(row.getSnapshotId(), loaded.snapshot().getId())
                    || !"factory-versions".equals(row.getDomainCode())
                    || !catalog.require("factory-versions").sourceTable().equals(row.getSourceTable())
                    || row.getSourceKey() == null || row.getSourceKey().isBlank()
                    || serialNumber == null || serialNumber.isBlank()
                    || !sourceKeys.add(row.getSourceKey()) || !serialNumbers.add(serialNumber)) {
                fail("DPPMS_FACTORY_CORRECTION_SOURCE_IDENTITY_INVALID");
            }
        }
        String protocol = "V1";
        String key = "DPPMS-FACTORY-CORRECTION:" + request.sourceId() + ":" + request.snapshotId() + ":" + protocol;
        String digest = DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId, request.sourceId(),
                request.snapshotId(), request.checksum(), protocol)));
        var execution = commands.execute(new IdempotencyScope(actor.tenantId(), "PROJ:DPPMS:FACTORY_CORRECTION", 0L, key),
                digest, FactoryCorrectionReport.class, () -> importFactoryCorrection(projectId, loaded, factories, actor),
                report -> new SuccessFacts("PROJ_DPPMS_FACTORY_CORRECT", "Project", projectId.toString(), key,
                        JsonUtils.toJsonString(Map.of("operatorUserId", actor.actorId(), "report", report)), null, null));
        if (execution.decision() == Decision.CONFLICT || execution.decision() == Decision.IN_PROGRESS) {
            fail("DPPMS_FACTORY_CORRECTION_" + execution.decision());
        }
        return new FactoryCorrectionResult(execution.decision().name(), execution.response());
    }

    private FactoryCorrectionReport importFactoryCorrection(Long projectId, Loaded loaded,
            List<ProjectLegacyRecordDO> factories, ProjectAccessActor actor) {
        var incoming = factories.stream().map(row -> new DppmsAssetEntityImportApi.FactoryVersion(row.getSourceKey(),
                value(row,"serial_number"), value(row,"conp"), value(row,"boot"), value(row,"cpld"), value(row,"pcb"))).toList();
        var outcomes = assets.importEntities(new DppmsAssetEntityImportApi.Command(actor.tenantId(), actor.actorId(), projectId,
                loaded.source().getSourceContractNo(), loaded.snapshot().getId(), loaded.snapshot().getChecksum(),
                loaded.snapshot().getSourceReadAt(), List.of(), List.of(), List.of(), incoming));
        if (outcomes == null || outcomes.size() != factories.size()) fail("DPPMS_FACTORY_CORRECTION_OWNER_RESULT_INVALID");
        var expected = new HashSet<String>(); factories.forEach(row -> expected.add(row.getSourceKey()));
        var bySource = new HashMap<String, ExternalTargetMapping>();
        var targetIds = new HashSet<Long>();
        for (var outcome : outcomes) {
            if (outcome == null || !"factory-versions".equals(outcome.domain()) || !expected.contains(outcome.sourceKey())
                    || bySource.containsKey(outcome.sourceKey())) fail("DPPMS_FACTORY_CORRECTION_OWNER_RESULT_INVALID");
            if (outcome.reasonCode() != null) fail("DPPMS_FACTORY_CORRECTION_BLOCKED:" + outcome.reasonCode());
            if (outcome.targets().size() != 1) fail("DPPMS_FACTORY_CORRECTION_OWNER_RESULT_INVALID");
            var target = outcome.targets().getFirst();
            if (!"AST".equals(target.targetContext()) || !"DeviceFactoryVersion".equals(target.targetObjectType())
                    || !"ast_device_factory_version".equals(target.targetTable()) || !"FACTORY_VERSION".equals(target.targetRole())
                    || target.targetSequence() != 0 || target.targetId() == null || target.targetId() <= 0
                    || !targetIds.add(target.targetId())) fail("DPPMS_FACTORY_CORRECTION_OWNER_RESULT_INVALID");
            bySource.put(outcome.sourceKey(), target);
        }
        if (!bySource.keySet().equals(expected)) fail("DPPMS_FACTORY_CORRECTION_OWNER_RESULT_INVALID");
        return new FactoryCorrectionReport(loaded.source().getId(), loaded.snapshot().getId(), factories.size(),
                factories.stream().map(row -> new FactoryCorrectionTarget(row.getSourceKey(), bySource.get(row.getSourceKey()))).toList());
    }


    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult correctCarrierNames(Long projectId, Request request, ProjectAccessActor actor) {
        var loaded=loadCorrectionSource(projectId,request,actor);
        var packages=loaded.domains().get("shipment-packages");
        if(packages.isEmpty())fail("DPPMS_CARRIER_CORRECTION_EMPTY_SOURCE");
        var contracts=loaded.domains().get("contracts");
        if(contracts.size()!=1||!Objects.equals(value(contracts.getFirst(),"contractNo"),loaded.source().getSourceContractNo()))
            fail("DPPMS_NATIVE_CONTRACT_SOURCE_UNRESOLVED");
        var incoming=new ArrayList<DppmsCommerceDetailImportApi.Package>();
        var packageNumbers=new HashSet<String>();
        for(var row:packages) {
            String number=value(row,"packlist_id");
            var logistics=loaded.domains().get("shipment-logistics").stream()
                    .filter(r->Objects.equals(value(r,"packId"),number)).toList();
            if(number==null||number.isBlank()||!packageNumbers.add(number)||logistics.size()!=1
                    ||value(logistics.getFirst(),"emsCompany")==null||value(logistics.getFirst(),"emsCompany").isBlank())
                fail("DPPMS_CARRIER_CORRECTION_SOURCE_IDENTITY_INVALID");
            incoming.add(new DppmsCommerceDetailImportApi.Package(row.getSourceKey(),number,null,null,null,null,null,
                    value(logistics.getFirst(),"emsCompany"),null,value(row,"emsCompany")));
        }
        String key="DPPMS-CARRIER-CORRECTION:"+request.sourceId()+":"+request.snapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request.sourceId(),request.snapshotId(),request.checksum(),"V1")));
        var execution=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:CARRIER_CORRECTION",0L,key),
                digest,CorrectionReport.class,()->{
                    var identities=new DppmsCommerceEntityResolveApi.Command(actor.tenantId(),actor.actorId(),projectId,
                            loaded.source().getSourceContractNo(),List.of(),List.of(),List.of(),List.of(),contracts.getFirst().getSourceKey());
                    var outcomes=commerceDetails.importDetails(new DppmsCommerceDetailImportApi.Command(identities,
                            List.of(),List.of(),incoming,List.of(),List.of()));
                    return carrierCorrectionReport(loaded,packages,contracts.getFirst().getSourceKey(),outcomes);
                },report->new SuccessFacts("PROJ_DPPMS_CARRIER_CORRECT","Project",projectId.toString(),key,
                        JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(execution.decision()==Decision.CONFLICT||execution.decision()==Decision.IN_PROGRESS)
            fail("DPPMS_CARRIER_CORRECTION_"+execution.decision());
        return new CorrectionResult(execution.decision().name(),execution.response());
    }

    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult correctInstallationCandidates(Long projectId, InstallationCandidateCorrectionRequest request,
            ProjectAccessActor actor) {
        projects.getProjectForManage(projectId,actor);
        throw new IllegalArgumentException("旧安装候选隔离方案已撤回，请使用正式安装记录迁移");
    }

    Loaded loadCorrectionSource(Long projectId,Request request,ProjectAccessActor actor) {
        validateActor(actor);projects.getProjectForManage(projectId,actor);
        var loaded=load(projectId,request.sourceId(),request.snapshotId(),actor,true);
        if(!Objects.equals(loaded.source().getId(),request.sourceId())
                ||!Objects.equals(loaded.source().getTenantId(),actor.tenantId())
                ||!Objects.equals(loaded.source().getProjectId(),projectId)
                ||!Objects.equals(loaded.snapshot().getId(),request.snapshotId())
                ||!Objects.equals(loaded.snapshot().getTenantId(),actor.tenantId()))
            fail("DPPMS_CORRECTION_SOURCE_INACCESSIBLE");
        if(!Objects.equals(request.checksum(),loaded.snapshot().getChecksum()))fail("DPPMS_NATIVE_CHECKSUM_CONFLICT");
        requireFullSource(loaded);
        for(var domain:catalog.domains()) {
            var sourceKeys=new HashSet<String>();
            for(var row:loaded.domains().get(domain.code()))
                if(!Objects.equals(row.getTenantId(),actor.tenantId())
                        ||!Objects.equals(row.getSnapshotId(),loaded.snapshot().getId())
                        ||!Objects.equals(row.getDomainCode(),domain.code())
                        ||!Objects.equals(row.getSourceTable(),domain.sourceTable())
                        ||row.getSourceKey()==null||row.getSourceKey().isBlank()||!sourceKeys.add(row.getSourceKey()))
                    fail("DPPMS_CORRECTION_SOURCE_IDENTITY_INVALID");
        }
        return loaded;
    }

    private CorrectionReport carrierCorrectionReport(Loaded loaded,List<ProjectLegacyRecordDO> rows,String contractKey,
            List<NativeEntityMigrationRow> outcomes) {
        if(outcomes==null||outcomes.size()!=rows.size()+1)fail("DPPMS_CARRIER_CORRECTION_OWNER_RESULT_INVALID");
        var expected=new HashSet<String>();rows.forEach(row->expected.add(row.getSourceKey()));
        var bySource=new HashMap<String,List<ExternalTargetMapping>>();var targetIds=new HashSet<Long>();
        boolean contractFound=false;
        for(var outcome:outcomes) {
            if(outcome==null)fail("DPPMS_CARRIER_CORRECTION_OWNER_RESULT_INVALID");
            if(outcome.reasonCode()!=null)fail("DPPMS_CARRIER_CORRECTION_BLOCKED:"+outcome.reasonCode());
            if("contracts".equals(outcome.domain())) {
                if(contractFound||!Objects.equals(outcome.sourceKey(),contractKey)||outcome.targets().size()!=1
                        ||!targetMatches(outcome.targets().getFirst(),"COM","Contract","com_contract","BUSINESS_ENTITY",0))
                    fail("DPPMS_CARRIER_CORRECTION_OWNER_RESULT_INVALID");
                contractFound=true;continue;
            }
            if(!"shipment-packages".equals(outcome.domain())||!expected.contains(outcome.sourceKey())
                    ||bySource.containsKey(outcome.sourceKey())||outcome.targets().size()!=1
                    ||!targetMatches(outcome.targets().getFirst(),"COM","ShipmentPackage","com_shipment_package","BUSINESS_ENTITY",0)
                    ||!targetIds.add(outcome.targets().getFirst().targetId()))
                fail("DPPMS_CARRIER_CORRECTION_OWNER_RESULT_INVALID");
            bySource.put(outcome.sourceKey(),outcome.targets());
        }
        if(!contractFound||!bySource.keySet().equals(expected))fail("DPPMS_CARRIER_CORRECTION_OWNER_RESULT_INVALID");
        return new CorrectionReport(loaded.source().getId(),loaded.snapshot().getId(),"shipment-packages",rows.size(),
                rows.stream().map(row->new CorrectionTarget(row.getSourceKey(),bySource.get(row.getSourceKey()))).toList());
    }

    private static boolean targetMatches(ExternalTargetMapping target,String context,String objectType,String table,String role,int sequence) {
        return target!=null&&Objects.equals(context,target.targetContext())&&Objects.equals(objectType,target.targetObjectType())
                &&Objects.equals(table,target.targetTable())&&Objects.equals(role,target.targetRole())
                &&target.targetSequence()==sequence&&target.targetId()!=null&&target.targetId()>0;
    }

    /** Only a fresh, complete source capture may be promoted. Older evidence remains readable. */
    private void requireFullSource(Loaded loaded) {
        Map<String,Integer> counts;
        try {
            counts=JsonUtils.parseObject(loaded.snapshot().getDomainCountsJson(),new TypeReference<Map<String,Integer>>(){});
        } catch (RuntimeException failure) {
            fail("DPPMS_NATIVE_FULL_SOURCE_REQUIRED");return;
        }
        var domains=new HashSet<String>();catalog.domains().forEach(domain->domains.add(domain.code()));
        if(counts!=null && !counts.containsKey("real-product-upstream")
                && loaded.domains().get("real-product-upstream").isEmpty()) domains.remove("real-product-upstream");
        if(counts==null||!counts.keySet().equals(domains))fail("DPPMS_NATIVE_FULL_SOURCE_REQUIRED");
        for(var domain:catalog.domains()) {
            var records=loaded.domains().get(domain.code());
            if(!Objects.equals(counts.getOrDefault(domain.code(),0),records.size()))fail("DPPMS_NATIVE_FULL_SOURCE_REQUIRED");
            var fields=new HashSet<String>();domain.fields().forEach(field->fields.add(field.key()));
            for(var record:records) {
                Map<String,String> values;
                try {
                    values=JsonUtils.parseObject(record.getPayloadJson(),new TypeReference<Map<String,String>>(){});
                } catch (RuntimeException failure) {
                    fail("DPPMS_NATIVE_FULL_SOURCE_REQUIRED");return;
                }
                if(values==null||!values.keySet().equals(fields))fail("DPPMS_NATIVE_FULL_SOURCE_REQUIRED");
            }
        }
    }

    public Report status(Long projectId, Long sourceId, Long snapshotId, ProjectAccessActor actor) {
        validateActor(actor); projects.getProject(projectId, actor);
        var loaded = load(projectId, sourceId, snapshotId, actor, false);
        Map<String, ProjectLegacyNativeEvidenceService.RowStatus> statuses = new HashMap<>();
        loaded.domains().forEach((domain, rows) -> evidence.statuses(actor.tenantId(), loaded.source().getSourceProjectKey(), loaded.snapshot().getId(), rows)
                .forEach((key, value) -> statuses.put(identity(domain, key), value)));
        return report(loaded, statuses);
    }

    public Map<String, ProjectLegacyNativeEvidenceService.RowStatus> rowStatuses(Long projectId, Long sourceId,
            Long snapshotId, String domain, String sourceKey, String parentDomain, String parentKey,
            int pageNo, int pageSize, ProjectAccessActor actor) {
        validateActor(actor); projects.getProject(projectId, actor); catalog.require(domain);
        if (pageNo < 1 || pageSize < 1 || pageSize > 100 || (long)(pageNo - 1)*pageSize > Integer.MAX_VALUE) fail("LEGACY_PAGE_INVALID");
        if ((parentDomain == null) != (parentKey == null)) fail("LEGACY_PARENT_INVALID");
        if (parentDomain != null) { catalog.require(parentDomain); if (parentKey.isBlank()) fail("LEGACY_PARENT_INVALID"); }
        if (sourceKey != null && sourceKey.isBlank()) fail("LEGACY_SOURCE_KEY_INVALID");
        var source = source(projectId, sourceId, actor);
        var snapshot = snapshot(source, snapshotId, actor);
        var rows = mapper.selectRecords(new RecordsPageQuery(actor.tenantId(), snapshot.getId(), domain,
                sourceKey, parentDomain, parentKey, Math.multiplyExact(pageNo - 1, pageSize), pageSize));
        return evidence.statuses(actor.tenantId(), source.getSourceProjectKey(), snapshot.getId(), rows);
    }

    private Report importEntities(Long projectId, Loaded loaded, ProjectAccessActor actor,Long softwareHistoryDefinitionId) {
        var source = loaded.source(); var snapshot = loaded.snapshot();
        var rows = loaded.domains();
        Map<String, NativeEntityMigrationRow> outcomes = new HashMap<>();
        var contracts = rows.get("contracts");
        if (contracts.size() != 1 || !Objects.equals(value(contracts.getFirst(), "contractNo"), source.getSourceContractNo()))
            fail("DPPMS_NATIVE_CONTRACT_SOURCE_UNRESOLVED");
        var commerceIdentity = new DppmsCommerceEntityResolveApi.Command(actor.tenantId(), actor.actorId(), projectId,
                source.getSourceContractNo(), rows.get("orders").stream().map(r -> new DppmsCommerceEntityResolveApi.Order(
                        r.getSourceKey(), value(r,"source"), value(r,"compCode"), value(r,"orderType"), value(r,"orderNumber"))).toList(),
                rows.get("order-lines").stream().map(r -> new DppmsCommerceEntityResolveApi.Line(r.getSourceKey(), value(r,"source"),
                        value(r,"compCode"), value(r,"lineType"), value(r,"orderNumber"), value(r,"lineNum"))).toList(),
                rows.get("executions").stream().map(r -> new DppmsCommerceEntityResolveApi.Execution(r.getSourceKey(),
                        value(r,"dataSource"), value(r,"orderExecNumber"))).toList(),
                rows.get("shipment-logistics").stream().map(r -> new DppmsCommerceEntityResolveApi.Package(r.getSourceKey(),
                        value(r,"packId"))).toList(), contracts.getFirst().getSourceKey());
        add(outcomes,commerceDetails.importDetails(new DppmsCommerceDetailImportApi.Command(commerceIdentity,
                rows.get("orders").stream().map(r -> new DppmsCommerceDetailImportApi.Order(r.getSourceKey(),
                value(r,"contractNo"), value(r,"orderExecNumber"), time(value(r,"orderCreateTime")), time(value(r,"customerRequireTime")), value(r,"customerCode"), value(r,"customerName"), value(r,"projectName"), value(r,"orderComment"), value(r,"salesType"), value(r,"customInfo"), value(r,"compCode"), value(r,"source"), value(r,"id"), time(value(r,"syncTime")))).toList(),
                rows.get("order-lines").stream().map(r -> new DppmsCommerceDetailImportApi.Line(r.getSourceKey(),
                value(r,"itemCode"), value(r,"itemDesc"), decimal(value(r,"orderQuantity")), decimal(value(r,"openQuantity")), value(r,"bundleCode"), integer(value(r,"warrantyMonth")), value(r,"lineType"), value(r,"profitCenter"), value(r,"realOrderExecNumber"), value(r,"customInfo"), value(r,"compCode"), value(r,"source"), value(r,"id"), time(value(r,"syncTime")))).toList(),
                rows.get("shipment-packages").stream().map(r -> new DppmsCommerceDetailImportApi.Package(r.getSourceKey(),
                value(r,"packlist_id"), time(value(r,"packdate")), time(value(r,"warrantyStartTime")), time(value(r,"warrantyEndTime")), value(r,"receiveName"), value(r,"emsNum"), carrierName(rows,r), value(r,"con_id"), value(r,"emsCompany"))).toList(),
                rows.get("plans").stream().map(r -> new DppmsCommerceDetailImportApi.Plan(r.getSourceKey(),
                value(r,"contractNo"), value(r,"batchCode"), value(r,"basicDataName"), value(r,"referenceEventName"), time(value(r,"eventPlanHappenDate")), decimal(value(r,"afterDaysNum")), time(value(r,"eventActualFinishDate")), value(r,"marketingFeedback"), value(r,"dataSource"), time(value(r,"createTime")), value(r,"createBy"), time(value(r,"updateTime")), value(r,"updateBy"), time(value(r,"effectiveFrom")), time(value(r,"effectiveTo")))).toList(),
                rows.get("executions").stream().map(r->new DppmsCommerceDetailImportApi.Execution(r.getSourceKey(),value(r,"orderExecNumber"),value(r,"dataSource"),value(r,"projectCode"),value(r,"projectName"),value(r,"salesManCode"),value(r,"salesManName"),value(r,"marketCode"),value(r,"marketName"),value(r,"systemId"),value(r,"systemName"),value(r,"expendId"),value(r,"expendName"),value(r,"industryId"),value(r,"industryName"),value(r,"officeCode"),value(r,"officeName"),value(r,"serviceTypeName"),value(r,"channelName"),value(r,"engineeFee"),value(r,"objId"),value(r,"applyType"),value(r,"corporationCode"),value(r,"customerProjectName"),value(r,"finalCustomerName"),value(r,"agentName"),value(r,"majorProjectLevel"),decimal(value(r,"projectMoney")),time(value(r,"submitTime")),time(value(r,"predBidDate")),value(r,"linkmanName"),value(r,"linkmanTel"))).toList())));
        var associatedResult=associated.importRecords(projectId,rows,actor);
        add(outcomes,associatedResult.rows());
        for (var r : rows.get("parties")) {
            assertProject(source, r); add(outcomes, List.of(importParty(projectId, r, actor)));
        }
        var products = rows.get("shipment-products");
        Map<ShipmentIdentity,List<ProjectLegacyRecordDO>> productsByShipment = new HashMap<>();
        products.forEach(r -> productsByShipment.computeIfAbsent(new ShipmentIdentity(identityText(value(r,"barcode")),identityText(value(r,"packId"))),
                ignored -> new ArrayList<>()).add(r));
        Map<ShipmentIdentity,List<ProjectLegacyRecordDO>> factsByShipment = new HashMap<>();
        List<DppmsAssetEntityImportApi.Shipment> shipments = new ArrayList<>();
        for (var r : rows.get("shipment-facts")) {
            var shipmentIdentity = new ShipmentIdentity(identityText(value(r,"barcode")),identityText(value(r,"pack_id")));
            factsByShipment.computeIfAbsent(shipmentIdentity,ignored -> new ArrayList<>()).add(r);
            var matches = productsByShipment.getOrDefault(shipmentIdentity,List.of());
            if(matches.size()>1)fail("DPPMS_NATIVE_SHIPMENT_PRODUCT_AMBIGUOUS");
            var p=matches.isEmpty()?null:matches.getFirst();
            if(p!=null&&!DppmsContractIdentity.same(value(p,"contract_code"),source.getSourceContractNo()))
                fail("DPPMS_NATIVE_SHIPMENT_CONTRACT_CONFLICT");
            // The legacy view filters RMA rows. Original package/fact and material lookups still own those facts.
            if(p==null&&rows.get("shipment-packages").stream().noneMatch(pack->Objects.equals(
                    identityText(value(pack,"packlist_id")),identityText(value(r,"pack_id")))))
                fail("DPPMS_NATIVE_SHIPMENT_PACKAGE_UNRESOLVED");
            String productCode=value(r,"item");
            String primaryModel=p==null?materialValue(rows,productCode,"itemname"):value(p,"itemModel");
            String primaryName=p==null?materialValue(rows,productCode,"describe_"):value(p,"itemName");
            String secondaryModel=p==null?materialValue(rows,value(r,"item2"),"itemname"):value(p,"itemModel2");
            String secondaryName=p==null?materialValue(rows,value(r,"item2"),"describe_"):value(p,"itemName2");
            shipments.add(new DppmsAssetEntityImportApi.Shipment(r.getSourceKey(), value(r,"barcode"), value(r,"pack_id"),
                    productCode, primaryModel, primaryName,
                    new DppmsAssetEntityImportApi.ShipmentDetails(value(r,"pack_id"), value(r,"rmaBarcode"), value(r,"rma_no")==null||value(r,"rma_no").isEmpty()?0:1, value(r,"orderNumber"), value(r,"lineNum"), value(r,"item"), value(r,"barcode2"), value(r,"item2"), value(r,"com_barcode"), integer(value(r,"isRMA")), value(r,"profitCenter"), value(r,"soleAgentSuffix"), time(value(r,"syncTime")), value(r,"uuid"), date(value(r,"warrantyStartDate")), integer(value(r,"warrantyMonth")), value(r,"rma_no"), null),
                    new DppmsAssetEntityImportApi.ShipmentProductDetails(primaryModel,primaryName,
                            secondaryModel,secondaryName,time(value(r,"updateTime")))));
        }
        // view_warranty is a calculated read model; migrate its formal inputs instead of creating independent facts.
        // Existing records and receipts remain protected until a separate withdrawal is authorized.
        add(outcomes, assets.importEntities(new DppmsAssetEntityImportApi.Command(actor.tenantId(),actor.actorId(),projectId,
                source.getSourceContractNo(),snapshot.getId(),snapshot.getChecksum(),LocalDateTime.now(),shipments,List.of(),List.of(),
                List.of())));

        for (var record:rows.get("software-versions")) assertProject(source,record);
        for (var record:rows.get("software-changes")) assertProject(source,record);
        add(outcomes,sourceHistory.importSourceHistory(new DppmsAssetSourceHistoryApi.Command(
                actor.tenantId(),actor.actorId(),projectId,source.getSourceProjectKey(),snapshot.getChecksum(),snapshot.getSourceReadAt(),
                softwareObservations(new Loaded(source,snapshot,rows),actor,softwareHistoryDefinitionId),
                softwareBatches(new Loaded(source,snapshot,rows),actor),
                factoryRecords.command(projectId,new Loaded(source,snapshot,rows),actor).factories())));

        add(outcomes,warrantySources.importRecords(new DppmsWarrantySourceRecordsApi.Command(actor.tenantId(),actor.actorId(),projectId,
                source.getSourceProjectKey(),snapshot.getChecksum(),snapshot.getSourceReadAt(),
                rows.get("warranty-services").stream().map(r->new DppmsWarrantySourceRecordsApi.ServiceRecord(r.getSourceKey(),value(r,"id"),value(r,"con_xb"),value(r,"barcode"),value(r,"grade"),time(value(r,"begin_date")),time(value(r,"end_date")),value(r,"warranty"),value(r,"remark"),integer(value(r,"isyb")),integer(value(r,"state")))).toList(),
                rows.get("warranty-registrations").stream().map(r->new DppmsWarrantySourceRecordsApi.Registration(sourceLong(value(r,"id")),integer(value(r,"packlistId")),integer(value(r,"contractId")),value(r,"barCode"),value(r,"officeCode"),value(r,"projectName"),value(r,"contractNo"),integer(value(r,"contractType")),value(r,"customerName"),value(r,"itemCode"),value(r,"itemName"),value(r,"warrantyLevel"),time(value(r,"warrantyStartTime")),time(value(r,"warrantyEndTime")),integer(value(r,"warrantyLimit")))).toList(),
                rows.get("warranty-changes").stream().map(r->new DppmsWarrantySourceRecordsApi.Change(sourceLong(value(r,"id")),value(r,"username"),integer(value(r,"updateType")),value(r,"barcode"),time(value(r,"warrantyStartTime")),time(value(r,"warrantyEndTime")),integer(value(r,"warrantyTimes")),time(value(r,"updateTime")))).toList())));

        Map<String, Set<Long>> deviceIdsBySn = new HashMap<>();
        for (var r : rows.get("shipment-facts")) {
            var matched=outcomes.get(identity("shipment-facts",r.getSourceKey()));
            matched.targets().stream().filter(t -> "DEVICE".equals(t.targetRole())).forEach(t ->
                    deviceIdsBySn.computeIfAbsent(identityText(value(r,"barcode")),ignored -> new HashSet<>()).add(t.targetId()));
        }
        for (var r : products) {
            var candidates=factsByShipment.getOrDefault(new ShipmentIdentity(identityText(value(r,"barcode")),identityText(value(r,"packId"))),List.of());
            var match = candidates.size()==1 ? outcomes.get(identity("shipment-facts",candidates.getFirst().getSourceKey())) : null;
            add(outcomes,List.of(match != null && !match.targets().isEmpty()
                    ? new NativeEntityMigrationRow("shipment-products",r.getSourceKey(),match.targets(),null)
                    : NativeEntityMigrationRow.retained("shipment-products",r.getSourceKey(),candidates.size()>1
                    ? "NATIVE_SOURCE_IDENTITY_AMBIGUOUS" : "DEVICE_NOT_RECONCILED")));
        }
        for(var material:rows.getOrDefault("shipment-materials",List.of())) {
            var shipmentTargets=rows.get("shipment-facts").stream().filter(fact->
                    Objects.equals(identityText(value(material,"item")),identityText(value(fact,"item")))||
                    Objects.equals(identityText(value(material,"item")),identityText(value(fact,"item2"))))
                    .map(fact->outcomes.get(identity("shipment-facts",fact.getSourceKey())))
                    .filter(Objects::nonNull).flatMap(outcome->outcome.targets().stream())
                    .filter(target->"SHIPMENT".equals(target.targetRole())).distinct()
                    .sorted(Comparator.comparing(ExternalTargetMapping::targetId)).toList();
            List<ExternalTargetMapping> targets=new ArrayList<>();
            for(var target:shipmentTargets) targets.add(new ExternalTargetMapping(
                    target.targetContext(),target.targetObjectType(),target.targetTable(),
                    target.targetId(),target.targetRole(),targets.size()));
            if(targets.isEmpty())fail("DPPMS_NATIVE_MATERIAL_REFERENCE_UNRESOLVED");
            add(outcomes,List.of(new NativeEntityMigrationRow("shipment-materials",material.getSourceKey(),targets,null)));
        }
        List<DppmsInstallationAddressImportApi.Address> addresses = new ArrayList<>();
        for (var r : rows.get("installations")) {
            assertProject(source,r);
            var devicesForSn = deviceIdsBySn.getOrDefault(identityText(value(r,"barcode")),Set.of());
            if (devicesForSn.size()!=1) {
                add(outcomes,List.of(NativeEntityMigrationRow.retained("installations",r.getSourceKey(),"DEVICE_NOT_RECONCILED")));
            } else addresses.add(new DppmsInstallationAddressImportApi.Address(r.getSourceKey(),devicesForSn.iterator().next(),value(r,"installAddress"),
                    new DppmsInstallationAddressImportApi.Details(value(r,"itemCode"), value(r,"itemModel"), value(r,"itemName"), value(r,"receiveName"), value(r,"emsNum"), value(r,"emsCompany"), time(value(r,"packdate")), value(r,"contractNo"), value(r,"chProjectId"), value(r,"chContractNo"), value(r,"transferProjectId"), value(r,"transferContractNo"), integer(value(r,"transferFlag")), time(value(r,"createTime")), value(r,"createBy"), time(value(r,"updateTime")), value(r,"updateBy")),source.getSourceProjectKey(),value(r,"barcode"),sourceLong(value(r,"id")),sourceLong(value(r,"projectId"))));
        }
        add(outcomes, installations.importAddresses(new DppmsInstallationAddressImportApi.Command(actor.tenantId(),actor.actorId(),projectId,addresses,snapshot.getChecksum(),snapshot.getSourceReadAt())));
        Map<String, ProjectLegacyNativeEvidenceService.RowStatus> statuses = new HashMap<>();
        rows.forEach((domain, records) -> {
            for (var r : records) {
                var outcome = outcomes.computeIfAbsent(identity(domain,r.getSourceKey()), ignored -> NativeEntityMigrationRow.retained(
                        domain,r.getSourceKey(),LegacyNativeReasons.forDomain(domain)));
                if(REQUIRED_NATIVE_DOMAINS.contains(domain)&&outcome.targets().isEmpty())
                    fail("DPPMS_REQUIRED_NATIVE_UNRESOLVED:"+domain+":"+outcome.reasonCode());
                String classification = outcome.targets().isEmpty() ? "RETAINED" : "MAPPED";
                statuses.put(identity(domain,r.getSourceKey()),new ProjectLegacyNativeEvidenceService.RowStatus(classification,
                        outcome.reasonCode(),LegacyNativeReasons.text(outcome.reasonCode()),outcome.targets()));
            }
            if("real-products".equals(domain)) evidence.reconcileRealProducts(source,snapshot,records,outcomes,associatedResult.realBaselines());
            else if("lease-products".equals(domain)) evidence.reconcileAssociatedList(source,snapshot,domain,records,outcomes,associatedResult.leaseBaselines());
            else if("configured-products".equals(domain)) evidence.reconcileAssociatedList(source,snapshot,domain,records,outcomes,associatedResult.configurationBaselines());
            else if("products".equals(domain)) evidence.reconcile(source,snapshot,domain,records,outcomes,"PRODUCT_SCOPE_V3",associatedResult.productBaselines());
            else evidence.reconcile(source,snapshot,domain,records,outcomes);
        });
        return report(loaded,statuses);
    }

    private NativeEntityMigrationRow importParty(Long projectId, ProjectLegacyRecordDO r, ProjectAccessActor actor) {
        String role = value(r,"partyRole");
        // These are the audited legacy channel role codes, kept verbatim rather than guessed customer/manager roles.
        if (role == null || !Set.of("20","30").contains(role)) return NativeEntityMigrationRow.retained("parties",r.getSourceKey(),"TARGET_IDENTITY_CONFLICT");
        var row = parties.selectSourceForUpdate(new ProjectPartyMapper.PartySourceQuery(actor.tenantId(),"DPPMS",
                r.getSourceTable(),r.getSourceKey(),role));
        if (row != null && (Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getProjectId(),projectId)))
            return NativeEntityMigrationRow.retained("parties",r.getSourceKey(),"TARGET_IDENTITY_CONFLICT");
        if (row == null) {
            row = new ProjectPartyDO(); row.setId(IdWorker.getId()); row.setTenantId(actor.tenantId()); row.setProjectId(projectId);
            row.setPartyRole(role); row.setPartyCode(value(r,"partyCode")); row.setPartyName(value(r,"partyName"));
            row.setSourceSystem("DPPMS"); row.setSourceTable(r.getSourceTable()); row.setSourceRecordKey(r.getSourceKey());
            row.setEffectiveFrom(time(value(r,"effectiveFrom"))); row.setEffectiveTo(time(value(r,"effectiveTo")));
            row.setStatus("ACTIVE"); row.setVersion(0); parties.insert(row);
        }
        return NativeEntityMigrationRow.mapped("parties",r.getSourceKey(),new ExternalTargetMapping("PROJ","ProjectParty",
                "proj_project_party",row.getId(),"PARTY",0));
    }

    List<DppmsAssetSourceHistoryApi.SoftwareObservation> softwareObservations(Loaded loaded,ProjectAccessActor actor,Long definitionId) {
        return softwareObservations(loaded,actor,definitionId,false);
    }
    List<DppmsAssetSourceHistoryApi.SoftwareObservation> softwareObservations(Loaded loaded,ProjectAccessActor actor,Long definitionId,boolean correction) {
        var records=loaded.domains().get("software-versions");
        if (records.isEmpty()) return List.of();
        if (definitionId==null || definitionId<=0) throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_DEFINITION_REQUIRED");
        var fields=Set.of("id","projectId","logId","contractNo","itemCode","barCode","conp","conpType","conpSeries","conpMark",
                "conpBak","conpChange","cpld","cpldBak","cpldChange","boot","bootBak","bootChange","pcb","pcbBak","pcbChange",
                "executeTime","datastate","createTime","createBy","updateTime","updateBy","customInfo");
        var statuses=evidence.statuses(actor.tenantId(),loaded.source().getSourceProjectKey(),loaded.snapshot().getId(),records);
        Set<String> sourceIds=new HashSet<>();Set<Long> targetIds=new HashSet<>();
        var raw=records.stream().map(r->new DppmsAssetSourceHistoryApi.SoftwareObservation(sourceLong(value(r,"id")),sourceLong(value(r,"projectId")),sourceLong(value(r,"logId")),value(r,"contractNo"),value(r,"itemCode"),value(r,"barCode"),value(r,"conp"),value(r,"conpType"),value(r,"conpSeries"),value(r,"conpMark"),value(r,"conpBak"),integer(value(r,"conpChange")),value(r,"cpld"),value(r,"cpldBak"),integer(value(r,"cpldChange")),value(r,"boot"),value(r,"bootBak"),integer(value(r,"bootChange")),value(r,"pcb"),value(r,"pcbBak"),integer(value(r,"pcbChange")),date(value(r,"executeTime")),integer(value(r,"datastate")),time(value(r,"createTime")),value(r,"createBy"),time(value(r,"updateTime")),value(r,"updateBy"),value(r,"customInfo"))).toList();
        var result=new ArrayList<DppmsAssetSourceHistoryApi.SoftwareObservation>();
        for (int index=0;index<records.size();index++) {
            var record=records.get(index);var values=JsonUtils.parseObject(record.getPayloadJson(),new TypeReference<Map<String,String>>(){});
            if (values==null || !values.keySet().equals(fields) || !sourceIds.add(record.getSourceKey())
                    || !Objects.equals(record.getSourceKey(),values.get("id")) || !Objects.equals(loaded.source().getSourceProjectKey(),values.get("projectId"))
                    || !Objects.equals(record.getTenantId(),actor.tenantId()) || !Objects.equals(record.getSnapshotId(),loaded.snapshot().getId())
                    || !"pm_project_soft_version".equals(record.getSourceTable()))
                throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_ORIGINAL_FIELDS_INVALID");
            var status=statuses.get(record.getSourceKey());
            if (status==null || Set.of("CHECKSUM_CONFLICT","AMBIGUOUS","RETAINED").contains(status.classification()))
                throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_SOURCE_EVIDENCE_INVALID");
            var legacy=status.targets().stream().filter(t->"ast_device_software_observation".equals(t.targetTable())).toList();
            if (!legacy.isEmpty()) {
                if (!correction) throw new IllegalArgumentException("DPPMS_LEGACY_SOFTWARE_REQUIRES_APPROVED_CORRECTION");
                if (legacy.size()!=1 || status.targets().size()!=1 || !"AST".equals(legacy.getFirst().targetContext())
                        || !"DeviceSoftwareObservation".equals(legacy.getFirst().targetObjectType()) || !"SOFTWARE_OBSERVATION".equals(legacy.getFirst().targetRole())
                        || legacy.getFirst().targetSequence()!=0 || legacy.getFirst().targetId()==null || legacy.getFirst().targetId()<=0
                        || !targetIds.add(legacy.getFirst().targetId()))
                    throw new IllegalArgumentException("DPPMS_LEGACY_SOFTWARE_BINDING_INVALID");
                result.add(raw.get(index).withMapping(legacy.getFirst().targetId(),null,definitionId));continue;
            }
            var targets=status.targets().stream().filter(t->"ast_device_software_version_record".equals(t.targetTable())).toList();
            if (targets.size()>1 || !targets.isEmpty() && (!"AST".equals(targets.getFirst().targetContext())
                    || !"SoftwareVersionRecord".equals(targets.getFirst().targetObjectType()) || !"SOFTWARE_VERSION_RECORD".equals(targets.getFirst().targetRole())
                    || targets.getFirst().targetSequence()!=0 || targets.getFirst().targetId()==null || targets.getFirst().targetId()<=0
                    || !targetIds.add(targets.getFirst().targetId())))
                throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_BINDING_INVALID");
            result.add(raw.get(index).withMapping(targets.isEmpty()?null:targets.getFirst().targetId(),null,definitionId));
        }
        return List.copyOf(result);
    }

    List<DppmsAssetSourceHistoryApi.SoftwareBatch> softwareBatches(Loaded loaded, ProjectAccessActor actor) {
        var records=loaded.domains().get("software-changes");
        if (records.isEmpty()) return List.of();
        var statuses=evidence.statuses(actor.tenantId(),loaded.source().getSourceProjectKey(),loaded.snapshot().getId(),records);
        Set<String> keys=new HashSet<>();Set<Long> ids=new HashSet<>();
        var result=new ArrayList<DppmsAssetSourceHistoryApi.SoftwareBatch>();
        for (var record:records) {
            var fields=JsonUtils.parseObject(record.getPayloadJson(),new TypeReference<Map<String,String>>(){});
            if (fields==null || !fields.keySet().equals(Set.of("id","projectId","changeVersion","changeRemark","latest",
                    "createBy","createTime","updateBy","updateTime")) || !keys.add(record.getSourceKey())
                    || !Objects.equals(record.getSourceKey(),fields.get("id"))
                    || !Objects.equals(loaded.source().getSourceProjectKey(),fields.get("projectId"))
                    || !Objects.equals(record.getTenantId(),actor.tenantId())
                    || !Objects.equals(record.getSnapshotId(),loaded.snapshot().getId())
                    || !"pm_project_soft_change_logs".equals(record.getSourceTable()))
                throw new IllegalArgumentException("DPPMS_BATCH_ORIGINAL_FIELDS_INVALID");
            var status=statuses.get(record.getSourceKey());
            if (status==null || Set.of("CHECKSUM_CONFLICT","AMBIGUOUS","RETAINED").contains(status.classification()))
                throw new IllegalArgumentException("DPPMS_BATCH_SOURCE_EVIDENCE_INVALID");
            var targets=status.targets().stream().filter(t->"ast_device_software_change_batch".equals(t.targetTable())).toList();
            if (targets.size()>1 || !targets.isEmpty() && (!"AST".equals(targets.getFirst().targetContext())
                    || !"DeviceSoftwareChangeBatch".equals(targets.getFirst().targetObjectType())
                    || !"SOFTWARE_BATCH".equals(targets.getFirst().targetRole()) || targets.getFirst().targetSequence()!=0
                    || targets.getFirst().targetId()==null || targets.getFirst().targetId()<=0 || !ids.add(targets.getFirst().targetId())))
                throw new IllegalArgumentException("DPPMS_BATCH_BINDING_INVALID");
            result.add(new DppmsAssetSourceHistoryApi.SoftwareBatch(sourceLong(fields.get("id")),sourceLong(fields.get("projectId")),
                    fields.get("changeVersion"),fields.get("changeRemark"),integer(fields.get("latest")),fields.get("createBy"),
                    time(fields.get("createTime")),fields.get("updateBy"),time(fields.get("updateTime")),
                    targets.isEmpty()?null:targets.getFirst().targetId()));
        }
        return List.copyOf(result);
    }

    private Loaded load(Long projectId, Long sourceId, Long snapshotId, ProjectAccessActor actor, boolean lock) {
        var source = source(projectId,sourceId,actor);
        if (lock) source = mapper.selectSourceForUpdate(new SourceIdentityQuery(actor.tenantId(),source.getSourceProjectKey()));
        var snapshot = snapshot(source,snapshotId,actor);
        if (lock && !Objects.equals(source.getCurrentSnapshotId(),snapshot.getId())) fail("DPPMS_NATIVE_HEAD_CHANGED");
        Map<String,List<ProjectLegacyRecordDO>> domains = new LinkedHashMap<>();
        for (var d : catalog.domains()) {
            List<ProjectLegacyRecordDO> records = new ArrayList<>();
            for (int offset = 0;; offset += 500) {
                var page = mapper.selectRecords(new RecordsPageQuery(actor.tenantId(),snapshot.getId(),d.code(),null,null,null,offset,500));
                records.addAll(page); if (page.size() < 500) break;
            }
            domains.put(d.code(),records);
        }
        if (domains.values().stream().mapToInt(List::size).sum() != snapshot.getRecordCount()) fail("DPPMS_NATIVE_SNAPSHOT_COUNT_CONFLICT");
        return new Loaded(source,snapshot,domains);
    }
    private ProjectLegacySourceDO source(Long projectId, Long sourceId, ProjectAccessActor actor) {
        return mapper.selectProjectSources(new ProjectSourcesQuery(actor.tenantId(),projectId)).stream()
                .filter(r -> Objects.equals(r.getId(),sourceId)).findFirst().orElseThrow(() -> invalidParamException("LEGACY_SOURCE_INACCESSIBLE"));
    }
    private ProjectLegacySnapshotDO snapshot(ProjectLegacySourceDO source, Long snapshotId, ProjectAccessActor actor) {
        var snapshot = mapper.selectSnapshot(new SnapshotQuery(actor.tenantId(),snapshotId == null ? source.getCurrentSnapshotId() : snapshotId));
        if (snapshot == null || !Objects.equals(snapshot.getSourceId(),source.getId())) fail("LEGACY_SNAPSHOT_INACCESSIBLE");
        return snapshot;
    }
    private Report report(Loaded loaded, Map<String, ProjectLegacyNativeEvidenceService.RowStatus> statuses) {
        List<DomainSummary> summaries = new ArrayList<>();
        loaded.domains().forEach((code,rows) -> {
            int mapped=0,retained=0,pending=0; Set<String> tables=new TreeSet<>(); Map<String,Integer> reasons=new TreeMap<>();
            for (var row : rows) {
                var status=statuses.get(identity(code,row.getSourceKey()));
                if (status != null && "MAPPED".equals(status.classification())) { mapped++; status.targets().forEach(t -> tables.add(t.targetTable())); }
                else if (status != null && "RETAINED".equals(status.classification())) {
                    retained++; reasons.merge(status.reason() == null ? "承接原因尚待核对" : status.reason(),1,Integer::sum);
                } else pending++;
            }
            summaries.add(new DomainSummary(code,catalog.require(code).label(),rows.size(),mapped,retained,pending,List.copyOf(tables),reasons));
        });
        return new Report(loaded.source().getId(),loaded.snapshot().getId(),loaded.snapshot().getRecordCount(),
                summaries.stream().mapToInt(DomainSummary::mappedCount).sum(), summaries.stream().mapToInt(DomainSummary::retainedCount).sum(),
                summaries.stream().mapToInt(DomainSummary::pendingCount).sum(),summaries);
    }
    private static void add(Map<String,NativeEntityMigrationRow> target, List<NativeEntityMigrationRow> rows) {
        for (var r : rows) if (target.putIfAbsent(identity(r.domain(),r.sourceKey()),r) != null) throw new IllegalStateException("DPPMS_NATIVE_DUPLICATE_OUTCOME");
    }
    static String value(ProjectLegacyRecordDO row, String key) {
        String value=JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){}).get(key);
        return value;
    }
    private static String carrierName(Map<String,List<ProjectLegacyRecordDO>> rows,ProjectLegacyRecordDO source) {
        var matches=rows.get("shipment-logistics").stream().filter(r->Objects.equals(value(r,"packId"),value(source,"packlist_id"))).toList();
        return matches.size()==1?value(matches.getFirst(),"emsCompany"):null;
    }
    private static String identityText(String raw) { return raw==null?null:raw.stripTrailing().toLowerCase(Locale.ROOT); }
    private static String materialValue(Map<String,List<ProjectLegacyRecordDO>> rows,String item,String field) {
        if(item==null||item.isBlank())return null;
        var candidates=rows.getOrDefault("shipment-materials",List.of()).stream()
                .filter(r->Objects.equals(identityText(value(r,"item")),identityText(item))).toList();
        if(candidates.size()>1)fail("DPPMS_NATIVE_SHIPMENT_MATERIAL_AMBIGUOUS");
        return candidates.isEmpty()?null:value(candidates.getFirst(),field);
    }
    private static String syncText(String value) { return value==null?null:value.trim().isEmpty()?null:value.trim(); }
    static Long sourceLong(String value) { return value==null||value.isBlank()?null:Long.valueOf(value); }
    static BigDecimal decimal(String value) { return value==null||value.isBlank()?null:new BigDecimal(value); }
    static LocalDateTime time(String value) { return value == null || value.isBlank() ? null : value.length() == 10 ? LocalDate.parse(value).atStartOfDay() : LocalDateTime.parse(value.replace(' ','T')); }
    static LocalDate date(String value) { return value == null || value.isBlank() ? null : time(value).toLocalDate(); }
    static Integer integer(String value) { return value == null || value.isBlank() ? null : new BigDecimal(value).intValueExact(); }
    private static void assertProject(ProjectLegacySourceDO source,ProjectLegacyRecordDO row) {
        if (!Objects.equals(source.getSourceProjectKey(),value(row,"projectId"))) fail("DPPMS_NATIVE_SOURCE_PROJECT_CONFLICT");
    }
    private static void validateActor(ProjectAccessActor actor) {
        if (actor == null || actor.actorId() == null || actor.actorId() <= 0 || actor.tenantId() == null
                || !Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId())) fail("LEGACY_ACTOR_INVALID");
    }
    private static void fail(String code) { throw invalidParamException(code); }
}
